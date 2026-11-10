// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Experimento;
// Los dos enums de dominio que se validan aquí.
import com.ipn.mx.fst.domain.valores.SesionVideo;
import com.ipn.mx.fst.domain.valores.TipoGrupo;
import com.ipn.mx.fst.repository.ExperimentoRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
// Ffprobe: mide el video con el programa externo.
import com.ipn.mx.fst.utilerias.Ffprobe;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
// UncheckedIOException: envuelve una IOException para poder lanzarla desde una lambda (ver abajo).
import java.io.UncheckedIOException;
// BigDecimal y RoundingMode: la duración exacta con dos decimales.
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
// Locale: para que toLowerCase no dependa de la configuración regional (ver abajo).
import java.util.Locale;
import java.util.Map;
import java.util.Set;
// UUID: un identificador único para el nombre temporal.
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
// Las dos clases para abrir una transacción A MANO, en lugar de con @Transactional.
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

// Subida de videos. Orden de la guía (sección 4.1): el archivo se escribe primero y las filas al final, y si
// algo falla no queda basura (ni archivo ni filas). Las filas se insertan con SQL explícito (JdbcTemplate) en
// una sola transacción: ANALISIS lleva solo idvideo, para que estado, etapa, nivelclasif y fechaanalisis
// tomen los DEFAULT de la tabla (los valores de arranque, D-79). Después de encolar, el backend ya no toca
// esos campos: son del worker (sección 4.7).
//
// Es el método más complejo del backend, y su dificultad no es el código sino la COORDINACIÓN: una sola
// petición puede crear hasta cinco cosas (grupo, tanda, especímenes, video y análisis) y además escribir un
// archivo de un gigabyte. La base sabe deshacer sus cambios; el disco no. De ahí el orden y la limpieza.
// Fíjate en la estrategia general, numerada en el método: primero se rechaza TODO lo que se pueda rechazar
// sin tocar el disco, y solo entonces se escribe el archivo. Rechazar un video de 1 GB después de
// recibirlo entero sería un desperdicio para el usuario y para el servidor.
@Service
public class VideoServiceImpl implements VideoService {

