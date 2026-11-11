// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// El enum de los tres formatos de reporte.
import com.ipn.mx.fst.domain.valores.FormatoReporte;
import com.ipn.mx.fst.utilerias.ErrorApi;
// @PostConstruct: marca un método que Spring llama una vez, al terminar de construir el objeto.
import jakarta.annotation.PostConstruct;
import java.io.IOException;
// Las clases de archivos: el resultado de un paso del recorrido, las utilidades, las rutas, el visitante
// de carpetas, las opciones de copia y los atributos de un archivo.
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
// Pattern: una expresión regular compilada.
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
// DataAccessException: la excepción raíz de los fallos de base de datos de Spring.
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Las dos clases que permiten ejecutar algo DESPUÉS de que la transacción confirme.
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Los reportes guardados (C-07 y C-08, decididos el 25-oct; RN-06 y RN-14 del diseño). Cada reporte es un archivo en
// el volumen, reportes/{idExperimento}/..., con su fila en REPORTE (ruta y fechageneracion). Sirve para cualquier
// formato y alcance: hoy el PDF del experimento; después, los CSV y XLSX de grupo y de tanda.
//   reportes/{idExperimento}/experimento.pdf
//   reportes/{idExperimento}/grupo-{idGrupo}.{csv|xlsx}
//   reportes/{idExperimento}/tanda-{idTanda}.{csv|xlsx}
// Un reporte se conserva aunque el video se haya borrado. Cuando cambia lo que muestra, se invalida: se borran sus filas
// y su carpeta, y la próxima descarga lo regenera. Con MODO_DESARROLLADOR no se guarda nada: el PDF lleva los tiempos
// del análisis y no debe quedar guardado para quien tenga el modo apagado.
//
// En el fondo es una CACHÉ en disco, y las cachés tienen fama de difíciles por una razón: lo complicado no
// es guardar, es saber cuándo lo guardado dejó de valer. Este archivo es casi todo eso.
// "Se conserva aunque el video se haya borrado" es justo lo que la hace valiosa: los videos se van a los
// 30 días (ver la entidad Video) y el reporte es lo que queda del experimento.
// Hay cuatro motivos distintos por los que un reporte se invalida, y cada uno tiene su método:
//   1. cambió la VERSIÓN del PDF (al arrancar)        → invalidarPdfDeOtraVersion
//   2. cambiaron los datos del experimento            → invalidar
//   3. cambió el nombre del creador                   → invalidarDeLaCuenta
//   4. está el modo desarrollador                     → sencillamente no se guarda
@Service
public class ReporteAlmacen {

	private static final Logger log = LoggerFactory.getLogger(ReporteAlmacen.class);

	// Versión del contenido del PDF del experimento. Subirla cuando cambie lo que el PDF dibuja: al arrancar se borran los PDF
	// guardados de otra versión (sus filas y sus archivos experimento.pdf), y la próxima descarga los regenera. Así nadie
	// recibe un PDF viejo, sin tocar el esquema. Versión 2: el PDF con la sección de gráficas y sus marcadores (M1B).
	//
	// Es una solución elegante a un problema real: al mejorar el PDF, los ya guardados siguen siendo los
	// viejos y nada en los datos ha cambiado, así que ninguna invalidación normal los tocaría. Un número
	// de versión en un archivo del volumen resuelve el caso sin añadir una columna al esquema.
	static final String VERSION_PDF = "3";
	private static final String MARCA_VERSION = "version-pdf.txt";
	// La expresión regular que valida el alcance: la palabra grupo o tanda, un guion y uno o más dígitos.
	// \\d es un dígito (la barra va doble porque en Java la primera escapa a la segunda).
	// Compilarla una vez en una constante evita rehacerla en cada llamada.
	private static final Pattern GRUPO_O_TANDA = Pattern.compile("(grupo|tanda)-\\d+");

