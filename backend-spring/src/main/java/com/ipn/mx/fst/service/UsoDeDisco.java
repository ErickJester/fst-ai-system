package com.ipn.mx.fst.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// El disco del volumen de archivos, para GET /admin/system. Recorrer las carpetas cuesta, y la pantalla de Administración vuelve a
// pedir la cola cada pocos segundos, así que lo calculado se guarda un minuto.
@Component
public class UsoDeDisco {

	private static final Logger log = LoggerFactory.getLogger(UsoDeDisco.class);
	private static final long VIGENCIA_MS = 60_000;
	private static final double GB = 1024d * 1024d * 1024d;

	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	@Autowired
	private JdbcTemplate jdbc;

	private Map<String, Object> guardado;
	private long calculadoEn;

	// {usado_gb, total_gb, videos_gb, resultados_gb, por_liberar_7d_gb}, en GB con un decimal (la forma que lee la pantalla).
	//   total y usado: el sistema de archivos donde está el volumen.
	//   videos: lo que ocupa videos/ (se borran a los 30 días del análisis).
	//   resultados: analisis/ y reportes/, que se conservan.
	//   por_liberar_7d: lo que ocupan los videos de análisis completados cuyo plazo de 30 días vence en los próximos 7 días.
	public synchronized Map<String, Object> consultar() {
		long ahora = System.currentTimeMillis();
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
			Files.createDirectories(raiz);
			total = Files.getFileStore(raiz).getTotalSpace();
			libre = Files.getFileStore(raiz).getUsableSpace();
		} catch (IOException e) {
			log.warn("No se pudo leer el espacio del volumen {}: {}", raiz, e.getMessage());
		}

		long videos = tamano(raiz.resolve("videos"));
		long resultados = tamano(raiz.resolve("analisis")) + tamano(raiz.resolve("reportes"));

		long porLiberar = 0;
		List<String> archivos = jdbc.queryForList(
				"SELECT v.archivo FROM video v JOIN analisis a ON a.idvideo = v.idvideo "
						+ "WHERE a.estado = 'completado' AND v.archivo IS NOT NULL "
						+ "AND a.fechaanalisis + interval '30 days' <= now() + interval '7 days'", String.class);
		for (String archivo : archivos) {
			Path ruta = raiz.resolve(archivo).normalize();
			if (ruta.startsWith(raiz) && Files.isRegularFile(ruta)) {
				try {
					porLiberar += Files.size(ruta);
				} catch (IOException e) {
					log.warn("No se pudo leer el tamaño de {}: {}", ruta, e.getMessage());
				}
			}
		}

		Map<String, Object> disco = new LinkedHashMap<>();
		disco.put("usado_gb", enGb(total - libre));
		disco.put("total_gb", enGb(total));
		disco.put("videos_gb", enGb(videos));
		disco.put("resultados_gb", enGb(resultados));
		disco.put("por_liberar_7d_gb", enGb(porLiberar));
		return disco;
	}

	// Suma de los archivos de una carpeta (0 si no existe). Un archivo que desaparece mientras se recorre no cuenta.
	private long tamano(Path carpeta) {
		if (!Files.isDirectory(carpeta)) {
			return 0;
		}
		try (Stream<Path> rutas = Files.walk(carpeta)) {
			return rutas.filter(Files::isRegularFile).mapToLong(r -> {
				try {
					return Files.size(r);
				} catch (IOException e) {
					return 0;
				}
			}).sum();
		} catch (IOException e) {
			log.warn("No se pudo recorrer {}: {}", carpeta, e.getMessage());
			return 0;
		}
	}

	private static double enGb(long bytes) {
		return Math.round(bytes / GB * 10) / 10d;
	}

}
