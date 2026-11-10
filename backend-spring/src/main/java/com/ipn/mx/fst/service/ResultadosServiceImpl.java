// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Los enums de dominio que se usan para comparar estados, etapas y conductas.
import com.ipn.mx.fst.domain.valores.EstadoAnalisis;
import com.ipn.mx.fst.domain.valores.EtapaAnalisis;
import com.ipn.mx.fst.domain.valores.TipoConducta;
import com.ipn.mx.fst.repository.ExperimentoRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
// Duration, OffsetDateTime y ZoneId: el plazo de la pausa y las fechas en la zona de México.
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
// HashMap: mapa sin orden, cuando el orden no importa. LinkedHashMap: conserva el de inserción.
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
// TreeMap: mapa ORDENADO por su clave. Aquí ordena los minutos.
import java.util.TreeMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Jackson 3 (paquete tools.jackson), para leer los JSON que escribe el worker.
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Progreso y resultados de un experimento. Todo se calcula al pedirlo, desde SEGUNDO y ANALISIS: los totales
// no se guardan (D-52). Las consultas son agregadas (GROUP BY en la base) y se arman con JdbcTemplate, como
// en VideoServiceImpl, sin una consulta por espécimen.
//
// El contrato completo de los métodos está en ResultadosService.
// Es el archivo donde vive el cálculo científico del sistema, y su decisión de fondo es D-52: los totales
// NO se guardan. Se recalculan desde la tabla segundo cada vez que alguien los pide.
// Por qué vale la pena pagar ese precio: si los totales estuvieran guardados, cada corrección de la
// revisión obligaría a recalcularlos y a mantenerlos sincronizados, y cualquier olvido dejaría números
// que no cuadran con los datos. Calculándolos siempre, el resultado no puede quedar desfasado.
// Lo que hace viable esa decisión es que las sumas las hace PostgreSQL con GROUP BY: a Java solo llega el
// resumen, no las decenas de miles de filas de segundo.
@Service
public class ResultadosServiceImpl implements ResultadosService {

	private static final Logger log = LoggerFactory.getLogger(ResultadosServiceImpl.class);

	// La zona del laboratorio, fijada para que la hora no dependa de dónde corra el contenedor.
	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
	private static final JsonMapper JSON = JsonMapper.builder().build();

	// Las cuatro conductas, con su nombre de la base y en este orden en todas las respuestas.
	private static final List<String> CONDUCTAS = Arrays.stream(TipoConducta.values()).map(TipoConducta::valor).toList();

	// Las cuatro conductas más «sin ver», que son los segundos con conducta NULL (no se vio al animal o la revisión los marcó como
	// no visibles). Se agregan al final de totales y minutos para que las cinco sumen el 100 % y los 60 s.
	//
	// Esa suma es lo que permite confiar en el dato: si las cuatro conductas sumaran 47 de 60 segundos sin
	// explicar los otros 13, nadie sabría si falta medir o si no se veía al espécimen.
	private static final List<String> CONDUCTAS_Y_SIN_VER = Stream.concat(CONDUCTAS.stream(), Stream.of(TablaDeReporte.SIN_VER)).toList();

	// Raíz del volumen videos_reportes: de ahí salen pausa.json y los PDF de diagnóstico.
	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	// MODO_DESARROLLADOR: los tiempos de cada análisis (tiempos.json) son solo para quien desarrolla. Con false no
	// salen en /status, ni en /results, ni en el PDF, aunque exista un tiempos.json de antes. El avance
	// (progreso.json) no depende de esto: la barra de 0 a 100 % es para todos.
	//
	// La distinción es clara: el AVANCE lo necesita cualquiera que espere un resultado; los TIEMPOS (cuánto
	// tardó cada etapa, en qué equipo, con qué modelo) solo sirven para comparar modelos y máquinas.
	@Value("${app.modo-desarrollador:false}")
	private boolean modoDesarrollador;

	// Horas que espera un análisis en pausa antes de cancelarse (la misma variable que usa el worker).
	// Si los dos tuvieran plazos distintos, la pantalla enseñaría un tiempo que no coincide con la realidad.
	@Value("${PAUSE_TIMEOUT_HOURS:72}")
	private int horasDePausa;

	@Autowired
	private ExperimentoRepository experimentoRepository;

	@Autowired
	private JdbcTemplate jdbc;

	private Path raiz;

	@PostConstruct
	void fijarRaiz() {
		raiz = Path.of(raizConfigurada).toAbsolutePath().normalize();
	}

	// Una fila de la consulta de resultados: las veces que un espécimen tuvo una conducta en un minuto.
	// Es la fila CRUDA que devuelve el GROUP BY, antes de agruparla por espécimen.
	private record FilaConteo(int idGrupo, String etiqueta, int ordinal, int cilindro, String nivel, int idAnalisis,
			String nombreVideo, int minuto, String conducta, long segundos, boolean revisado) {
	}

	// Un espécimen con sus conteos ya sumados. filas incluye los segundos sin conducta (NULL), que van bajo la clave
	// «sin ver» en total y porMinuto: cuentan en el denominador del porcentaje y son la quinta columna.
	//
	// Es una clase normal y no un record porque se va LLENANDO mientras se recorren las filas: sus campos
	// cambian, y los de un record no pueden. Es el acumulador del agrupado.
	// "Cuentan en el denominador" importa: el porcentaje se calcula sobre TODOS los segundos del
	// espécimen, no solo sobre los que tienen conducta. Así un 30 % de inmovilidad significa 30 % del
	// tiempo medido, y no 30 % de lo que se alcanzó a ver.
	private static class Espec {
		int idGrupo;
		String etiqueta;
		int ordinal;
		int cilindro;
		String nivel;
		int idAnalisis;
		String nombreVideo;      // el del video de la tanda; null en los videos anteriores al 20-oct-2026
		boolean revisado;
		long filas;
		// HashMap: el orden de este mapa no importa, porque al escribirlo se recorre CONDUCTAS_Y_SIN_VER,
		// que sí tiene el orden bueno.
		final Map<String, Long> total = new HashMap<>();
		// TreeMap: ordenado por la clave (el minuto), así los minutos salen 1, 2, 3 sin ordenarlos aparte.
		final TreeMap<Integer, Map<String, Long>> porMinuto = new TreeMap<>();
	}

	// Una tanda de la consulta de grupos, con el estado del análisis de su video del Día 2 (null si no tiene
	// Día 2).
	private record FilaTanda(int idGrupo, int ordinal, int cilindros, String estado) {
	}

	// ---------------------------------------------------------------------------
	//  /status
	// ---------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> estado(Integer idExperimento) {
		existe(idExperimento);

		List<Map<String, Object>> analisis = jdbc.query("""
				SELECT a.idanalisis, g.etiqueta, t.ordinal, v.sesion, a.estado, a.etapa, a.fechaanalisis, a.rutadiagnostico,
				       a.idvideo
				FROM analisis a
				JOIN video v ON v.idvideo = a.idvideo
				JOIN tanda t ON t.idtanda = v.idtanda
				JOIN grupo g ON g.idgrupo = t.idgrupo
				WHERE g.idexperimento = ?
				ORDER BY a.fechaanalisis, a.idanalisis
				""", (rs, fila) -> {
			int idAnalisis = rs.getInt("idanalisis");
			String estado = rs.getString("estado");
			String etapa = rs.getString("etapa");

			Map<String, Object> item = new LinkedHashMap<>();
			item.put("idAnalisis", idAnalisis);
			// "Control · A": el mismo texto que arma RevisionServiceImpl, para que coincidan.
			item.put("tanda", rs.getString("etiqueta") + " · " + letra(rs.getInt("ordinal")));
			item.put("sesion", rs.getString("sesion"));
			item.put("estado", estado);
			item.put("etapa", etapa);
			Avance avance = avance(idAnalisis, estado, etapa);
			item.put("porcentaje", avance.general());
			item.put("porcentajeEtapa", avance.etapa());
			// La pausa solo se lee si el análisis está pausado: el archivo no existe en los demás casos.
			item.put("pausa", EstadoAnalisis.EN_PAUSA.es(estado) ? pausa(idAnalisis) : null);
			item.put("diagnosticoDisponible", diagnosticoDisponible(estado, rs.getString("rutadiagnostico")));
			// La imagen de control (los tubos que encontró el pipeline) que la pantalla de error muestra.
			item.put("controlDisponible", Files.isRegularFile(rutaControl(idAnalisis, rs.getInt("idvideo"))));
			// Mientras el análisis no termina es la hora en que se encoló (D-79); el worker la reemplaza al
			// terminar. En la zona de México, con su desfase, para que se lea como hora local.
			// Recuerda que esta columna tiene dos significados según el estado (ver la entidad Analisis).
			item.put("fechaAnalisis", rs.getObject("fechaanalisis", OffsetDateTime.class)
					.atZoneSameInstant(ZONA).toOffsetDateTime());
			// Todavía no se guarda en ningún lado por qué se canceló un análisis.
			// La clave se manda igualmente, en null: así el cliente ya la lee y el día que exista el dato
			// no hay que cambiar el contrato.
			item.put("motivoCancelacion", null);
			// Un análisis en cola puede traer el archivo de una corrida anterior (se reencoló): no es de esta
			// espera, así que no se muestra. Los demás estados leen tiempos.json, o null si no existe.
			item.put("tiempos", modoDesarrollador && !EstadoAnalisis.EN_COLA.es(estado) ? tiempos(idAnalisis) : null);
			return item;
		}, idExperimento);