	// Genera los bytes del reporte (el generarReporte de ExperimentoServiceImpl para el PDF).
	//
	// @FunctionalInterface: una interfaz de UN solo método, así que se puede escribir como lambda.
	// Es lo que permite pasarle al almacén "cómo generar el reporte" sin que él sepa nada de PDF ni de
	// Excel. Y, sobre todo, el generador solo se EJECUTA si de verdad hace falta: si el archivo guardado
	// sirve, el trabajo caro no llega a hacerse.
	@FunctionalInterface
	public interface Generador {
		byte[] generar() throws IOException;
	}

	// Lo que devuelve un generador que decide si su resultado se guarda: false = se entrega, pero no queda en caché.
	public record Generado(byte[] bytes, boolean guardar) {
	}

	@FunctionalInterface
	public interface GeneradorGuardable {
		Generado generar() throws IOException;
	}

	// Raíz del volumen, igual que las demás rutas que se guardan en la base (app.archivos.raiz).
	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	@Value("${app.modo-desarrollador:false}")
	private boolean modoDesarrollador;

	@Autowired
	private JdbcTemplate jdbc;

	// La raíz ya normalizada. Se calcula una vez en prepararRaiz.
	private Path raiz;

	// @PostConstruct: Spring lo llama al terminar de construir el objeto, cuando los @Value ya están
	// puestos. No se puede hacer en el constructor: ahí los campos inyectados todavía están vacíos.
	@PostConstruct
	void prepararRaiz() {
		raiz = Path.of(raizConfigurada).toAbsolutePath().normalize();
		invalidarPdfDeOtraVersion();
	}

	// Si la marca del volumen no es la versión actual del PDF, borra las filas y los archivos experimento.pdf de la versión
	// anterior. Si no se puede, el arranque falla: servir un PDF de la versión anterior sería peor que no arrancar.
	//
	// Esa decisión es la misma filosofía de ddl-auto=validate y del JWT_SECRET corto: ante la duda, no
	// arrancar. Un PDF viejo que nadie sabe que es viejo puede acabar en un artículo.
	void invalidarPdfDeOtraVersion() {
		Path reportes = raiz.resolve("reportes");
		Path marca = reportes.resolve(MARCA_VERSION);
		try {
			// Camino normal: la marca existe y coincide. No hay nada que hacer y se sale enseguida.
			if (Files.isRegularFile(marca) && VERSION_PDF.equals(Files.readString(marca).trim())) {
				return;
			}
			// Se borran las filas de TODOS los PDF, de todos los experimentos. El ::formato_reporte es la
			// conversión explícita de PostgreSQL al tipo del dominio: sin ella, el texto no cuadra con la
			// columna (ver la entidad Reporte).
			jdbc.update("DELETE FROM reporte WHERE formato = 'PDF'::formato_reporte");
			if (Files.isDirectory(reportes)) {
				// try-con-recursos: Files.list abre descriptores del sistema y hay que cerrarlos.
				try (Stream<Path> carpetas = Files.list(reportes)) {
					for (Path carpeta : carpetas.toList()) {
						// Solo el PDF: los CSV y XLSX de cada carpeta no dependen de esta versión y se
						// quedan. deleteIfExists no se queja si no está.
						if (Files.isDirectory(carpeta)) { // la marca de versión es un archivo, no una carpeta de experimento
								Files.deleteIfExists(carpeta.resolve("experimento.pdf"));
							}
					}
				}
			}
			// Se deja la marca con la versión nueva, para que el próximo arranque no repita el borrado.
			Files.createDirectories(reportes);
			Files.writeString(marca, VERSION_PDF);
			log.info("PDF de resultados invalidados: la versión guardada no coincide con la {}", VERSION_PDF);
		} catch (IOException | DataAccessException e) {
			// Lanzar desde un @PostConstruct detiene el arranque de Spring: el contenedor muere y el error
			// sale en el log, que es exactamente lo que se quiere.
			throw new IllegalStateException("No se pudieron invalidar los PDF de resultados de otra versión", e);
		}
	}

