// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

import java.io.IOException;
// Files y Path: recorrer y medir archivos del volumen.
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
// Stream: el recorrido perezoso de archivos que devuelve Files.walk.
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
// JdbcTemplate: SQL directo, para la consulta de los videos por vencer.
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// El disco del volumen de archivos, para GET /admin/system. Recorrer las carpetas cuesta, y la pantalla de Administración vuelve a
// pedir la cola cada pocos segundos, así que lo calculado se guarda un minuto.
//
// Por qué importa el disco en este sistema: los videos son enormes y se acumulan. Si el volumen se llena,
// el worker no puede trabajar y se pierden análisis. Esta pantalla es la que avisa antes de llegar ahí, y
// de ella sale también la notificación ALERTA_DE_DISCO (ver TipoNotificacion).
// Fíjate en el problema que resuelve la caché: Files.walk recorre CADA archivo del volumen, que pueden ser
// miles de videos. Hacerlo cada pocos segundos tendría al servidor leyendo el disco sin parar para
// enseñar un número que apenas cambia.
@Component
public class UsoDeDisco {

	private static final Logger log = LoggerFactory.getLogger(UsoDeDisco.class);
	// Cuánto vale lo calculado antes de rehacerlo. Un minuto: el disco no cambia tan deprisa como para
	// que importe, y ahorra decenas de recorridos.
	private static final long VIGENCIA_MS = 60_000;
	// Un gigabyte en bytes. 1024 y no 1000: es la convención de los sistemas de archivos.
	// La d del final hace que sean double y que la división de abajo no trunque.
	private static final double GB = 1024d * 1024d * 1024d;

	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	@Autowired
	private JdbcTemplate jdbc;

	// El estado de la caché: lo último calculado y cuándo.
	private Map<String, Object> guardado;
	private long calculadoEn;

	// {usado_gb, total_gb, videos_gb, resultados_gb, por_liberar_7d_gb}, en GB con un decimal (la forma que lee la pantalla).
	//   total y usado: el sistema de archivos donde está el volumen.
	//   videos: lo que ocupa videos/ (se borran a los 30 días del análisis).
	//   resultados: analisis/ y reportes/, que se conservan.
	//   por_liberar_7d: lo que ocupan los videos de análisis completados cuyo plazo de 30 días vence en los próximos 7 días.
	//
	// La distinción entre videos y resultados es la que importa: los videos se van solos al cumplir los 30
	// días, los resultados se quedan para siempre. Y por_liberar_7d responde la pregunta práctica del
	// administrador: "si aguanto una semana más, ¿cuánto se libera sin que yo haga nada?".
	//
	// synchronized: solo un hilo a la vez entra aquí. Hace falta porque varios usuarios pueden pedir la
	// pantalla al mismo tiempo, y sin él dos hilos podrían ver la caché vencida a la vez y lanzarse los
	// dos a recorrer el disco. Además protege la escritura de los dos campos, que no es atómica.
	public synchronized Map<String, Object> consultar() {
		long ahora = System.currentTimeMillis();
		// Se recalcula si nunca se calculó o si lo guardado ya venció.
		if (guardado == null || ahora - calculadoEn > VIGENCIA_MS) {
			guardado = calcular();
			calculadoEn = ahora;
		}
		return guardado;
	}

