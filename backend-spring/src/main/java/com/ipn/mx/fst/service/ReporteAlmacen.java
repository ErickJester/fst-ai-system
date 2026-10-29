package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.valores.FormatoReporte;
import com.ipn.mx.fst.utilerias.ErrorApi;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
@Service
public class ReporteAlmacen {

	private static final Logger log = LoggerFactory.getLogger(ReporteAlmacen.class);
	private static final Pattern GRUPO_O_TANDA = Pattern.compile("(grupo|tanda)-\\d+");

	// Genera los bytes del reporte (el generarReporte de ExperimentoServiceImpl para el PDF).
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

	private Path raiz;

	@PostConstruct
	void prepararRaiz() {
		raiz = Path.of(raizConfigurada).toAbsolutePath().normalize();
	}

	// La ruta relativa a la raíz que se guarda en REPORTE.ruta. El PDF es solo del experimento; CSV y XLSX son del grupo o
	// de la tanda. Cualquier otra combinación, o un alcance con otro texto, es IllegalArgumentException.
	public static String rutaRelativa(int idExperimento, FormatoReporte formato, String alcance) {
		boolean pdfDelExperimento = formato == FormatoReporte.PDF && "experimento".equals(alcance);
		boolean tablaDeGrupoOTanda = formato != FormatoReporte.PDF && alcance != null
				&& GRUPO_O_TANDA.matcher(alcance).matches();
		if (!pdfDelExperimento && !tablaDeGrupoOTanda) {
			throw new IllegalArgumentException("Formato y alcance no válidos: " + formato + " / " + alcance);
		}
		return "reportes/" + idExperimento + "/" + alcance + "." + formato.valor().toLowerCase();
	}

	// Entrega el reporte: el archivo guardado si hay fila y el archivo existe; si no, lo genera, lo guarda y registra su
	// fila (o refresca la existente). Primero bloquea el experimento (FOR UPDATE), así que una descarga y un cambio del
	// experimento no se cruzan. 404 si el experimento no existe. Todo va en la misma transacción.
	@Transactional
	public byte[] obtener(Integer idExperimento, FormatoReporte formato, String alcance, Generador generador)
			throws IOException {
		return obtenerSiCorresponde(idExperimento, formato, alcance, () -> new Generado(generador.generar(), true));
	}

	// Como obtener, pero el generador decide si el resultado se guarda (un PDF sin resultados no se guarda: el siguiente
	// análisis completado lo haría viejo sin que nada lo invalide). Con el modo desarrollador no se guarda nunca.
	@Transactional
	public byte[] obtenerSiCorresponde(Integer idExperimento, FormatoReporte formato, String alcance,
			GeneradorGuardable generador) throws IOException {
		if (jdbc.queryForList("SELECT idexperimento FROM experimento WHERE idexperimento = ? FOR UPDATE",
				Integer.class, idExperimento).isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + idExperimento + " no existe");
		}
		if (modoDesarrollador) {
			return generador.generar().bytes();     // con los tiempos del análisis: no se guarda
		}

		String ruta = rutaRelativa(idExperimento, formato, alcance);
		Path archivo = archivoDe(ruta);
		Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM reporte WHERE ruta = ?", Integer.class, ruta);
		if (filas != null && filas > 0 && Files.isRegularFile(archivo)) {
			return Files.readAllBytes(archivo);
		}