	// La ruta relativa a la raíz que se guarda en REPORTE.ruta. El PDF es solo del experimento; CSV y XLSX son del grupo o
	// de la tanda. Cualquier otra combinación, o un alcance con otro texto, es IllegalArgumentException.
	//
	// IllegalArgumentException y no ErrorApi: un alcance mal formado es un error de programación, no del
	// usuario. El usuario nunca escribe este texto; lo arma el código a partir de ids ya validados.
	// La validación es además una defensa: el alcance acaba dentro de una ruta de archivo, y aceptar
	// cualquier texto permitiría colar un "../" y escribir fuera del volumen.
	public static String rutaRelativa(int idExperimento, FormatoReporte formato, String alcance) {
		// Las dos únicas combinaciones válidas, cada una en su booleano con nombre: se lee mejor que un
		// if con cuatro condiciones encadenadas.
		boolean pdfDelExperimento = formato == FormatoReporte.PDF && "experimento".equals(alcance);
		boolean tablaDeGrupoOTanda = formato != FormatoReporte.PDF && alcance != null
				&& GRUPO_O_TANDA.matcher(alcance).matches();
		if (!pdfDelExperimento && !tablaDeGrupoOTanda) {
			throw new IllegalArgumentException("Formato y alcance no válidos: " + formato + " / " + alcance);
		}
		// La extensión sale del propio enum en minúsculas: "PDF" pasa a "pdf".
		return "reportes/" + idExperimento + "/" + alcance + "." + formato.valor().toLowerCase();
	}

	// Entrega el reporte: el archivo guardado si hay fila y el archivo existe; si no, lo genera, lo guarda y registra su
	// fila (o refresca la existente). Primero bloquea el experimento (FOR UPDATE), así que una descarga y un cambio del
	// experimento no se cruzan. 404 si el experimento no existe. Todo va en la misma transacción.
	//
	// "Fila Y archivo": se comprueban los dos porque pueden desincronizarse (alguien borró el archivo a
	// mano, o el disco falló). Si falta cualquiera de los dos, se regenera.
	@Transactional
	public byte[] obtener(Integer idExperimento, FormatoReporte formato, String alcance, Generador generador)
			throws IOException {
		// Es el caso simple del método de abajo: un generador que siempre quiere guardar.
		return obtenerSiCorresponde(idExperimento, formato, alcance, () -> new Generado(generador.generar(), true));
	}

	// Como obtener, pero el generador decide si el resultado se guarda (un PDF sin resultados no se guarda: el siguiente
	// análisis completado lo haría viejo sin que nada lo invalide). Con el modo desarrollador no se guarda nunca.
	//
	// El paréntesis explica el caso que obligó a separar los dos métodos: un PDF que dice "sin
	// resultados" no debe guardarse, porque ninguna de las cuatro invalidaciones lo tocaría cuando por
	// fin termine un análisis, y el investigador seguiría descargando el PDF vacío.
	@Transactional
	public byte[] obtenerSiCorresponde(Integer idExperimento, FormatoReporte formato, String alcance,
			GeneradorGuardable generador) throws IOException {
		// El candado va PRIMERO, antes de leer nada. Es lo que impide que una descarga lea datos viejos
		// mientras un cambio está a medio confirmar y acabe guardando un reporte equivocado.
		// Si la lista viene vacía, el experimento no existe.
		if (jdbc.queryForList("SELECT idexperimento FROM experimento WHERE idexperimento = ? FOR UPDATE",
				Integer.class, idExperimento).isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + idExperimento + " no existe");
		}
		if (modoDesarrollador) {
			return generador.generar().bytes();     // con los tiempos del análisis: no se guarda
			// (el PDF de desarrollo lleva tiempos y modelos; guardarlo haría que alguien con el modo
			// apagado se descargara después ese mismo archivo con información que no le corresponde)
		}

		String ruta = rutaRelativa(idExperimento, formato, alcance);
		Path archivo = archivoDe(ruta);
		Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM reporte WHERE ruta = ?", Integer.class, ruta);
		// ACIERTO de caché: hay fila y el archivo está. Se devuelve sin generar nada.
		if (filas != null && filas > 0 && Files.isRegularFile(archivo)) {
			return Files.readAllBytes(archivo);
		}