	private Map<String, Object> calcular() {
		Path raiz = Path.of(raizConfigurada).toAbsolutePath().normalize();
		long total = 0;
		long libre = 0;
		try {
			// Se crea la carpeta si no existe: en una instalación nueva todavía no hay nada.
			// createDirectories no se queja si ya está.
			Files.createDirectories(raiz);
			// getFileStore da el sistema de archivos donde vive esa ruta, es decir, la partición entera.
			total = Files.getFileStore(raiz).getTotalSpace();
			// getUsableSpace y no getFreeSpace: el usable descuenta el espacio que el sistema reserva para
			// root, así que es el que de verdad se puede llenar.
			libre = Files.getFileStore(raiz).getUsableSpace();
		} catch (IOException e) {
			// Si no se puede leer, los dos quedan en 0 y la pantalla enseña ceros. No se lanza excepción:
			// es información de apoyo, y que falte no debe tumbar la pantalla de administración.
			log.warn("No se pudo leer el espacio del volumen {}: {}", raiz, e.getMessage());
		}

		long videos = tamano(raiz.resolve("videos"));
		long resultados = tamano(raiz.resolve("analisis")) + tamano(raiz.resolve("reportes"));

		long porLiberar = 0;
		// Los videos de análisis completados cuyo plazo de 30 días vence dentro de 7 días o menos.
		// La condición se lee así: la fecha del análisis más 30 días cae antes de dentro de 7 días.
		// archivo IS NOT NULL descarta los que ya se borraron (ver la entidad Video).
		// El cálculo de fechas se hace en SQL, con los intervalos de PostgreSQL, por lo mismo que en
		// TandaRepository: así no depende de la zona horaria de la JVM.
		List<String> archivos = jdbc.queryForList(
				"SELECT v.archivo FROM video v JOIN analisis a ON a.idvideo = v.idvideo "
						+ "WHERE a.estado = 'completado' AND v.archivo IS NOT NULL "
						+ "AND a.fechaanalisis + interval '30 days' <= now() + interval '7 days'", String.class);
		for (String archivo : archivos) {
			Path ruta = raiz.resolve(archivo).normalize();
			// startsWith(raiz) es una comprobación de SEGURIDAD, no una formalidad: la ruta viene de la
			// base, y si alguna vez trajera "../../etc", normalize() la resolvería fuera del volumen.
			// Esto garantiza que, después de normalizar, la ruta sigue estando dentro de la raíz.
			if (ruta.startsWith(raiz) && Files.isRegularFile(ruta)) {
				try {
					porLiberar += Files.size(ruta);
				} catch (IOException e) {
					// Un archivo que no se puede medir se cuenta como 0 y se sigue: el total queda algo
					// corto, que es mejor que no dar ninguna cifra.
					log.warn("No se pudo leer el tamaño de {}: {}", ruta, e.getMessage());
				}
			}
		}

		// Las claves van en minúsculas con guion bajo, no en el estilo del resto de la API: es la forma
		// que ya lee la pantalla de administración.
		Map<String, Object> disco = new LinkedHashMap<>();
		// Lo usado es lo total menos lo libre: el sistema de archivos no da un "usado" directo.
		disco.put("usado_gb", enGb(total - libre));
		disco.put("total_gb", enGb(total));
		disco.put("videos_gb", enGb(videos));
		disco.put("resultados_gb", enGb(resultados));
		disco.put("por_liberar_7d_gb", enGb(porLiberar));
		return disco;
	}

	// Suma de los archivos de una carpeta (0 si no existe). Un archivo que desaparece mientras se recorre no cuenta.
	//
	// Esa última frase describe una carrera real: el worker borra videos vencidos mientras esto recorre.
	// Un archivo listado hace un instante puede ya no estar al medirlo, y por eso cada medición va en su
	// propio try: el recorrido continúa en vez de abortarse entero.
	private long tamano(Path carpeta) {
		// Una carpeta que todavía no existe (instalación nueva) ocupa 0, no es un error.
		if (!Files.isDirectory(carpeta)) {
			return 0;
		}
		// try-con-recursos: Files.walk abre descriptores del sistema y hay que cerrarlos. Sin esto se
		// irían agotando con cada consulta.
		try (Stream<Path> rutas = Files.walk(carpeta)) {
			// walk recorre también las subcarpetas; el filtro se queda solo con archivos.
			return rutas.filter(Files::isRegularFile).mapToLong(r -> {
				try {
					return Files.size(r);
				} catch (IOException e) {
					// El archivo desapareció entre listarlo y medirlo: cuenta 0 y se sigue, sin ruido en
					// el log, porque es lo esperado.
					return 0;
				}
			}).sum();
		} catch (IOException e) {
			log.warn("No se pudo recorrer {}: {}", carpeta, e.getMessage());
			return 0;
		}
	}

	// Bytes a gigabytes con un decimal. El mismo truco de redondeo que Estadisticos: multiplicar por 10,
	// redondear y dividir entre 10. El 10d del divisor obliga a que la división sea decimal.
	private static double enGb(long bytes) {
		return Math.round(bytes / GB * 10) / 10d;
	}

}
