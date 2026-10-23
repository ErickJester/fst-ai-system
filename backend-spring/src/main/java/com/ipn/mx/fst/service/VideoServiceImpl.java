package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Experimento;
import com.ipn.mx.fst.repository.ExperimentoRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
import com.ipn.mx.fst.utilerias.Ffprobe;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

// Subida de videos. Orden de la guía (sección 4.1): el archivo se escribe primero y las filas al final, y si
// algo falla no queda basura (ni archivo ni filas). Las filas se insertan con SQL explícito (JdbcTemplate) en
// una sola transacción: ANALISIS lleva solo idvideo, para que estado, etapa, nivelclasif y fechaanalisis
// tomen los DEFAULT de la tabla (los valores de arranque, D-79). Después de encolar, el backend ya no toca
// esos campos: son del worker (sección 4.7).
@Service
public class VideoServiceImpl implements VideoService {

	private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);

	private static final Set<String> TIPOS = Set.of("control", "referencia", "tratamiento experimental");
	private static final String DANADO = "El video no se puede reproducir: el archivo está dañado o incompleto.";

	// Raíz del volumen videos_reportes. Las rutas que se guardan en la base son RELATIVAS a ella.
	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	@Autowired
	private ExperimentoRepository experimentoRepository;

	@Autowired
	private Ffprobe ffprobe;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private Path raiz;

	// videos/ guarda los archivos y tmp/ es donde Spring deja la subida mientras llega (multipart.location):
	// así un video de 1 GB no pasa por el /tmp del contenedor y se mueve a su lugar sin copiarse.
	@PostConstruct
	void prepararCarpetas() {
		raiz = Path.of(raizConfigurada);
		try {
			Files.createDirectories(raiz.resolve("videos"));
			Files.createDirectories(raiz.resolve("tmp"));
		} catch (IOException e) {
			log.error("No se pudieron crear las carpetas de archivos en {}: {}", raiz, e.getMessage());
		}
	}

	@Override
	public Map<String, Object> subir(Integer idExperimento, String etiquetaGrupo, MultipartFile archivo,
			String sesion, String tipo, String tratamiento, Integer nCilindros, Integer ordinal,
			Authentication authentication) {
		// 1. Todo lo que se puede rechazar sin tocar el disco.
		Experimento experimento = experimentoRepository.findById(idExperimento).orElseThrow(
				() -> new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + idExperimento + " no existe"));
		// D-107: solo quien creó el experimento sube sus videos, sea Investigador o Administrador. VIDEO no guarda
		// quién lo subió y el backend reporta al creador del experimento como "subidoPor"; si subiera otra cuenta,
		// ese dato sería falso.
		if (!experimento.getIdInstitucional().equals(authentication.getName())) {
			throw new ErrorApi(HttpStatus.FORBIDDEN, "Solo quien creó el experimento sube sus videos.");
		}
		String etiqueta = etiquetaGrupo == null ? "" : etiquetaGrupo.trim();
		if (etiqueta.isEmpty()) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "La etiqueta del grupo no puede estar vacía.");
		}
		if (archivo == null || archivo.isEmpty()) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "Falta el archivo del video.");
		}
		if (!"Dia 1".equals(sesion) && !"Dia 2".equals(sesion)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "La sesión debe ser 'Dia 1' o 'Dia 2'.");
		}
		String extension = extensionDe(archivo.getOriginalFilename());
		if (extension == null) {
			throw unprocessable("Solo se aceptan videos .mp4 o .mov.");
		}
		tipo = limpiar(tipo);
		tratamiento = limpiar(tratamiento);
		// Grupo y tanda: se revisan ya (sin crear nada) para no escribir un video de 1 GB que se va a rechazar.
		resolverDestino(idExperimento, etiqueta, sesion, tipo, tratamiento, nCilindros, ordinal, false);

		// 2. El archivo, a un nombre temporal dentro de videos/ (el mismo volumen que el nombre final).
		Path temporal = raiz.resolve("videos").resolve("subiendo-" + UUID.randomUUID() + "." + extension);
		Path[] movido = {null};
		try {
			archivo.transferTo(temporal);

			// 3. ¿Se puede reproducir y es horizontal? Si no, se borra el temporal (en el catch de abajo).
			Ffprobe.Sonda sonda = ffprobe.sondear(temporal);
			BigDecimal duracion = sonda == null ? null
					: BigDecimal.valueOf(sonda.duracion()).setScale(2, RoundingMode.HALF_UP);
			if (sonda == null || sonda.ancho() <= 0 || sonda.alto() <= 0 || duracion.signum() <= 0) {
				throw unprocessable(DANADO);
			}
			if (sonda.ancho() <= sonda.alto()) {
				throw unprocessable("El video es vertical: se necesita un video horizontal, con los cilindros de lado a lado.");
			}

			// 4. Las filas, en una sola transacción. El archivo se mueve a su nombre final DENTRO de ella:
			// si algo falla, no se confirma nada y el catch borra el archivo, esté donde esté.
			String tipoFinal = tipo;
			String tratamientoFinal = tratamiento;
			// Solo para mostrarlo en Resultados y en los reportes; el archivo se guarda como videos/{id}.{ext}.
			String nombreOriginal = nombreParaMostrar(archivo.getOriginalFilename());
			return new TransactionTemplate(transactionManager).execute(estado -> {
				Integer idTanda = resolverDestino(idExperimento, etiqueta, sesion, tipoFinal, tratamientoFinal,
						nCilindros, ordinal, true);
				Integer idVideo = jdbc.queryForObject(
						"INSERT INTO video (idtanda, sesion, duracion, nombreoriginal) VALUES (?, ?, ?, ?) RETURNING idvideo",
						Integer.class, idTanda, sesion, duracion, nombreOriginal);
				// La ruta es relativa a la raíz del volumen, como la guarda y la lee todo el sistema.
				String relativa = "videos/" + idVideo + "." + extension;
				jdbc.update("UPDATE video SET archivo = ? WHERE idvideo = ?", relativa, idVideo);
				Path destino = raiz.resolve(relativa);
				try {
					Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
				movido[0] = destino;

				Map<String, Object> datos = new LinkedHashMap<>();
				datos.put("idVideo", idVideo);
				if ("Dia 2".equals(sesion)) {
					// Solo el Día 2 se analiza (D-71). Los valores de arranque salen de los DEFAULT de la tabla.
					Map<String, Object> analisis = jdbc.queryForMap(
							"INSERT INTO analisis (idvideo) VALUES (?) RETURNING idanalisis, estado", idVideo);
					datos.put("idAnalisis", analisis.get("idanalisis"));
					datos.put("estadoAnalisis", analisis.get("estado"));
				} else {
					datos.put("idAnalisis", null);
					datos.put("estadoAnalisis", null);
				}
				return datos;
			});
		} catch (DataIntegrityViolationException e) {
			// Las llaves únicas (grupo, tanda, video por sesión) cubren la carrera entre dos subidas.
			borrar(temporal, movido[0]);
			throw new ErrorApi(HttpStatus.CONFLICT,
					"Otra subida se adelantó con el mismo grupo, tanda o video. Revisa e intenta de nuevo.");
		} catch (IOException | UncheckedIOException e) {
			borrar(temporal, movido[0]);
			log.error("No se pudo guardar el video del experimento {}", idExperimento, e);
			throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el video en el servidor.");
		} catch (RuntimeException e) {
			borrar(temporal, movido[0]);
			throw e;
		}
	}

	// Encuentra el grupo y la tanda donde va el video y revisa que se pueda: tipo y tratamiento si el grupo es
	// nuevo, entre 2 y 4 cilindros si la tanda es nueva y que la tanda no tenga ya el video de esa sesión.
	// Con crear = false solo revisa; con crear = true además crea lo que falta. Devuelve el idTanda (null si
	// no se creó). Si el grupo ya existe, tipo y tratamiento se ignoran; si la tanda ya existe, nCilindros también.
	private Integer resolverDestino(Integer idExperimento, String etiqueta, String sesion, String tipo,
			String tratamiento, Integer nCilindros, Integer ordinalPedido, boolean crear) {
		Integer idGrupo = primero(jdbc.queryForList(
				"SELECT idgrupo FROM grupo WHERE idexperimento = ? AND etiqueta = ?", Integer.class,
				idExperimento, etiqueta));
		if (idGrupo == null) {
			if (tipo == null || tratamiento == null) {
				throw unprocessable("Un grupo nuevo necesita tipo y tratamiento.");
			}
			if (!TIPOS.contains(tipo)) {
				throw unprocessable("El tipo de grupo debe ser 'control', 'referencia' o 'tratamiento experimental'.");
			}
			if (crear) {
				idGrupo = jdbc.queryForObject(
						"INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, ?, ?) RETURNING idgrupo",
						Integer.class, idExperimento, etiqueta, tipo, tratamiento);
			}
		}

		// La tanda confirmada por el usuario; si no viene, la siguiente del grupo.
		int ordinal;
		if (ordinalPedido != null) {
			if (ordinalPedido < 1 || ordinalPedido > Short.MAX_VALUE) {
				throw unprocessable("El ordinal de la tanda no es válido.");
			}
			ordinal = ordinalPedido;
		} else if (idGrupo == null) {
			ordinal = 1;
		} else {
			ordinal = jdbc.queryForObject("SELECT COALESCE(MAX(ordinal), 0) + 1 FROM tanda WHERE idgrupo = ?",
					Integer.class, idGrupo);
		}

		Integer idTanda = idGrupo == null ? null : primero(jdbc.queryForList(
				"SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, idGrupo, ordinal));
		if (idTanda == null) {
			if (nCilindros == null || nCilindros < 2 || nCilindros > 4) {
				throw unprocessable("Una tanda nueva necesita entre 2 y 4 cilindros.");
			}
			if (crear) {
				idTanda = jdbc.queryForObject(
						"INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, ?) RETURNING idtanda",
						Integer.class, idGrupo, ordinal, nCilindros);
				for (int cilindro = 1; cilindro <= nCilindros; cilindro++) {
					jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, ?)", idTanda, cilindro);
				}
			}
		} else if (!jdbc.queryForList("SELECT 1 FROM video WHERE idtanda = ? AND sesion = ?", Integer.class,
				idTanda, sesion).isEmpty()) {
			String letra = String.valueOf((char) ('A' + ordinal - 1));
			String dia = "Dia 1".equals(sesion) ? "Día 1" : "Día 2";
			throw new ErrorApi(HttpStatus.CONFLICT, "La Tanda " + letra + " ya tiene su video de " + dia
					+ ". Cada tanda lleva un solo video por sesión.");
		}
		return idTanda;
	}

	// "mp4" o "mov" según la extensión del nombre original (sin distinguir mayúsculas: .MOV vale), o null
	// si es otra cosa (R-09).
	private static String extensionDe(String nombre) {
		if (nombre == null) {
			return null;
		}
		String minusculas = nombre.toLowerCase(Locale.ROOT);
		if (minusculas.endsWith(".mp4")) {
			return "mp4";
		}
		if (minusculas.endsWith(".mov")) {
			return "mov";
		}
		return null;
	}

	// El nombre del archivo como lo subió el usuario, para mostrarlo: respeta mayúsculas y acentos ("AcuBN1.MOV" queda
	// tal cual). Algunos navegadores mandan la ruta completa (C:\carpeta\archivo.mp4): solo vale lo que sigue al
	// último / o \. Sin espacios de los lados, sin caracteres de control y de a lo más 255 caracteres (el largo de
	// la columna). Vacío es null. NUNCA se usa para armar rutas: el archivo se guarda como videos/{id}.{ext}.
	static String nombreParaMostrar(String original) {
		if (original == null) {
			return null;
		}
		String nombre = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
		StringBuilder sinControl = new StringBuilder();
		nombre.codePoints().filter(c -> !Character.isISOControl(c)).forEach(sinControl::appendCodePoint);
		String texto = sinControl.toString().trim();
		if (texto.codePointCount(0, texto.length()) > 255) {
			texto = texto.substring(0, texto.offsetByCodePoints(0, 255));
		}
		return texto.isEmpty() ? null : texto;
	}

	// Texto sin espacios de los lados; vacío o ausente es null.
	private static String limpiar(String texto) {
		if (texto == null) {
			return null;
		}
		String limpio = texto.trim();
		return limpio.isEmpty() ? null : limpio;
	}

	private static Integer primero(List<Integer> ids) {
		return ids.isEmpty() ? null : ids.get(0);
	}

	// 422: la petición está bien formada pero el contenido no se puede aceptar.
	private static ErrorApi unprocessable(String mensaje) {
		return new ErrorApi(HttpStatus.valueOf(422), mensaje);
	}

	// Borra el temporal y el archivo ya movido (el que no exista se ignora). Es la limpieza de "si algo falla
	// no queda basura".
	private void borrar(Path... archivos) {
		for (Path archivo : archivos) {
			if (archivo == null) {
				continue;
			}
			try {
				Files.deleteIfExists(archivo);
			} catch (IOException e) {
				log.warn("No se pudo borrar {}: {}", archivo, e.getMessage());
			}
		}
	}

}