		// FALLO de caché: ahora sí se ejecuta el generador, que es el trabajo caro.
		Generado generado = generador.generar();
		// El generador puede decir que no merece guardarse (el PDF sin resultados).
		if (!generado.guardar()) {
			return generado.bytes();
		}
		byte[] bytes = generado.bytes();
		escribirAtomico(archivo, bytes);
		// ON CONFLICT: si la fila ya estaba (el archivo faltaba), se refresca la fecha en vez de duplicarla.
		//
		// Es el "insertar o actualizar" de PostgreSQL. Funciona porque la columna ruta es UNIQUE (ver la
		// entidad Reporte): ese es el conflicto que detecta. Sin esto habría que comprobar antes si la
		// fila existe, con la carrera que eso implica.
		jdbc.update("INSERT INTO reporte (idexperimento, formato, ruta, fechageneracion) "
				+ "VALUES (?, ?::formato_reporte, ?, now()) "
				+ "ON CONFLICT (ruta) DO UPDATE SET fechageneracion = now()", idExperimento, formato.valor(), ruta);
		return bytes;
	}

	// Bloquea la fila del experimento (FOR UPDATE). Debe ser el PRIMER paso de toda transacción que cambie datos que un
	// reporte muestra, antes de tocar análisis, SEGUNDO u otra tabla. Así una descarga (obtener) y un cambio se esperan
	// uno al otro: la descarga no puede leer datos viejos y guardarlos mientras el cambio aún no confirma. Como todos
	// bloquean primero el experimento, no hay interbloqueos.
	//
	// Es el mismo principio que CandadosDeCuenta: un ORDEN fijo de bloqueo evita que dos transacciones se
	// esperen en círculo. Aquí el experimento es el tercer nivel de ese orden.
	public void bloquearExperimento(Integer idExperimento) {
		jdbc.queryForList("SELECT idexperimento FROM experimento WHERE idexperimento = ? FOR UPDATE",
				Integer.class, idExperimento);
	}

	// Un reporte guardado ya no refleja los datos: se borran sus filas y, DESPUÉS del commit, la carpeta reportes/{id}/.
	// Quien llama debe estar dentro de su transacción (así el borrado de la carpeta espera al commit). Si no la hay, la
	// carpeta se borra al momento. Un fallo al borrar la carpeta solo se registra: la fila ya no existe, así que la
	// próxima descarga regenera el reporte.
	//
	// El "DESPUÉS del commit" resuelve un desajuste de fondo: la base sabe deshacer una transacción, el
	// sistema de archivos no. Si se borrara la carpeta al momento y luego la transacción fallara, las
	// filas volverían pero los archivos ya no estarían. Esperando al commit, el borrado solo ocurre si el
	// cambio de verdad se confirmó.
	public void invalidar(Integer idExperimento) {
		bloquearExperimento(idExperimento);     // por si quien llama no lo bloqueó antes: repetirlo no cuesta nada
		jdbc.update("DELETE FROM reporte WHERE idexperimento = ?", idExperimento);
		// ¿Hay una transacción abierta? Si la hay, se registra un "aviso para después del commit".
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					// Esto corre cuando la transacción YA confirmó. Si se deshiciera, no se ejecuta.
					borrarCarpeta(idExperimento);
				}
			});
		} else {
			// Sin transacción (por ejemplo, desde una prueba) no hay commit que esperar.
			borrarCarpeta(idExperimento);
		}
	}

	// Cambió el nombre o los apellidos de una cuenta: el PDF de cada experimento suyo muestra el nombre del creador, así que sus
	// reportes guardados ya no lo reflejan. Bloquea TODOS los experimentos de la cuenta en orden de idexperimento (un solo
	// SELECT ... FOR UPDATE con ORDER BY: PostgreSQL bloquea en el orden del ORDER BY) y luego invalida cada reporte. Va después
	// de la fila de la cuenta (CandadosDeCuenta) y antes del UPDATE de usuario. Lo usan PATCH /users/me y PATCH /admin/users/{id}.
	//
	// Es el tercer motivo de invalidación, y el menos evidente: cambiar un apellido no toca ningún dato
	// científico, pero el PDF lleva impreso el nombre del creador y quedaría desfasado.
	// El ORDER BY del SELECT es lo que fija el orden de los candados y evita interbloqueos; el bucle de
	// abajo ya los encuentra tomados y no vuelve a esperar por ellos.
	public void invalidarDeLaCuenta(String idInstitucional) {
		List<Integer> experimentos = jdbc.queryForList(
				"SELECT idexperimento FROM experimento WHERE idinstitucional = ? ORDER BY idexperimento FOR UPDATE",
				Integer.class, idInstitucional);
		for (Integer idExperimento : experimentos) {
			invalidar(idExperimento);
		}
	}

	// El archivo de la ruta relativa, dentro de la raíz. Las rutas salen de ids y de constantes, nunca de la petición.
	//
	// La comprobación startsWith es la defensa contra salirse del volumen: normalize() resuelve los "..",
	// y si después de resolverlos la ruta ya no empieza por la raíz, es que apuntaba fuera.
	// Aquí es doblemente seguro (rutaRelativa ya valida el alcance), pero es la costumbre correcta:
	// comprobar en el punto donde se construye la ruta, no confiar en quien llama.
	private Path archivoDe(String ruta) {
		Path archivo = raiz.resolve(ruta).normalize();
		if (!archivo.startsWith(raiz)) {
			throw new IllegalArgumentException("La ruta del reporte sale de la raíz: " + ruta);
		}
		return archivo;
	}

	// Escribe a un temporal en la misma carpeta y luego renombra: un corte a media escritura nunca deja un PDF a medias.
	//
	// Es el patrón de ESCRITURA ATÓMICA. Si se escribiera directo al destino y se cortara la luz a mitad,
	// quedaría un PDF truncado con su fila diciendo que está bien, y nadie lo regeneraría.
	// Funciona porque renombrar dentro del MISMO sistema de archivos es atómico: o pasó o no pasó, nunca a
	// medias. De ahí que el temporal tenga que estar en la misma carpeta y no en /tmp.
	private void escribirAtomico(Path destino, byte[] datos) throws IOException {
		Files.createDirectories(destino.getParent());
		// resolveSibling deja el temporal al lado del destino, con el mismo nombre más ".tmp".
		Path temporal = destino.resolveSibling(destino.getFileName() + ".tmp");
		Files.write(temporal, datos);
		// REPLACE_EXISTING pisa el archivo viejo; ATOMIC_MOVE exige que el renombrado sea atómico.
		Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	// Borra reportes/{id}/ entera. Un fallo es solo un aviso en el log.
	//
	// Es solo un aviso porque las filas ya se borraron: aunque queden archivos sueltos, la próxima
	// descarga regenera el reporte y lo pisa. Se desperdicia algo de disco, nada más.
	private void borrarCarpeta(Integer idExperimento) {
		Path carpeta = raiz.resolve("reportes").resolve(String.valueOf(idExperimento)).normalize();
		// La misma comprobación de siempre, y además que la carpeta exista. Si no, no hay nada que hacer.
		if (!carpeta.startsWith(raiz) || !Files.isDirectory(carpeta)) {
			return;
		}
		try {
			// Files.walkFileTree con un visitante: Java no trae un "borrar carpeta con todo dentro".
			// Hace falta recorrerla borrando los archivos y, al salir de cada carpeta, la carpeta misma.
			Files.walkFileTree(carpeta, new SimpleFileVisitor<Path>() {
				// Se llama por cada archivo encontrado.
				@Override
				public FileVisitResult visitFile(Path archivo, BasicFileAttributes atributos) throws IOException {
					Files.delete(archivo);
					// CONTINUE: sigue con el recorrido.
					return FileVisitResult.CONTINUE;
				}

				// postVisitDirectory se llama DESPUÉS de recorrer el contenido de la carpeta, que es
				// cuando ya está vacía y se puede borrar. Si se hiciera antes (preVisitDirectory),
				// fallaría por no estar vacía.
				@Override
				public FileVisitResult postVisitDirectory(Path directorio, IOException e) throws IOException {
					Files.delete(directorio);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			log.warn("No se pudo borrar la carpeta de reportes {}: {}", carpeta, e.getMessage());
		}
	}
}