		// Se envuelve en un objeto en vez de devolver la lista pelada: así caben más campos después sin
		// romper a quien ya lo lee.
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("analisis", analisis);
		return datos;
	}

	// El avance de un análisis: el general (0 a 100) y el de su etapa (0 a 100, o null si no se conoce).
	private record Avance(int general, Integer etapa) {
	}

	// En cola: 0 y 0. Completado: 100 y 100. En cualquier otro estado (procesando, en pausa, error, cancelado):
	// lo que dejó el worker en analisis/{id}/progreso.json, si existe y su etapa es la misma que la de la base;
	// si no (un análisis de antes de que existiera el archivo, o uno que acaba de empezar), el mapeo viejo por
	// etapa (33, 67, 100) y el avance de la etapa en null, para que nada se rompa.
	//
	// Es un buen ejemplo de compatibilidad hacia atrás: se prefiere el dato fino del archivo nuevo, pero si
	// no está se cae al cálculo viejo en vez de fallar. Los análisis de hace meses siguen mostrando algo.
	// La comprobación de que la etapa coincida evita el caso sutil de un archivo que quedó de la etapa
	// anterior: enseñaría un avance que no es el de ahora.
	private Avance avance(int idAnalisis, String estado, String etapa) {
		if (EstadoAnalisis.EN_COLA.es(estado)) {
			return new Avance(0, 0);
		}
		if (EstadoAnalisis.COMPLETADO.es(estado)) {
			return new Avance(100, 100);
		}
		Avance leido = leerProgreso(idAnalisis, etapa);
		return leido != null ? leido : new Avance(porcentaje(estado, etapa), null);
	}

	// progreso.json: {"etapa", "porcentajeEtapa", "porcentaje", "actualizado"}. Mismo cuidado que con pausa.json: si
	// el archivo está roto, null y un aviso en el log (si no existe es lo normal en un análisis viejo: sin aviso).
	private Avance leerProgreso(int idAnalisis, String etapaDeLaBase) {
		Path archivo = raiz.resolve("analisis").resolve(String.valueOf(idAnalisis)).resolve("progreso.json");
		// Que no exista es normal y no merece aviso: se distingue del archivo roto, que sí lo merece.
		if (!Files.isRegularFile(archivo)) {
			return null;
		}
		try {
			JsonNode progreso = JSON.readTree(Files.readString(archivo));
			// El archivo tiene que hablar de la MISMA etapa que dice la base; si no, está desfasado.
			if (!etapaDeLaBase.equals(progreso.path("etapa").asText())) {
				return null;
			}
			if (!progreso.has("porcentaje") || !progreso.has("porcentajeEtapa")) {
				return null;
			}
			// Math.max(0, Math.min(100, ...)) acota el valor entre 0 y 100: el worker escribe este archivo
			// mientras trabaja y un valor fuera de rango rompería la barra de la pantalla.
			int general = Math.max(0, Math.min(100, progreso.path("porcentaje").asInt()));
			int deLaEtapa = Math.max(0, Math.min(100, progreso.path("porcentajeEtapa").asInt()));
			return new Avance(general, deLaEtapa);
		} catch (IOException | JacksonException e) {
			// El worker puede estar escribiéndolo justo ahora y pillarse a medias: por eso se atrapa y se
			// sigue en vez de fallar.
			log.warn("No se pudo leer {}: {}", archivo, e.getMessage());
			return null;
		}
	}

	// tiempos.json: lo que el worker midió de un análisis (cuándo empezó cada etapa y cuánto tardó, y en qué equipo).
	// Mismo cuidado que con progreso.json: null si no existe (los análisis de antes no lo tienen: sin aviso) y null
	// con un aviso en el log si está roto.
	private JsonNode leerTiempos(int idAnalisis) {
		Path archivo = raiz.resolve("analisis").resolve(String.valueOf(idAnalisis)).resolve("tiempos.json");
		if (!Files.isRegularFile(archivo)) {
			return null;
		}
		try {
			return JSON.readTree(Files.readString(archivo));
		} catch (IOException | JacksonException e) {
			log.warn("No se pudo leer {}: {}", archivo, e.getMessage());
			return null;
		}
	}

	// Lo de tiempos.json que necesita la pantalla de progreso. De la etapa en curso segundos es null y viene su
	// inicio, para que el navegador la cronometre; el total y el fin también son null mientras el análisis corre.
	//
	// "Para que el navegador la cronometre" es la razón de mandar el inicio sin los segundos: el reloj corre
	// en la pantalla, sin pedirle al servidor una actualización por segundo.
	private Map<String, Object> tiempos(int idAnalisis) {
		JsonNode t = leerTiempos(idAnalisis);
		if (t == null) {
			return null;
		}
		List<Map<String, Object>> etapas = new ArrayList<>();
		for (JsonNode e : t.path("etapas")) {
			Map<String, Object> etapa = new LinkedHashMap<>();
			// valor() devuelve null limpio cuando el campo falta, en vez de un texto "null".
			etapa.put("etapa", valor(e.path("etapa")));
			etapa.put("inicio", valor(e.path("inicio")));
			etapa.put("fin", valor(e.path("fin")));
			etapa.put("segundos", valor(e.path("segundos")));
			etapas.add(etapa);
		}
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("inicio", valor(t.path("inicio")));
		datos.put("fin", valor(t.path("fin")));
		datos.put("totalSegundos", valor(t.path("totalSegundos")));
		datos.put("etapas", etapas);
		datos.put("equipo", equipo(t));
		datos.put("modelo", modelo(t));
		return datos;
	}

	// Los datos de la máquina que hizo el análisis, para comparar tiempos entre computadoras.
	// El bucle sobre los nombres evita cinco líneas casi idénticas.
	private Map<String, Object> equipo(JsonNode t) {
		Map<String, Object> equipo = new LinkedHashMap<>();
		for (String campo : List.of("nombre", "cpu", "nucleos", "memoriaGB", "dockerCPU")) {
			equipo.put(campo, valor(t.path("equipo").path(campo)));
		}
		return equipo;
	}

	// Con qué modelo se hizo el análisis: el bloque «modelo» de tiempos.json (el archivo, la versión de la red, su SHA-256, el
	// tamaño y la fecha del archivo y con cuántos videos se entrenó). null si no lo trae: un análisis de antes de que el
	// worker lo anotara.
	//
	// Es el dato que permite explicar por qué dos corridas del mismo video dieron resultados distintos.
	private Map<String, Object> modelo(JsonNode t) {
		JsonNode m = t.path("modelo");
		// isMissingNode (la clave no está) e isNull (está pero vale null) son dos casos distintos; los dos
		// significan lo mismo aquí.
		if (m.isMissingNode() || m.isNull()) {
			return null;
		}
		Map<String, Object> modelo = new LinkedHashMap<>();
		for (String campo : List.of("archivo", "version", "sha256", "tamanoMB", "modificado", "videosDeEntrenamiento", "clips")) {
			modelo.put(campo, valor(m.path(campo)));
		}
		return modelo;
	}

	// Una línea por análisis del reporte, «Tanda A: fst_cnn_seg.pt · red 5.0.0 · …», con el modelo con el que se hizo. Solo
	// en modo desarrollador: sin él, la lista va vacía y el CSV y el XLSX salen como siempre.
	//
	// Devolver una lista vacía (y no null) hace que el bucle de ReporteCsv y ReporteXlsx simplemente no dé
	// vueltas: ninguno de los dos tiene que comprobar el modo.
	private List<String> modelosDelReporte(List<Espec> especimenes) {
		if (!modoDesarrollador) {
			return List.of();
		}
		// computeIfAbsent sobre el idAnalisis evita leer el mismo tiempos.json una vez por espécimen: los
		// cuatro de una tanda comparten análisis, así que se lee una sola vez por tanda.
		Map<Integer, String> porAnalisis = new LinkedHashMap<>();
		for (Espec e : especimenes) {
			porAnalisis.computeIfAbsent(e.idAnalisis, id -> {
				JsonNode t = leerTiempos(id);
				// El texto lo arma ModeloDelAnalisis, el mismo que usa el PDF: así los tres formatos
				// escriben la línea igual.
				return "Tanda " + letra(e.ordinal) + ": " + ModeloDelAnalisis.texto(t == null ? null : modelo(t));
			});
		}
		return new ArrayList<>(porAnalisis.values());
	}

	// El valor de un campo de tiempos.json como número o texto, o null si falta o es null.
	//
	// Sin esto, asText() sobre un campo ausente devolvería el texto "null" (cuatro letras), que llegaría al
	// JSON como un valor de verdad y confundiría al cliente.
	// La distinción número/texto conserva el tipo: un total de segundos sale como número y se puede sumar.
	private static Object valor(JsonNode nodo) {
		if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
			return null;
		}
		return nodo.isNumber() ? nodo.numberValue() : nodo.asText();
	}

	// Lo que /results muestra de cada análisis: cuánto tardó y en qué equipo (el nombre y, si no tiene, el CPU).
	// Es la versión corta de tiempos(): en la tabla de resultados no cabe el desglose por etapas.
	private Map<String, Object> tiempoAnalisis(int idAnalisis) {
		JsonNode t = leerTiempos(idAnalisis);
		if (t == null) {
			return null;
		}
		Object nombre = valor(t.path("equipo").path("nombre"));
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("totalSegundos", valor(t.path("totalSegundos")));
		// El nombre que se puso en EQUIPO_NOMBRE, o el modelo del procesador si nadie lo configuró.
		datos.put("equipo", nombre != null ? nombre : valor(t.path("equipo").path("cpu")));
		return datos;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Map<String, Object>> tiemposDelExperimento(Integer idExperimento) {
		if (!modoDesarrollador) {
			return List.of();      // sin modo desarrollador el PDF no lleva la sección de tiempos
		}
		// Se parte de la misma lista de videos analizados y se le añade el bloque de tiempos a cada fila:
		// así las dos tablas del PDF hablan exactamente de las mismas tandas y en el mismo orden.
		List<Map<String, Object>> filas = videosAnalizados(idExperimento);
		for (Map<String, Object> fila : filas) {
			fila.put("tiempos", tiempos((Integer) fila.get("idAnalisis")));
		}
		return filas;
	}

	// Una fila por tanda con análisis completado: grupo, tanda (letra), nombre del video como lo subió el usuario
	// (null en los videos anteriores al 20-oct-2026), duración del video e idAnalisis. Es para el PDF de resultados.
	//
	// Esta tabla SÍ sale siempre, aunque no haya modo desarrollador: dice de qué archivos salió la
	// medición, y eso es parte del resultado.
	@Override
	@Transactional(readOnly = true)
	public List<Map<String, Object>> videosAnalizados(Integer idExperimento) {
		existe(idExperimento);
		return jdbc.query("""
				SELECT a.idanalisis, g.etiqueta, t.ordinal, v.duracion, v.nombreoriginal
				FROM analisis a
				JOIN video v ON v.idvideo = a.idvideo
				JOIN tanda t ON t.idtanda = v.idtanda
				JOIN grupo g ON g.idgrupo = t.idgrupo
				WHERE g.idexperimento = ? AND a.estado = 'completado'
				ORDER BY g.idgrupo, t.ordinal
				""", (rs, fila) -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("grupo", rs.getString("etiqueta"));
			item.put("tanda", letra(rs.getInt("ordinal")));
			item.put("duracionVideo", rs.getDouble("duracion"));
			item.put("nombreVideo", rs.getString("nombreoriginal"));
			item.put("idAnalisis", rs.getInt("idanalisis"));
			return item;
		}, idExperimento);
	}

	// Avance en porcentaje por etapa (el de antes de progreso.json, y el respaldo): en cola 0, completado 100 y,
	// en cualquier otro estado, el de su etapa.
	//
	// Es el cálculo basto: supone que las tres etapas duran lo mismo, lo que no es cierto, pero da una
	// barra que avanza. progreso.json, cuando existe, lo sustituye por el avance real.
	private static int porcentaje(String estado, String etapa) {
		if (EstadoAnalisis.EN_COLA.es(estado)) {
			return 0;
		}
		if (EstadoAnalisis.COMPLETADO.es(estado)) {
			return 100;
		}
		// Un tercio por etapa, en el orden en que las recorre el worker (ver EtapaAnalisis).
		if (EtapaAnalisis.PREPROCESAMIENTO.es(etapa)) {
			return 33;
		}
		if (EtapaAnalisis.CILINDROS.es(etapa)) {
			return 67;
		}
		// El 0 final cubre una etapa desconocida: mejor no mostrar avance que mostrar uno inventado.
		return EtapaAnalisis.CLASIFICACION.es(etapa) ? 100 : 0;
	}

	// Lo que dice analisis/{id}/pausa.json (lo escribe el worker; hoy ningún análisis pausa, pero el plazo de 72 h se revisa igual): en qué cilindro
	// y desde qué segundo, y cuántos segundos quedan antes de que se cancele. Si el archivo no existe o está
	// roto, null y un aviso en el log.
	//
	// Es la versión corta de PausaServiceImpl.pausa: para la lista de la pantalla de progreso basta con
	// saber que está pausado y cuánto queda; los recuadros solo hacen falta al atenderla.
	private Map<String, Object> pausa(int idAnalisis) {
		Path archivo = raiz.resolve("analisis").resolve(String.valueOf(idAnalisis)).resolve("pausa.json");
		try {
			JsonNode pausa = JSON.readTree(Files.readString(archivo));
			OffsetDateTime horaPausa = OffsetDateTime.parse(pausa.path("hora_pausa").asText());
			long restantes = Duration.between(OffsetDateTime.now(), horaPausa.plusHours(horasDePausa)).getSeconds();

			Map<String, Object> datos = new LinkedHashMap<>();
			datos.put("cilindro", pausa.path("cilindro").asInt());
			datos.put("desdeSegundo", pausa.path("desde_segundo").asInt());
			// Math.max(0, ...) evita enseñar un tiempo negativo si la pausa ya venció y el worker todavía
			// no la ha cancelado.
			datos.put("segundosRestantes", Math.max(0, restantes));
			return datos;
		} catch (IOException | JacksonException | DateTimeParseException e) {
			// Aquí SÍ se avisa aunque el archivo no exista: si la base dice "en pausa", el archivo debería
			// estar, y que falte es una incoherencia que alguien debería mirar.
			log.warn("El análisis {} está en pausa pero no se pudo leer {}: {}", idAnalisis, archivo, e.getMessage());
			return null;
		}
	}

	// ¿Hay PDF de diagnóstico que ofrecer? Las tres condiciones: el análisis está en error, la columna trae
	// ruta, y el archivo existe de verdad (ver la entidad Analisis).
	private boolean diagnosticoDisponible(String estado, String rutaDiagnostico) {
		if (!EstadoAnalisis.ERROR.es(estado) || rutaDiagnostico == null) {
			return false;
		}
		Path pdf = rutaSegura(rutaDiagnostico);
		return pdf != null && Files.isRegularFile(pdf);
	}

	// Une la raíz con una ruta que viene de la base y se asegura de que siga DENTRO de la raíz: la ruta se
	// guarda relativa, pero no se sigue a ciegas (un "../" la sacaría del volumen). Null si se sale.
	//
	// Es la misma defensa que en ReporteAlmacen y UsoDeDisco: normalize() resuelve los "..", y si después
	// la ruta ya no empieza por la raíz, es que apuntaba fuera.
	private Path rutaSegura(String relativa) {
		Path ruta = raiz.resolve(relativa).normalize();
		return ruta.startsWith(raiz) ? ruta : null;
	}

	// ---------------------------------------------------------------------------
	//  /results, /results/byminute, /results/groups
	// ---------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> resultados(Integer idExperimento) {
		existe(idExperimento);
		// false: sin el desglose por minuto.
		return respuestaDeEspecimenes(leerEspecimenes(idExperimento), false);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> resultadosPorMinuto(Integer idExperimento) {
		existe(idExperimento);
		// La ÚNICA diferencia con el anterior es este true.
		return respuestaDeEspecimenes(leerEspecimenes(idExperimento), true);
	}

	// /results y /results/byminute comparten la consulta y el armado; el segundo solo agrega los minutos.
	// Un booleano en vez de dos métodos casi iguales: así los campos comunes no pueden divergir.
	private Map<String, Object> respuestaDeEspecimenes(List<Espec> especimenes, boolean conMinutos) {
		List<Map<String, Object>> lista = new ArrayList<>();
		Map<Integer, Map<String, Object>> tiempoDe = new HashMap<>();    // un archivo por análisis, no por espécimen
		for (Espec e : especimenes) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("idGrupo", e.idGrupo);
			item.put("tanda", e.etiqueta + " · " + letra(e.ordinal));
			item.put("ordinal", e.ordinal);
			item.put("numeroCilindro", e.cilindro);
			item.put("nivelClasif", e.nivel);
			item.put("revisado", e.revisado);
			item.put("nombreVideo", e.nombreVideo);     // el del video de su tanda; null en los videos anteriores
			item.put("totales", totales(e));
			// La caché de arriba evita abrir el mismo tiempos.json cuatro veces (una por espécimen de la tanda).
			item.put("tiempoAnalisis", modoDesarrollador ? tiempoDe.computeIfAbsent(e.idAnalisis, this::tiempoAnalisis) : null);
			if (conMinutos) {
				item.put("minutos", minutos(e));
			}
			lista.add(item);
		}
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("especimenes", lista);
		return datos;
	}

	// Las CUATRO conductas y «sin ver» siempre: segundos y porcentaje (con un decimal) sobre todos los segundos del
	// espécimen; las cinco suman el 100 %.
	//
	// "Siempre" quiere decir que una conducta que no ocurrió sale con 0, no se omite: así la tabla tiene
	// las mismas columnas para todos y el cliente no tiene que rellenar huecos.
	private Map<String, Object> totales(Espec e) {
		Map<String, Object> totales = new LinkedHashMap<>();
		for (String conducta : CONDUCTAS_Y_SIN_VER) {
			long segundos = e.total.getOrDefault(conducta, 0L);
			Map<String, Object> par = new LinkedHashMap<>();
			par.put("segundos", segundos);
			// El denominador es e.filas, TODOS los segundos del espécimen, incluidos los «sin ver».
			// La comprobación de 0 evita dividir entre cero en un espécimen sin datos.
			par.put("porcentaje", e.filas == 0 ? 0.0 : redondear(100.0 * segundos / e.filas));
			totales.put(conducta, par);
		}
		return totales;
	}

	// minuto = ceil(segundo / 60), del 1 al último; las cuatro conductas y «sin ver» siempre, con 0 si no hay.
	//
	// ceil y no división entera: con 310 segundos el último minuto es el 6 (aunque solo tenga 10 s), no el
	// 5. Recortarlo perdería esos segundos.
	private List<Map<String, Object>> minutos(Espec e) {
		int ultimo = (int) Math.ceil(e.filas / 60.0);
		List<Map<String, Object>> minutos = new ArrayList<>();
		for (int minuto = 1; minuto <= ultimo; minuto++) {
			// Map.of() es un mapa vacío inmutable: evita comprobar null en el getOrDefault de abajo.
			Map<String, Long> delMinuto = e.porMinuto.getOrDefault(minuto, Map.of());
			Map<String, Object> fila = new LinkedHashMap<>();
			fila.put("minuto", minuto);
			for (String conducta : CONDUCTAS_Y_SIN_VER) {
				fila.put(conducta, delMinuto.getOrDefault(conducta, 0L));
			}
			minutos.add(fila);
		}
		return minutos;
	}

	// UNA consulta agregada para todos los especímenes con análisis completado: cuántos segundos tuvo cada
	// uno de cada conducta en cada minuto (el minuto sale del número de segundo: 1-60 es el 1, 61-120 el 2...).
	// Con GROUP BY también sale el grupo de los segundos sin conducta (NULL), que cuenta en el denominador.
	// "revisado" es verdadero si algún segundo tiene un origen distinto de 'maquina'. Ordenada por grupo,
	// tanda y cilindro.
	//
	// Es el corazón del archivo: de aquí salen /results, /results/byminute, /results/groups, las gráficas y
	// los reportes CSV y XLSX. Una sola consulta alimenta a todos, y por eso ninguno puede descuadrarse.
	// "UNA consulta" para un experimento con decenas de miles de filas de segundo: el GROUP BY lo resuelve
	// PostgreSQL y a Java llegan unos cientos de filas resumidas.
	private List<Espec> leerEspecimenes(Integer idExperimento) {
		List<FilaConteo> filas = jdbc.query("""
				SELECT g.idgrupo, g.etiqueta, t.ordinal, e.numerocilindro, a.nivelclasif, a.idanalisis, v.nombreoriginal,
				       ((s.segundo - 1) / 60) + 1 AS minuto,
				       s.conducta,
				       COUNT(*) AS segundos,
				       BOOL_OR(s.origen <> 'maquina') AS revisado
				FROM analisis a
				JOIN video v       ON v.idvideo = a.idvideo
				JOIN tanda t       ON t.idtanda = v.idtanda
				JOIN grupo g       ON g.idgrupo = t.idgrupo
				JOIN especimen e   ON e.idtanda = t.idtanda
				JOIN observacion o ON o.idespecimen = e.idespecimen AND o.idvideo = v.idvideo
				JOIN segundo s     ON s.idobservacion = o.idobservacion
				WHERE g.idexperimento = ? AND a.estado = 'completado'
				GROUP BY g.idgrupo, g.etiqueta, t.ordinal, e.numerocilindro, a.nivelclasif, a.idanalisis, v.nombreoriginal,
				         ((s.segundo - 1) / 60) + 1, s.conducta
				ORDER BY g.idgrupo, t.ordinal, e.numerocilindro, minuto
				""", (rs, fila) -> new FilaConteo(rs.getInt("idgrupo"), rs.getString("etiqueta"),
				rs.getInt("ordinal"), rs.getInt("numerocilindro"), rs.getString("nivelclasif"), rs.getInt("idanalisis"),
				rs.getString("nombreoriginal"), rs.getInt("minuto"), rs.getString("conducta"), rs.getLong("segundos"), rs.getBoolean("revisado")),
				idExperimento);
		// Notas de la consulta:
		//   ((s.segundo - 1) / 60) + 1  el minuto, calculado a partir del número de segundo (D-52: los
		//     totales por minuto no se guardan). El -1 y el +1 hacen que el segundo 60 caiga en el minuto
		//     1 y el 61 en el 2; sin el -1, el 60 se iría al 2.
		//   s.conducta en el GROUP BY: los NULL forman su propio grupo, así que los segundos «sin ver» se
		//     cuentan aparte en vez de perderse.
		//   BOOL_OR(s.origen <> 'maquina')  es un OR agregado: da true si AL MENOS UN segundo del grupo
		//     tiene un origen distinto de 'maquina', es decir, si lo tocó una persona. Es más barato que
		//     una segunda consulta solo para saberlo.
		//   a.estado = 'completado'  descarta los análisis a medias, cuyos segundos están incompletos.
		//   El ORDER BY deja las filas en el orden de la respuesta, así que no hay que ordenar después.

		// Las filas vienen por (espécimen, minuto, conducta) y aquí se agrupan por espécimen.
		Map<String, Espec> porEspecimen = new LinkedHashMap<>();
		for (FilaConteo f : filas) {
			// La clave es el trío que identifica a un espécimen, unido con "|": no tiene id propio en esta
			// consulta y los tres juntos son únicos.
			Espec e = porEspecimen.computeIfAbsent(f.idGrupo() + "|" + f.ordinal() + "|" + f.cilindro(), k -> {
				Espec nuevo = new Espec();
				nuevo.idGrupo = f.idGrupo();
				nuevo.etiqueta = f.etiqueta();
				nuevo.ordinal = f.ordinal();
				nuevo.cilindro = f.cilindro();
				nuevo.nivel = f.nivel();
				nuevo.idAnalisis = f.idAnalisis();
				nuevo.nombreVideo = f.nombreVideo();
				return nuevo;
			});
			// filas acumula TODOS los segundos del espécimen: es el denominador de los porcentajes.
			e.filas += f.segundos();
			// |= deja el booleano en true en cuanto una sola fila lo trae.
			e.revisado |= f.revisado();
			// Aquí el null de la base se convierte en la categoría «sin ver»: a partir de este punto ya es
			// una conducta más y los cálculos no tienen que tratarla aparte.
			String clave = f.conducta() == null ? TablaDeReporte.SIN_VER : f.conducta();
			// merge suma al valor que ya hubiera, o lo pone si es el primero. Equivale al típico
			// "si existe súmale, si no asígnalo" de tres líneas.
			e.total.merge(clave, f.segundos(), Long::sum);
			e.porMinuto.computeIfAbsent(f.minuto(), k -> new HashMap<>()).merge(clave, f.segundos(), Long::sum);
		}
		return new ArrayList<>(porEspecimen.values());
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> resultadosPorBloques(Integer idExperimento) {
		existe(idExperimento);

		// Un renglón por segundo (ya con las correcciones guardadas), de cada espécimen con análisis completado; los segundos sin
		// conducta (NULL) también vienen, y son los "no se vio".
		//
		// Esta es la única consulta del archivo que NO agrega: los bloques de cinco necesitan la conducta
		// de cada segundo, uno por uno, porque la regla mira la secuencia (ver BloquesDeCinco).
		Map<String, Map<String, Object>> porEspecimen = new LinkedHashMap<>();
		Map<String, List<String>> conductas = new HashMap<>();
		// Esta forma de jdbc.query recibe un RowCallbackHandler: procesa fila por fila sin construir una
		// lista intermedia. Con decenas de miles de segundos, eso ahorra memoria.
		jdbc.query("""
				SELECT g.idgrupo, g.etiqueta, t.ordinal, e.numerocilindro, s.segundo, s.conducta
				FROM analisis a
				JOIN video v       ON v.idvideo = a.idvideo
				JOIN tanda t       ON t.idtanda = v.idtanda
				JOIN grupo g       ON g.idgrupo = t.idgrupo
				JOIN especimen e   ON e.idtanda = t.idtanda
				JOIN observacion o ON o.idespecimen = e.idespecimen AND o.idvideo = v.idvideo
				JOIN segundo s     ON s.idobservacion = o.idobservacion
				WHERE g.idexperimento = ? AND a.estado = 'completado'
				ORDER BY g.idgrupo, t.ordinal, e.numerocilindro, s.segundo
				""", rs -> {
			int idGrupo = rs.getInt("idgrupo");
			int ordinal = rs.getInt("ordinal");
			int cilindro = rs.getInt("numerocilindro");
			String etiqueta = rs.getString("etiqueta");
			String clave = idGrupo + "|" + ordinal + "|" + cilindro;
			porEspecimen.computeIfAbsent(clave, k -> {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("tanda", etiqueta + " · " + letra(ordinal));
				item.put("ordinal", ordinal);
				item.put("numeroCilindro", cilindro);
				item.put("idGrupo", idGrupo);
				return item;
			});
			List<String> lista = conductas.computeIfAbsent(clave, k -> new ArrayList<>());
			// Un segundo que falte en la tabla (no debería) es un segundo sin ver: se rellena hasta el número que sigue.
			//
			// BloquesDeCinco trabaja por POSICIÓN (índice 0 = segundo 1), así que un hueco correría todos
			// los segundos siguientes y los bloques saldrían desplazados. Rellenar con null los mantiene
			// alineados y, además, es la interpretación honesta: un segundo que no está no se vio.
			int segundo = rs.getInt("segundo");
			while (lista.size() < segundo - 1) {
				lista.add(null);
			}
			lista.add(rs.getString("conducta"));
		}, idExperimento);

		List<Map<String, Object>> lista = new ArrayList<>();
		for (Map.Entry<String, Map<String, Object>> e : porEspecimen.entrySet()) {
			// La regla de los bloques vive en BloquesDeCinco, que es puro y se prueba sin base de datos.
			e.getValue().put("bloques", BloquesDeCinco.armar(conductas.get(e.getKey())));
			lista.add(e.getValue());
		}
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("especimenes", lista);
		return datos;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Map<String, Object>> resultadosPorGrupo(Integer idExperimento) {
		existe(idExperimento);

		// Tres consultas y ninguna dentro de un bucle: los especímenes con sus conteos, las tandas con el
		// estado de su análisis, y los grupos.
		List<Espec> especimenes = leerEspecimenes(idExperimento);
		List<FilaTanda> tandas = tandasDelExperimento(idExperimento);
		List<Map<String, Object>> grupos = gruposDelExperimento(idExperimento);

		for (Map<String, Object> grupo : grupos) {
			int idGrupo = (Integer) grupo.get("idGrupo");
			// Se filtra en memoria sobre la lista ya traída, en vez de consultar por grupo.
			List<Espec> delGrupo = especimenes.stream().filter(e -> e.idGrupo == idGrupo).toList();

			Faltan faltan = faltantesDelGrupo(idGrupo, tandas);
			int nTotal = faltan.nTotal();
			List<Map<String, Object>> faltantes = faltan.faltantes();

			// Las tandas completadas con niveles de clasificación distintos no se promedian juntas.
			// Un análisis "preciso" distingue nado de escalamiento y uno "agrupado" los junta bajo
			// "conducta activa" (ver NivelClasifAnalisis): promediarlos daría un número sin sentido.
			boolean mixto = delGrupo.stream().map(e -> e.nivel).distinct().count() > 1;

			List<Map<String, Object>> porNivel = null;
			if (mixto) {
				// En vez de un promedio del grupo, uno por tanda, que sí es homogénea.
				porNivel = new ArrayList<>();
				Map<Integer, List<Espec>> porTanda = new LinkedHashMap<>();
				for (Espec e : delGrupo) {
					porTanda.computeIfAbsent(e.ordinal, k -> new ArrayList<>()).add(e);
				}
				for (Map.Entry<Integer, List<Espec>> tanda : porTanda.entrySet()) {
					Map<String, Object> renglon = new LinkedHashMap<>();
					renglon.put("letra", letra(tanda.getKey()));
					// get(0) es seguro: computeIfAbsent solo crea la lista al añadir el primero.
					renglon.put("nivelClasif", tanda.getValue().get(0).nivel);
					renglon.put("n", tanda.getValue().size());
					renglon.put("promedios", promedios(tanda.getValue()));
					porNivel.add(renglon);
				}
			}

			// n son los especímenes CON resultados; nTotal, los que debería tener el grupo. La diferencia
			// es lo que falta por analizar, y por eso van los dos: una media sobre 6 de 8 merece un aviso.
			grupo.put("n", delGrupo.size());
			grupo.put("nTotal", nTotal);
			grupo.put("faltantes", faltantes);
			grupo.put("nivelMixto", mixto);
			// Las dos claves existen siempre, una de las dos en null, según el caso.
			grupo.put("promedios", mixto ? null : promedios(delGrupo));
			grupo.put("porNivel", porNivel);
		}
		return grupos;
	}

	// Todas las tandas con el estado del análisis de su Día 2: de ahí salen nTotal y las faltantes.
	private List<FilaTanda> tandasDelExperimento(Integer idExperimento) {
		return jdbc.query("""
				SELECT t.idgrupo, t.ordinal, t.ncilindros, a.estado
				FROM tanda t
				JOIN grupo g ON g.idgrupo = t.idgrupo
				LEFT JOIN video v    ON v.idtanda = t.idtanda AND v.sesion = 'Dia 2'
				LEFT JOIN analisis a ON a.idvideo = v.idvideo
				WHERE g.idexperimento = ?
				ORDER BY t.idgrupo, t.ordinal
				""", (rs, fila) -> new FilaTanda(rs.getInt("idgrupo"), rs.getInt("ordinal"), rs.getInt("ncilindros"),
				rs.getString("estado")), idExperimento);
		// Los LEFT JOIN son imprescindibles aquí: una tanda sin video de Día 2, o con video pero sin
		// análisis, tiene que SALIR igualmente, porque es justo una de las que faltan. Con JOIN normales
		// desaparecería y nTotal quedaría corto.
		// La condición v.sesion = 'Dia 2' va en el ON y no en el WHERE: en el WHERE anularía el efecto del
		// LEFT JOIN y volvería a descartar las tandas sin ese video.
	}

	// Los grupos del experimento, en bruto. Se devuelven como mapas modificables a propósito:
	// resultadosPorGrupo les va añadiendo campos encima.
	private List<Map<String, Object>> gruposDelExperimento(Integer idExperimento) {
		return jdbc.query(
				"SELECT idgrupo, etiqueta, tipo, tratamiento FROM grupo WHERE idexperimento = ? ORDER BY idgrupo",
				(rs, fila) -> {
					Map<String, Object> g = new LinkedHashMap<>();
					g.put("idGrupo", rs.getInt("idgrupo"));
					g.put("etiqueta", rs.getString("etiqueta"));
					g.put("tipo", rs.getString("tipo"));
					g.put("tratamiento", rs.getString("tratamiento"));
					return g;
				}, idExperimento);
	}

	// nTotal: los cilindros de todas las tandas del grupo; faltantes: las que no tienen su Día 2 completado.
	private record Faltan(int nTotal, List<Map<String, Object>> faltantes) {
	}

	// Recorre la lista YA traída en vez de consultar por grupo: es lo que evita una consulta por grupo.
	// static: no usa nada de la instancia, solo sus parámetros, y así se puede probar sin levantar Spring.
	private static Faltan faltantesDelGrupo(int idGrupo, List<FilaTanda> tandas) {
		int nTotal = 0;
		List<Map<String, Object>> faltantes = new ArrayList<>();
		for (FilaTanda t : tandas) {
			if (t.idGrupo() != idGrupo) {
				continue;
			}
			// nTotal suma los cilindros de TODAS las tandas, estén analizadas o no: es cuántos especímenes
			// debería tener el grupo al final.
			nTotal += t.cilindros();
			// es(...) no truena con null, que es justo lo que llega de una tanda sin análisis.
			if (!EstadoAnalisis.COMPLETADO.es(t.estado())) {
				Map<String, Object> falta = new LinkedHashMap<>();
				falta.put("letra", letra(t.ordinal()));
				falta.put("nCilindros", t.cilindros());
				// El estado puede ser null (sin video de Día 2), 'en cola', 'procesando'... La pantalla lo
				// enseña para que se sepa por qué falta.
				falta.put("estado", t.estado());
				faltantes.add(falta);
			}
		}
		return new Faltan(nTotal, faltantes);
	}

	// Datos de las seis gráficas (M1A/M1B): todos los grupos del experimento y su Día 2 completado, sin importar la tanda
	// que se vea. Un solo cálculo (GraficasDeResultados) para la pantalla y el PDF.
	//
	// Este método solo TRADUCE: toma lo que ya leyó leerEspecimenes y lo pasa a los records que espera
	// GraficasDeResultados, donde está el cálculo. Así las gráficas de la web y las del PDF no pueden
	// diferir ni en un decimal.
	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> graficas(Integer idExperimento) {
		existe(idExperimento);
		List<Espec> especimenes = leerEspecimenes(idExperimento);
		List<FilaTanda> tandas = tandasDelExperimento(idExperimento);
		List<GraficasDeResultados.Grupo> grupos = gruposDelExperimento(idExperimento).stream().map(g -> {
			int idGrupo = (Integer) g.get("idGrupo");
			Faltan faltan = faltantesDelGrupo(idGrupo, tandas);
			return new GraficasDeResultados.Grupo(idGrupo, (String) g.get("etiqueta"), (String) g.get("tipo"),
					(String) g.get("tratamiento"), faltan.nTotal(), faltan.faltantes());
		}).toList();
		// e.filas es la duración en segundos del espécimen: todos sus segundos, incluidos los «sin ver».
		List<GraficasDeResultados.Especimen> delExperimento = especimenes.stream().map(e -> new GraficasDeResultados.Especimen(
				e.idGrupo, e.ordinal, letra(e.ordinal), e.cilindro, e.nivel, e.filas, e.total, e.porMinuto)).toList();
		return GraficasDeResultados.armar(grupos, delExperimento);
	}

	// Promedio (no suma: el tamaño del grupo no distorsiona, guía 7), desviación estándar y varianza MUESTRALES (n − 1) de
	// los segundos de cada conducta por espécimen, con un decimal. Con un solo espécimen la desviación y la varianza son 0
	// y sin especímenes los tres valores son null.
	//
	// El paréntesis explica la decisión científica: si se sumaran los segundos, un grupo de 12 especímenes
	// parecería tener el triple de inmovilidad que uno de 4 solo por ser más grande. El promedio hace
	// comparables grupos de distinto tamaño.
	// "Muestrales (n − 1)" es la corrección de Bessel que explica Estadisticos: los especímenes son una
	// muestra, no la población entera.
	// Fíjate en que aquí recorre CONDUCTAS (cuatro) y no CONDUCTAS_Y_SIN_VER: la tabla de promedios por
	// grupo no lleva columna de «sin ver». El reporte CSV/XLSX sí (usa TablaDeReporte.COLUMNAS).
	private Map<String, Object> promedios(List<Espec> especimenes) {
		Map<String, Object> promedios = new LinkedHashMap<>();
		for (String conducta : CONDUCTAS) {
			List<Long> totales = totalesDe(especimenes, conducta);
			Map<String, Object> par = new LinkedHashMap<>();
			par.put("media", Estadisticos.redondear(Estadisticos.media(totales)));
			par.put("de", Estadisticos.redondear(Estadisticos.desviacion(totales)));
			par.put("varianza", Estadisticos.redondear(Estadisticos.varianza(totales)));
			promedios.put(conducta, par);
		}
		return promedios;
	}

	// Los segundos de una conducta de cada espécimen (0 si no tuvo).
	//
	// Aquí el 0 SÍ es correcto: "ese espécimen no hizo nada de eso" es un dato real, y omitirlo lo sacaría
	// del promedio como si no se hubiera medido, subiendo la media artificialmente.
	private static List<Long> totalesDe(List<Espec> especimenes, String conducta) {
		return especimenes.stream().map(e -> e.total.getOrDefault(conducta, 0L)).toList();
	}

	// ---------------------------------------------------------------------------
	//  Reportes CSV y XLSX
	// ---------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public AlcanceDeReporte alcanceDeReporte(Integer idExperimento, Integer idGrupo, String letra) {
		existe(idExperimento);
		// Este es el 400 que hace que los controllers declaren el parámetro opcional: así la falta llega
		// aquí con su mensaje redactado en vez de acabar en un 500 genérico.
		if (idGrupo == null) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "Falta el grupo: ?grupo=<idGrupo> (y tanda=<letra> para una sola).");
		}
		// Las DOS condiciones: el grupo existe Y es de ese experimento. Sin la segunda, cambiando el
		// número se podrían sacar los resultados de otro experimento.
		List<String> etiquetas = jdbc.queryForList("SELECT etiqueta FROM grupo WHERE idgrupo = ? AND idexperimento = ?",
				String.class, idGrupo, idExperimento);
		if (etiquetas.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El grupo " + idGrupo + " no existe en el experimento " + idExperimento);
		}
		// Sin letra: el grupo completo. Los tres null son lo que distingue este caso (ver AlcanceDeReporte).
		if (letra == null || letra.isBlank()) {
			return new AlcanceDeReporte(idGrupo, etiquetas.get(0), null, null, null);
		}

		// Locale.ROOT en toUpperCase: sin él, en turco la "i" no sube a "I" y la letra se rechazaría.
		String l = letra.trim().toUpperCase(java.util.Locale.ROOT);
		// Exactamente UNA letra de la A a la Z: ni dos caracteres, ni un dígito, ni un símbolo.
		if (l.length() != 1 || l.charAt(0) < 'A' || l.charAt(0) > 'Z') {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "La tanda debe ser una letra, por ejemplo A.");
		}
		// El inverso de letra(): de 'A' a 1, de 'B' a 2.
		int ordinal = l.charAt(0) - 'A' + 1;
		List<Integer> tandas = jdbc.queryForList("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?",
				Integer.class, idGrupo, ordinal);
		if (tandas.isEmpty()) {
			// El mensaje usa la etiqueta del grupo y no su id: es lo que el usuario reconoce.
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El grupo " + etiquetas.get(0) + " no tiene la tanda " + l);
		}
		return new AlcanceDeReporte(idGrupo, etiquetas.get(0), tandas.get(0), ordinal, l);
	}

	@Override
	@Transactional(readOnly = true)
	public TablaDeReporte tablaDeReporte(Integer idExperimento, Integer idGrupo, String letra) {
		// Valida y traduce de una vez; de aquí salen el 400 y los 404.
		AlcanceDeReporte alcance = alcanceDeReporte(idExperimento, idGrupo, letra);
		// Se reutiliza la consulta grande y se filtra en memoria. El segundo término del && deja pasar
		// todas las tandas cuando el alcance es el grupo completo (ordinal null).
		List<Espec> especimenes = leerEspecimenes(idExperimento).stream()
				.filter(e -> e.idGrupo == alcance.idGrupo() && (alcance.ordinal() == null || e.ordinal == alcance.ordinal()))
				.toList();
		// 409 y no 404: el grupo existe, pero todavía no hay nada que reportar. El mensaje se adapta a si
		// se pidió el grupo entero o una tanda.
		if (especimenes.isEmpty()) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Todavía no hay análisis completados en "
					+ (alcance.letra() == null ? "el grupo " + alcance.etiqueta()
							: "la tanda " + alcance.letra() + " del grupo " + alcance.etiqueta()));
		}

		List<TablaDeReporte.FilaDeReporte> filas = new ArrayList<>();
		List<TablaDeReporte.Revisado> revisados = new ArrayList<>();
		for (Espec e : especimenes) {
			int ultimo = (int) Math.ceil(e.filas / 60.0);
			for (int minuto = 1; minuto <= ultimo; minuto++) {
				Map<String, Long> delMinuto = e.porMinuto.getOrDefault(minuto, Map.of());
				// Los segundos en el orden de COLUMNAS (las cuatro conductas más «sin ver»): la lista es
				// posicional, y ese orden es el que espera el encabezado del CSV y del XLSX.
				List<Long> segundos = TablaDeReporte.COLUMNAS.stream().map(c -> delMinuto.getOrDefault(c, 0L)).toList();
				filas.add(new TablaDeReporte.FilaDeReporte(letra(e.ordinal), e.cilindro, minuto, segundos));
			}
			if (e.revisado) {
				revisados.add(new TablaDeReporte.Revisado(letra(e.ordinal), e.cilindro));
			}
		}

		List<TablaDeReporte.Estadistico> estadisticos = new ArrayList<>();
		// Aquí sí se recorren las CINCO columnas (con «sin ver»), al revés que en promedios(): el reporte
		// lleva media, DE y varianza también de los segundos sin ver.
		for (String conducta : TablaDeReporte.COLUMNAS) {
			List<Long> totales = totalesDe(especimenes, conducta);
			estadisticos.add(new TablaDeReporte.Estadistico(Estadisticos.redondear(Estadisticos.media(totales)),
					Estadisticos.redondear(Estadisticos.desviacion(totales)), Estadisticos.redondear(Estadisticos.varianza(totales))));
		}
		boolean mixto = especimenes.stream().map(e -> e.nivel).distinct().count() > 1;
		// La tabla sale de aquí ya completa; ReporteCsv y ReporteXlsx solo la escriben, sin calcular nada.
		return new TablaDeReporte(alcance.etiqueta(), filas, estadisticos, mixto, revisados, modelosDelReporte(especimenes));
	}

	// ---------------------------------------------------------------------------
	//  PDF de diagnóstico
	// ---------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public Path diagnostico(Integer idExperimento, Integer idAnalisis) {
		existe(idExperimento);

		List<Map<String, Object>> filas = jdbc.queryForList("""
				SELECT a.estado, a.rutadiagnostico
				FROM analisis a
				JOIN video v ON v.idvideo = a.idvideo
				JOIN tanda t ON t.idtanda = v.idtanda
				JOIN grupo g ON g.idgrupo = t.idgrupo
				WHERE a.idanalisis = ? AND g.idexperimento = ?
				""", idAnalisis, idExperimento);
		if (filas.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND,
					"El análisis " + idAnalisis + " no existe en el experimento " + idExperimento);
		}
		String estado = (String) filas.get(0).get("estado");
		String ruta = (String) filas.get(0).get("rutadiagnostico");
		// La columna solo se llena cuando el estado es 'error' (ver la entidad Analisis): pedir el
		// diagnóstico de un análisis sano da 404 porque, en efecto, no existe.
		if (!EstadoAnalisis.ERROR.es(estado) || ruta == null) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El análisis " + idAnalisis + " no tiene reporte de diagnóstico");
		}
		// Tres 404 con mensajes distintos: el análisis no es de ahí, no tiene diagnóstico, o el archivo
		// se perdió. Son situaciones distintas y conviene distinguirlas al depurar.
		Path pdf = rutaSegura(ruta);
		if (pdf == null || !Files.isRegularFile(pdf)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El archivo de diagnóstico del análisis " + idAnalisis + " ya no existe");
		}
		return pdf;
	}

	// ---------------------------------------------------------------------------
	//  Salida por segundo del modelo (solo modo desarrollador)
	// ---------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public SalidaModelo salidaDelModelo(Integer idExperimento, Integer idGrupo, String letra) {
		// Las probabilidades del modelo solo salen en modo desarrollador: sin él, la ruta "no existe".
		// 404 y no 403: así la ruta no delata que existe en una instalación normal.
		if (!modoDesarrollador) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "No encontrado");
		}
		if (idGrupo == null) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "Falta el grupo: ?grupo=<idGrupo> (y tanda=<letra> para una sola).");
		}
		existe(idExperimento);
		List<String> etiquetas = jdbc.queryForList("SELECT etiqueta FROM grupo WHERE idgrupo = ? AND idexperimento = ?",
				String.class, idGrupo, idExperimento);
		if (etiquetas.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El grupo " + idGrupo + " no existe en el experimento " + idExperimento);
		}

		boolean deUnaTanda = letra != null && !letra.isBlank();
		// La consulta se arma por partes: un filtro más si se pidió una tanda concreta.
		List<Object> parametros = new ArrayList<>(List.of(idExperimento, idGrupo));
		String filtro = "";
		if (deUnaTanda) {
			String l = letra.trim().toUpperCase(java.util.Locale.ROOT);
			if (l.length() != 1 || l.charAt(0) < 'A' || l.charAt(0) > 'Z') {
				throw new ErrorApi(HttpStatus.BAD_REQUEST, "La tanda debe ser una letra, por ejemplo A.");
			}
			// Solo se concatena texto FIJO; el valor va como parámetro. Sin inyección posible.
			filtro = " AND t.ordinal = ?";
			parametros.add(l.charAt(0) - 'A' + 1);
		}
		// Solo tandas con análisis completado. Los nombres de los archivos salen del idVideo de la base (y el del
		// archivo descargado, del nombre original), nunca de la petición.
		List<Map<String, Object>> filas = jdbc.queryForList("""
				SELECT t.ordinal, v.idvideo, a.idanalisis, v.nombreoriginal
				FROM analisis a
				JOIN video v ON v.idvideo = a.idvideo
				JOIN tanda t ON t.idtanda = v.idtanda
				JOIN grupo g ON g.idgrupo = t.idgrupo
				WHERE g.idexperimento = ? AND g.idgrupo = ? AND a.estado = 'completado'
				""" + filtro + " ORDER BY t.ordinal", parametros.toArray());

		Map<String, Path> archivos = new LinkedHashMap<>();
		for (Map<String, Object> fila : filas) {
			int idAnalisis = ((Number) fila.get("idanalisis")).intValue();
			int idVideo = ((Number) fila.get("idvideo")).intValue();
			Path csv = rutaTrabajo(idAnalisis, idVideo + "_cnnseg.csv");
			// Un análisis completado puede no tener el CSV (se borró, o es de antes): se salta sin fallar.
			if (!Files.isRegularFile(csv)) {
				continue;
			}
			String base = nombreBase((String) fila.get("nombreoriginal"), idVideo);
			if (archivos.containsKey(base + "_cnnseg.csv")) {
				base = base + "_tanda" + letra(((Number) fila.get("ordinal")).intValue());   // dos videos con el mismo nombre
				// (pasa de verdad: si dos tandas se grabaron con el nombre que pone la cámara por omisión,
				// el segundo pisaría al primero dentro del ZIP)
			}
			archivos.put(base + "_cnnseg.csv", csv);
			Path resumen = rutaTrabajo(idAnalisis, idVideo + "_cnnseg_resumen.csv");
			// El resumen solo se incluye en el ZIP del grupo: al pedir una tanda se baja el CSV solo.
			if (!deUnaTanda && Files.isRegularFile(resumen)) {
				archivos.put(base + "_cnnseg_resumen.csv", resumen);
			}
		}
		if (archivos.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "Esta tanda no tiene la salida por segundo del modelo");
		}
		// Una tanda: el CSV suelto, con su propio nombre. El false dice que no es ZIP.
		if (deUnaTanda) {
			return new SalidaModelo(archivos.keySet().iterator().next(), false, archivos);
		}
		// El grupo entero: un ZIP con todos. El controller lo arma recorriendo este mapa.
		return new SalidaModelo(paraNombreDeArchivo(etiquetas.get(0)) + "_salida_modelo.zip", true, archivos);
	}

	// analisis/{id}/trabajo/{nombre}, normalizada: tiene que seguir DENTRO de la raíz (si no, una ruta que no existe).
	//
	// Devolver "no-existe" en vez de null es un detalle práctico: quien llama comprueba con
	// Files.isRegularFile de todos modos, así que no hace falta un caso aparte para el null.
	private Path rutaTrabajo(int idAnalisis, String nombre) {
		Path ruta = raiz.resolve("analisis").resolve(String.valueOf(idAnalisis)).resolve("trabajo").resolve(nombre).normalize();
		return ruta.startsWith(raiz) ? ruta : raiz.resolve("no-existe");
	}

	// El nombre original del video sin su extensión ("acu-yintang2.mp4" -> "acu-yintang2"), o "video<idVideo>" si no lo tiene.
	//
	// Se usa el nombre que puso el usuario porque es el que reconoce al abrir el ZIP; "video17.csv" no le
	// diría nada. Pero pasa por paraNombreDeArchivo antes, y el respaldo con el id cubre los videos
	// anteriores al 20-oct-2026, que no guardaron nombre.
	private static String nombreBase(String nombreOriginal, int idVideo) {
		if (nombreOriginal == null || nombreOriginal.isBlank()) {
			return "video" + idVideo;
		}
		// lastIndexOf('.') > 0 y no >= 0: un archivo que empiece por punto (".oculto") no tiene extensión,
		// ese punto es parte del nombre.
		int punto = nombreOriginal.lastIndexOf('.');
		String sinExtension = punto > 0 ? nombreOriginal.substring(0, punto) : nombreOriginal;
		String limpio = paraNombreDeArchivo(sinExtension);
		// Si al limpiarlo no queda nada (un nombre hecho solo de caracteres prohibidos), se usa el id.
		return limpio.isBlank() ? "video" + idVideo : limpio;
	}

	// Sin los caracteres que no valen en un nombre de archivo (rutas, comodines, de control).
	//
	// Es una medida de SEGURIDAD, no de estética: este texto acaba dentro de un ZIP y en la cabecera
	// Content-Disposition. Las barras permitirían escribir fuera de la carpeta al descomprimir, y los
	// caracteres de control podrían romper la cabecera HTTP.
	// La expresión regular cubre: \ / : * ? " < > | y cualquier carácter de control (\p{Cntrl}).
	// Las barras van cuádruples porque se escapan dos veces: una para el texto de Java y otra para la
	// expresión regular.
	private static String paraNombreDeArchivo(String texto) {
		return texto.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
	}

	// ---------------------------------------------------------------------------
	//  Imagen de control
	// ---------------------------------------------------------------------------

	// etiquetar.py la deja en analisis/{id}/trabajo/ y nombra el archivo con el idVideo (no con datos del usuario).
	// Normalizada, para que siga DENTRO de la raíz.
	//
	// El paréntesis es la nota de seguridad de siempre: la ruta se arma solo con números de la base.
	private Path rutaControl(int idAnalisis, int idVideo) {
		return raiz.resolve("analisis").resolve(String.valueOf(idAnalisis)).resolve("trabajo")
				.resolve(idVideo + "_control.jpg").normalize();
	}

	@Override
	@Transactional(readOnly = true)
	public Path control(Integer idExperimento, Integer idAnalisis) {
		existe(idExperimento);

		// Hace falta el idVideo porque el nombre del archivo se arma con él, no con el del análisis.
		List<Integer> videos = jdbc.queryForList("""
				SELECT a.idvideo
				FROM analisis a
				JOIN video v ON v.idvideo = a.idvideo
				JOIN tanda t ON t.idtanda = v.idtanda
				JOIN grupo g ON g.idgrupo = t.idgrupo
				WHERE a.idanalisis = ? AND g.idexperimento = ?
				""", Integer.class, idAnalisis, idExperimento);
		if (videos.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND,
					"El análisis " + idAnalisis + " no existe en el experimento " + idExperimento);
		}
		Path imagen = rutaControl(idAnalisis, videos.get(0));
		// Se vuelve a comprobar que esté dentro de la raíz, aunque rutaControl ya normalizó: comprobar en
		// el punto de uso es la costumbre correcta, no confiar en que lo hizo otro.
		if (!imagen.startsWith(raiz) || !Files.isRegularFile(imagen)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El análisis " + idAnalisis + " no tiene imagen de control");
		}
		return imagen;
	}

	// ---------------------------------------------------------------------------

	// El 404 común a TODOS los métodos del servicio, en un solo sitio: así ninguno se olvida de
	// comprobarlo ni lo hace con otro mensaje.
	private void existe(Integer idExperimento) {
		if (!experimentoRepository.existsById(idExperimento)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + idExperimento + " no existe");
		}
	}

	// 1 = A, 2 = B, ...
	// Aritmética de caracteres: 'A' + 0 es 'A', 'A' + 1 es 'B'. El -1 ajusta porque los ordinales empiezan
	// en 1; la conversión a char evita que salga el número 65 en vez de "A".
	private static String letra(int ordinal) {
		return String.valueOf((char) ('A' + ordinal - 1));
	}

	// Un atajo para no escribir Estadisticos.redondear en cada porcentaje. El redondeo a un decimal vive
	// en un solo sitio, para que la pantalla y los reportes muestren exactamente los mismos números.
	private static double redondear(double valor) {
		return Estadisticos.redondear(valor);
	}

}