	private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);

	// Los valores del dominio tipo_grupo de la base, tomados del enum.
	// Un Set y no una List: solo se usa para preguntar "¿está este valor?", que en un conjunto es inmediato.
	// toUnmodifiableSet lo deja inmutable: nadie puede añadirle un tipo por descuido.
	private static final Set<String> TIPOS = Arrays.stream(TipoGrupo.values()).map(TipoGrupo::valor)
			.collect(Collectors.toUnmodifiableSet());
	// Largo máximo de la etiqueta del grupo (columna varchar(100)) y de la letra de la tanda (A a Z = 1 a 26).
	private static final int LARGO_ETIQUETA = 100;
	private static final int MAX_TANDAS = 26;
	private static final String DANADO ="El video no se puede reproducir: el archivo está dañado o incompleto.";

	// Raíz del volumen videos_reportes. Las rutas que se guardan en la base son RELATIVAS a ella.
	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	@Autowired
	private ExperimentoRepository experimentoRepository;

	@Autowired
	private Ffprobe ffprobe;

	@Autowired
	private JdbcTemplate jdbc;

	// El administrador de transacciones, para abrir una a mano (ver el TransactionTemplate de abajo).
	@Autowired
	private PlatformTransactionManager transactionManager;

	private Path raiz;

	// videos/ guarda los archivos y tmp/ es donde Spring deja la subida mientras llega (multipart.location):
	// así un video de 1 GB no pasa por el /tmp del contenedor y se mueve a su lugar sin copiarse.
	//
	// La diferencia entre mover y copiar es enorme aquí: dentro del mismo sistema de archivos, mover es
	// instantáneo (solo cambia el nombre); copiar un gigabyte tarda y lo duplica mientras dura.
	@PostConstruct
	void prepararCarpetas() {
		raiz = Path.of(raizConfigurada);
		try {
			Files.createDirectories(raiz.resolve("videos"));
			Files.createDirectories(raiz.resolve("tmp"));
		} catch (IOException e) {
			// Solo se registra: si las carpetas no se pueden crear, el arranque sigue y el fallo saldrá
			// al primer intento de subida, con su mensaje.
			log.error("No se pudieron crear las carpetas de archivos en {}: {}", raiz, e.getMessage());
		}
	}

	@Override
	// Sin @Transactional en el método: la transacción se abre A MANO más abajo, y solo alrededor de las
	// inserciones. Si abarcara todo el método, estaría abierta mientras se reciben y se miden gigabytes de
	// video, reteniendo una conexión de la base durante minutos.
	public Map<String, Object> subir(Integer idExperimento, String etiquetaGrupo, MultipartFile archivo,
			String sesion, String tipo, String tratamiento, Integer nCilindros, Integer ordinal,
			Authentication authentication) {
		// 1. Todo lo que se puede rechazar sin tocar el disco.
		Experimento experimento = experimentoRepository.findById(idExperimento).orElseThrow(
				() -> new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + idExperimento + " no existe"));
		// D-107: solo quien creó el experimento sube sus videos, sea Investigador o Administrador. VIDEO no guarda
		// quién lo subió y el backend reporta al creador del experimento como "subidoPor"; si subiera otra cuenta,
		// ese dato sería falso.
		//
		// Fíjate en que aquí ni siquiera un administrador puede, al revés que en la pausa o la revisión:
		// no es una cuestión de permisos sino de que el dato quedaría mal. Es una regla distinta y por eso
		// la comprobación es distinta.
		if (!experimento.getIdInstitucional().equals(authentication.getName())) {
			throw new ErrorApi(HttpStatus.FORBIDDEN, "Solo quien creó el experimento sube sus videos.");
		}
		String etiqueta = etiquetaGrupo == null ? "" : etiquetaGrupo.trim();
		if (etiqueta.isEmpty()) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "La etiqueta del grupo no puede estar vacía.");
		}
		// La columna grupo.etiqueta es varchar(100): se cuentan caracteres, no bytes.
		//
		// codePointCount y no length(): length() cuenta unidades de 16 bits, y un emoji o ciertos
		// caracteres ocupan dos. Contando puntos de código, el límite coincide con el que cuenta PostgreSQL.
		if (etiqueta.codePointCount(0, etiqueta.length()) > LARGO_ETIQUETA) {
			throw unprocessable("La etiqueta del grupo no puede tener más de " + LARGO_ETIQUETA + " caracteres.");
		}
		if (archivo == null || archivo.isEmpty()) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "Falta el archivo del video.");
		}
		// es(...) compara el texto crudo con el del enum sin tronar si viene null (ver ValorDeBase).
		if (!SesionVideo.DIA_1.es(sesion) && !SesionVideo.DIA_2.es(sesion)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "La sesión debe ser 'Dia 1' o 'Dia 2'.");
		}
		String extension = extensionDe(archivo.getOriginalFilename());
		if (extension == null) {
			throw unprocessable("Solo se aceptan videos .mp4 o .mov.");
		}
		tipo = limpiar(tipo);
		tratamiento = limpiar(tratamiento);
		// Grupo y tanda: se revisan ya (sin crear nada) para no escribir un video de 1 GB que se va a rechazar.
		//
		// Esta es la llamada "en seco": el false final significa comprobar sin crear. Más abajo se vuelve
		// a llamar con true, ya dentro de la transacción. Se hace dos veces a propósito: la primera para
		// fallar pronto, la segunda porque entre una y otra pudo cambiar algo.
		resolverDestino(idExperimento, etiqueta, sesion, tipo, tratamiento, nCilindros, ordinal, false);

		// 2. El archivo, a un nombre temporal dentro de videos/ (el mismo volumen que el nombre final).
		//
		// El UUID garantiza que dos subidas simultáneas no se pisen el temporal. Y el temporal va en
		// videos/ y no en tmp/ por lo de siempre: el movimiento final solo es instantáneo y atómico si
		// origen y destino están en el mismo sistema de archivos.
		Path temporal = raiz.resolve("videos").resolve("subiendo-" + UUID.randomUUID() + "." + extension);
		// Un arreglo de un elemento como caja mutable: una lambda no puede asignar a una variable local,
		// pero sí modificar el contenido de un arreglo. Es el truco habitual para sacar un valor de dentro
		// de una lambda, y aquí sirve para que el catch sepa si el archivo llegó a moverse.
		Path[] movido = {null};
		try {
			// transferTo mueve lo que Spring ya recibió en tmp/ a este nombre temporal.
			archivo.transferTo(temporal);

			// 3. ¿Se puede reproducir y es horizontal? Si no, se borra el temporal (en el catch de abajo).
			Ffprobe.Sonda sonda = ffprobe.sondear(temporal);
			// BigDecimal con dos decimales, para que cuadre con el NUMERIC(8,2) de la columna.
			// HALF_UP es el redondeo de toda la vida (0,5 sube).
			BigDecimal duracion = sonda == null ? null
					: BigDecimal.valueOf(sonda.duracion()).setScale(2, RoundingMode.HALF_UP);
			// Las cuatro condiciones cubren lo mismo: el archivo no es un video utilizable. El || corta
			// por la izquierda, así que si sonda es null no se evalúa duracion y no truena.
			// signum() <= 0 descarta duración cero o negativa.
			if (sonda == null || sonda.ancho() <= 0 || sonda.alto() <= 0 || duracion.signum() <= 0) {
				throw unprocessable(DANADO);
			}
			// Recuerda que Ffprobe ya enderezó el giro de los celulares, así que esta comparación habla de
			// cómo se VE el video, no de cómo está guardado.
			if (sonda.ancho() <= sonda.alto()) {
				throw unprocessable("El video es vertical: se necesita un video horizontal, con los cilindros de lado a lado.");
			}

			// 4. Las filas, en una sola transacción. El archivo se mueve a su nombre final DENTRO de ella:
			// si algo falla, no se confirma nada y el catch borra el archivo, esté donde esté.
			// Una lambda solo puede usar variables que no cambian; tipo y tratamiento se reasignaron
			// arriba, así que se copian a dos finales.
			String tipoFinal = tipo;
			String tratamientoFinal = tratamiento;
			// Solo para mostrarlo en Resultados y en los reportes; el archivo se guarda como videos/{id}.{ext}.
			String nombreOriginal = nombreParaMostrar(archivo.getOriginalFilename());
			// TransactionTemplate abre la transacción justo aquí y la cierra al terminar la lambda.
			return new TransactionTemplate(transactionManager).execute(estado -> {
				// Ahora sí, con crear = true: esta llamada SÍ inserta el grupo, la tanda y los especímenes
				// que falten. Al estar dentro de la transacción, todo se deshace junto si algo falla.
				Integer idTanda = resolverDestino(idExperimento, etiqueta, sesion, tipoFinal, tratamientoFinal,
						nCilindros, ordinal, true);
				// RETURNING devuelve el id que acaba de asignar PostgreSQL, sin una segunda consulta.
				Integer idVideo = jdbc.queryForObject(
						"INSERT INTO video (idtanda, sesion, duracion, nombreoriginal) VALUES (?, ?, ?, ?) RETURNING idvideo",
						Integer.class, idTanda, sesion, duracion, nombreOriginal);
				// La ruta es relativa a la raíz del volumen, como la guarda y la lee todo el sistema.
				// El nombre se arma con el ID, nunca con el nombre que mandó el usuario: por eso un
				// nombre con "../" o caracteres raros no puede hacer nada.
				String relativa = "videos/" + idVideo + "." + extension;
				jdbc.update("UPDATE video SET archivo = ? WHERE idvideo = ?", relativa, idVideo);
				Path destino = raiz.resolve(relativa);
				try {
					Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException e) {
					// Una lambda no puede lanzar excepciones comprobadas, así que se envuelve en una no
					// comprobada. El catch de abajo la vuelve a desenvolver.
					throw new UncheckedIOException(e);
				}
				// Se apunta que el archivo YA está en su sitio final, por si hay que borrarlo después.
				movido[0] = destino;

				Map<String, Object> datos = new LinkedHashMap<>();
				datos.put("idVideo", idVideo);
				if (SesionVideo.DIA_2.es(sesion)) {
					// Solo el Día 2 se analiza (D-71). Los valores de arranque salen de los DEFAULT de la tabla.
					//
					// El INSERT lleva SOLO idvideo: estado, etapa, nivelclasif y fechaanalisis los pone la
					// base con sus DEFAULT. Escribirlos aquí duplicaría esos valores en dos sitios y
					// podrían descuadrarse.
					// Esta fila es la que encola el trabajo para el worker (ver la entidad Analisis).
					Map<String, Object> analisis = jdbc.queryForMap(
							"INSERT INTO analisis (idvideo) VALUES (?) RETURNING idanalisis, estado", idVideo);
					datos.put("idAnalisis", analisis.get("idanalisis"));
					datos.put("estadoAnalisis", analisis.get("estado"));
				} else {
					// Las claves se ponen igualmente, en null: así el cliente no tiene que comprobar si
					// existen, solo si valen null.
					datos.put("idAnalisis", null);
					datos.put("estadoAnalisis", null);
				}
				return datos;
			});
		} catch (DataIntegrityViolationException e) {
			// Las llaves únicas (grupo, tanda, video por sesión) cubren la carrera entre dos subidas.
			//
			// Es la última defensa: entre la comprobación "en seco" y el INSERT puede colarse otra
			// subida. Las restricciones de la base siempre ganan, y aquí se traducen a un 409 legible.
			borrar(temporal, movido[0]);
			throw new ErrorApi(HttpStatus.CONFLICT,
					"Otra subida se adelantó con el mismo grupo, tanda o video. Revisa e intenta de nuevo.");
		} catch (IOException | UncheckedIOException e) {
			borrar(temporal, movido[0]);
			log.error("No se pudo guardar el video del experimento {}", idExperimento, e);
			throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el video en el servidor.");
		} catch (RuntimeException e) {
			// El catch general: cubre los ErrorApi de validación que se lanzan DESPUÉS de escribir el
			// temporal (el video dañado, el vertical). Se limpia y se relanza tal cual, para que el
			// usuario reciba el mensaje concreto y no uno genérico.
			borrar(temporal, movido[0]);
			throw e;
		}
	}

	// Encuentra el grupo y la tanda donde va el video y revisa que se pueda: tipo y tratamiento si el grupo es
	// nuevo, entre 2 y 4 cilindros si la tanda es nueva y que la tanda no tenga ya el video de esa sesión.
	// Con crear = false solo revisa; con crear = true además crea lo que falta. Devuelve el idTanda (null si
	// no se creó). Si el grupo ya existe, tipo y tratamiento se ignoran; si la tanda ya existe, nCilindros también.
	// Cardinalidades de la guía (§5), que se cumplen aquí y no con CHECK: una tanda tiene de 2 a 4 especímenes (se rechaza
	// nCilindros fuera de ese rango y se crean exactamente nCilindros); un grupo tiene 1 o más tandas (un grupo solo nace
	// aquí, en la misma transacción que su primera tanda: si algo falla después, el grupo también se deshace; y al
	// eliminar su última tanda el grupo se borra, ver ExperimentoServiceImpl.eliminarTanda); y de las dos sale el mínimo de
	// 2 especímenes por grupo.
	//
	// El doble papel (revisar / revisar y crear) con un booleano es lo que permite usar EL MISMO código en
	// los dos momentos. Si fueran dos métodos, podrían separarse y la comprobación en seco dejaría pasar
	// algo que luego fallaría a media transacción.
	// El último párrafo explica algo sutil: hay reglas del modelo (de 2 a 4 especímenes, un grupo nunca
	// sin tandas) que no son CHECK de la base sino invariantes que sostiene este código junto con
	// eliminarTanda. Es una decisión consciente, no un olvido.
	private Integer resolverDestino(Integer idExperimento, String etiqueta, String sesion, String tipo,
			String tratamiento, Integer nCilindros, Integer ordinalPedido, boolean crear) {
		// ¿Existe ya un grupo con esa etiqueta en este experimento?
		Integer idGrupo = primero(jdbc.queryForList(
				"SELECT idgrupo FROM grupo WHERE idexperimento = ? AND etiqueta = ?", Integer.class,
				idExperimento, etiqueta));
		if (idGrupo == null) {
			// Grupo nuevo: ahora sí hacen falta tipo y tratamiento.
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
			// Con crear = false, idGrupo se queda en null y abajo se trata como grupo inexistente.
		}

		// La tanda confirmada por el usuario; si no viene, la siguiente del grupo.
		int ordinal;
		if (ordinalPedido != null) {
			ordinal = ordinalPedido;
		} else if (idGrupo == null) {
			// Grupo nuevo (o comprobación en seco): su primera tanda es la 1.
			ordinal = 1;
		} else {
			// El mismo cálculo que hace la consulta de GrupoRepository para el autocompletado.
			ordinal = jdbc.queryForObject("SELECT COALESCE(MAX(ordinal), 0) + 1 FROM tanda WHERE idgrupo = ?",
					Integer.class, idGrupo);
		}
		// La letra de la tanda va de A a Z: 1 a 26. Vale para la que manda el usuario y para la siguiente automática.
		// El tope no es caprichoso: las tandas se muestran como letras, y pasada la Z no habría cómo.
		if (ordinal < 1 || ordinal > MAX_TANDAS) {
			throw unprocessable("La tanda debe ir de A a Z.");
		}

		Integer idTanda = idGrupo == null ? null : primero(jdbc.queryForList(
				"SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, idGrupo, ordinal));
		if (idTanda == null) {
			// Tanda nueva: ahora sí hace falta saber cuántos cilindros se grabaron.
			if (nCilindros == null || nCilindros < 2 || nCilindros > 4) {
				throw unprocessable("Una tanda nueva necesita entre 2 y 4 cilindros.");
			}
			if (crear) {
				idTanda = jdbc.queryForObject(
						"INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, ?) RETURNING idtanda",
						Integer.class, idGrupo, ordinal, nCilindros);
				// Los especímenes se crean aquí, uno por cilindro: así la tanda nunca queda sin ellos.
				// Recuerda que un espécimen no es más que su posición en el cuadro (ver la entidad Especimen).
				for (int cilindro = 1; cilindro <= nCilindros; cilindro++) {
					jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, ?)", idTanda, cilindro);
				}
			}
		} else if (!jdbc.queryForList("SELECT 1 FROM video WHERE idtanda = ? AND sesion = ?", Integer.class,
				idTanda, sesion).isEmpty()) {
			// La tanda ya existe Y ya tiene el video de esa sesión: una tanda lleva como mucho un video
			// por día (ver la entidad Video).
			// El mensaje se escribe con la letra y el nombre del día, no con los ids, porque es lo que ve
			// el usuario en la pantalla.
			String letra = String.valueOf((char) ('A' + ordinal - 1));
			String dia = SesionVideo.DIA_1.es(sesion) ? "Día 1" : "Día 2";
			throw new ErrorApi(HttpStatus.CONFLICT, "La Tanda " + letra + " ya tiene su video de " + dia
					+ ". Cada tanda lleva un solo video por sesión.");
		}
		return idTanda;
	}

	// "mp4" o "mov" según la extensión del nombre original (sin distinguir mayúsculas: .MOV vale), o null
	// si es otra cosa (R-09).
	//
	// Locale.ROOT en toLowerCase no es un adorno: en turco, la "I" mayúscula NO se convierte en "i", y un
	// archivo .MP4 subido desde un sistema con esa configuración se rechazaría. Fijar el locale evita que
	// el comportamiento dependa de la máquina.
	// Ojo con lo que esto NO hace: comprobar la extensión no garantiza que el archivo sea un video. De eso
	// se encarga ffprobe después; esto solo descarta pronto lo evidente.
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
	//
	// Es un método de SANEADO: todo lo que llega del usuario pasa por aquí antes de guardarse. La última
	// frase es la garantía de fondo: aunque este saneado dejara pasar algo, el nombre no se usa para
	// ninguna ruta, así que no hay nada que explotar.
	// Quitar la ruta no es solo cosmética: guardar "C:\Users\ana\videos\acu1.mp4" revelaría la estructura
	// de carpetas y el nombre de usuario de quien lo subió.
	static String nombreParaMostrar(String original) {
		if (original == null) {
			return null;
		}
		// El mayor de los dos índices, +1, deja solo lo que sigue a la última barra. Si no hay ninguna,
		// las dos dan -1, el máximo es -1, +1 es 0, y se queda el texto entero. Funciona sin casos aparte.
		String nombre = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
		// Se quitan los caracteres de control (saltos de línea, tabuladores, bytes invisibles): colarlos
		// en una cabecera HTTP o en un PDF podría romper el formato.
		StringBuilder sinControl = new StringBuilder();
		// codePoints() recorre por puntos de código, así que un emoji no se parte en dos.
		nombre.codePoints().filter(c -> !Character.isISOControl(c)).forEach(sinControl::appendCodePoint);
		String texto = sinControl.toString().trim();
		// El recorte a 255 (el largo de la columna) también va por puntos de código, y offsetByCodePoints
		// da el índice real donde cortar sin partir un carácter por la mitad.
		if (texto.codePointCount(0, texto.length()) > 255) {
			texto = texto.substring(0, texto.offsetByCodePoints(0, 255));
		}
		// Si después de limpiar no queda nada, es null y no "": la columna admite nulos y así se
		// distingue "no se sabe" de "se llama vacío".
		return texto.isEmpty() ? null : texto;
	}

	// Texto sin espacios de los lados; vacío o ausente es null.
	// Normaliza los dos casos ("no vino" y "vino vacío") a uno solo, para que quien lo use solo compruebe null.
	private static String limpiar(String texto) {
		if (texto == null) {
			return null;
		}
		String limpio = texto.trim();
		return limpio.isEmpty() ? null : limpio;
	}

	// El primer elemento de una lista, o null si está vacía. Evita repetir el isEmpty en cada consulta.
	private static Integer primero(List<Integer> ids) {
		return ids.isEmpty() ? null : ids.get(0);
	}

	// 422: la petición está bien formada pero el contenido no se puede aceptar.
	//
	// Es la distinción con el 400: 400 es "no entiendo lo que me mandas" (falta el archivo, la sesión no
	// es válida) y 422 es "te entiendo perfectamente, pero eso no se puede hacer" (el video está de pie).
	// HttpStatus.valueOf(422) porque el enumerado de Spring no trae constante para este código.
	private static ErrorApi unprocessable(String mensaje) {
		return new ErrorApi(HttpStatus.valueOf(422), mensaje);
	}

	// Borra el temporal y el archivo ya movido (el que no exista se ignora). Es la limpieza de "si algo falla
	// no queda basura".
	//
	// Recibe los dos porque el fallo puede ocurrir antes o después del movimiento, y no siempre se sabe
	// cuál de los dos existe. Path... acepta cualquier número de rutas.
	// Un fallo al borrar solo se registra: la transacción ya se deshizo, así que el archivo queda
	// huérfano ocupando disco, pero ninguna fila lo referencia y nada se rompe.
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