		Generado generado = generador.generar();
		if (!generado.guardar()) {
			return generado.bytes();
		}
		byte[] bytes = generado.bytes();
		escribirAtomico(archivo, bytes);
		// ON CONFLICT: si la fila ya estaba (el archivo faltaba), se refresca la fecha en vez de duplicarla.
		jdbc.update("INSERT INTO reporte (idexperimento, formato, ruta, fechageneracion) "
				+ "VALUES (?, ?::formato_reporte, ?, now()) "
				+ "ON CONFLICT (ruta) DO UPDATE SET fechageneracion = now()", idExperimento, formato.valor(), ruta);
		return bytes;
	}

	// Bloquea la fila del experimento (FOR UPDATE). Debe ser el PRIMER paso de toda transacción que cambie datos que un
	// reporte muestra, antes de tocar análisis, SEGUNDO u otra tabla. Así una descarga (obtener) y un cambio se esperan
	// uno al otro: la descarga no puede leer datos viejos y guardarlos mientras el cambio aún no confirma. Como todos
	// bloquean primero el experimento, no hay interbloqueos.
	public void bloquearExperimento(Integer idExperimento) {
		jdbc.queryForList("SELECT idexperimento FROM experimento WHERE idexperimento = ? FOR UPDATE",
				Integer.class, idExperimento);
	}

	// Un reporte guardado ya no refleja los datos: se borran sus filas y, DESPUÉS del commit, la carpeta reportes/{id}/.
	// Quien llama debe estar dentro de su transacción (así el borrado de la carpeta espera al commit). Si no la hay, la
	// carpeta se borra al momento. Un fallo al borrar la carpeta solo se registra: la fila ya no existe, así que la
	// próxima descarga regenera el reporte.
	public void invalidar(Integer idExperimento) {
		bloquearExperimento(idExperimento);     // por si quien llama no lo bloqueó antes: repetirlo no cuesta nada
		jdbc.update("DELETE FROM reporte WHERE idexperimento = ?", idExperimento);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					borrarCarpeta(idExperimento);
				}
			});
		} else {
			borrarCarpeta(idExperimento);
		}
	}

	// Cambió el nombre o los apellidos de una cuenta: el PDF de cada experimento suyo muestra el nombre del creador, así que sus
	// reportes guardados ya no lo reflejan. Bloquea TODOS los experimentos de la cuenta en orden de idexperimento (un solo
	// SELECT ... FOR UPDATE con ORDER BY: PostgreSQL bloquea en el orden del ORDER BY) y luego invalida cada reporte. Va después
	// de la fila de la cuenta (CandadosDeCuenta) y antes del UPDATE de usuario. Lo usan PATCH /users/me y PATCH /admin/users/{id}.
	public void invalidarDeLaCuenta(String idInstitucional) {
		List<Integer> experimentos = jdbc.queryForList(
				"SELECT idexperimento FROM experimento WHERE idinstitucional = ? ORDER BY idexperimento FOR UPDATE",
				Integer.class, idInstitucional);
		for (Integer idExperimento : experimentos) {
			invalidar(idExperimento);
		}
	}

	// El archivo de la ruta relativa, dentro de la raíz. Las rutas salen de ids y de constantes, nunca de la petición.
	private Path archivoDe(String ruta) {
		Path archivo = raiz.resolve(ruta).normalize();
		if (!archivo.startsWith(raiz)) {
			throw new IllegalArgumentException("La ruta del reporte sale de la raíz: " + ruta);
		}
		return archivo;
	}

	// Escribe a un temporal en la misma carpeta y luego renombra: un corte a media escritura nunca deja un PDF a medias.
	private void escribirAtomico(Path destino, byte[] datos) throws IOException {
		Files.createDirectories(destino.getParent());
		Path temporal = destino.resolveSibling(destino.getFileName() + ".tmp");
		Files.write(temporal, datos);
		Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	// Borra reportes/{id}/ entera. Un fallo es solo un aviso en el log.
	private void borrarCarpeta(Integer idExperimento) {
		Path carpeta = raiz.resolve("reportes").resolve(String.valueOf(idExperimento)).normalize();
		if (!carpeta.startsWith(raiz) || !Files.isDirectory(carpeta)) {
			return;
		}
		try {
			Files.walkFileTree(carpeta, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path archivo, BasicFileAttributes atributos) throws IOException {
					Files.delete(archivo);
					return FileVisitResult.CONTINUE;
				}

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
