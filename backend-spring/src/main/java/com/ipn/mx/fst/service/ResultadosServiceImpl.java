package com.ipn.mx.fst.service;

import com.ipn.mx.fst.repository.ExperimentoRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Progreso y resultados de un experimento. Todo se calcula al pedirlo, desde SEGUNDO y ANALISIS: los totales
// no se guardan (D-52). Las consultas son agregadas (GROUP BY en la base) y se arman con JdbcTemplate, como
// en VideoServiceImpl, sin una consulta por espécimen.
@Service
public class ResultadosServiceImpl implements ResultadosService {

	private static final Logger log = LoggerFactory.getLogger(ResultadosServiceImpl.class);

	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
	private static final JsonMapper JSON = JsonMapper.builder().build();

	// Las cuatro conductas, con su nombre de la base y en este orden en todas las respuestas.
	private static final List<String> CONDUCTAS = List.of("nado activo", "inmovilidad", "escalamiento", "conducta activa");

	// Raíz del volumen videos_reportes: de ahí salen pausa.json y los PDF de diagnóstico.
	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	// MODO_DESARROLLADOR: los tiempos de cada análisis (tiempos.json) son solo para quien desarrolla. Con false no
	// salen en /status, ni en /results, ni en el PDF, aunque exista un tiempos.json de antes. El avance
	// (progreso.json) no depende de esto: la barra de 0 a 100 % es para todos.
	@Value("${app.modo-desarrollador:false}")
	private boolean modoDesarrollador;

	// Minutos que espera un análisis en pausa antes de cancelarse (la misma variable que usa el worker).
	@Value("${PAUSE_TIMEOUT_MINUTES:5}")
	private int minutosDePausa;

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
	private record FilaConteo(int idGrupo, String etiqueta, int ordinal, int cilindro, String nivel, int idAnalisis,
			int minuto, String conducta, long segundos, boolean revisado) {
	}

	// Un espécimen con sus conteos ya sumados. filas incluye los segundos sin conducta (NULL): cuentan en el
	// denominador del porcentaje pero en ninguna conducta.
	private static class Espec {
		int idGrupo;
		String etiqueta;
		int ordinal;
		int cilindro;
		String nivel;
		int idAnalisis;
		boolean revisado;
		long filas;
		final Map<String, Long> total = new HashMap<>();
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
				SELECT a.idanalisis, g.etiqueta, t.ordinal, v.sesion, a.estado, a.etapa, a.fechaanalisis, a.rutadiagnostico
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
			item.put("tanda", rs.getString("etiqueta") + " · " + letra(rs.getInt("ordinal")));
			item.put("sesion", rs.getString("sesion"));
			item.put("estado", estado);
			item.put("etapa", etapa);
			Avance avance = avance(idAnalisis, estado, etapa);
			item.put("porcentaje", avance.general());
			item.put("porcentajeEtapa", avance.etapa());
			item.put("pausa", "en pausa".equals(estado) ? pausa(idAnalisis) : null);
			item.put("diagnosticoDisponible", diagnosticoDisponible(estado, rs.getString("rutadiagnostico")));
			// Mientras el análisis no termina es la hora en que se encoló (D-79); el worker la reemplaza al
			// terminar. En la zona de México, con su desfase, para que se lea como hora local.
			item.put("fechaAnalisis", rs.getObject("fechaanalisis", OffsetDateTime.class)
					.atZoneSameInstant(ZONA).toOffsetDateTime());
			// Todavía no se guarda en ningún lado por qué se canceló un análisis.
			item.put("motivoCancelacion", null);
			// Un análisis en cola puede traer el archivo de una corrida anterior (se reencoló): no es de esta
			// espera, así que no se muestra. Los demás estados leen tiempos.json, o null si no existe.
			item.put("tiempos", modoDesarrollador && !"en cola".equals(estado) ? tiempos(idAnalisis) : null);
			return item;
		}, idExperimento);

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
	private Avance avance(int idAnalisis, String estado, String etapa) {
		if ("en cola".equals(estado)) {
			return new Avance(0, 0);
		}
		if ("completado".equals(estado)) {
			return new Avance(100, 100);
		}
		Avance leido = leerProgreso(idAnalisis, etapa);
		return leido != null ? leido : new Avance(porcentaje(estado, etapa), null);
	}

	// progreso.json: {"etapa", "porcentajeEtapa", "porcentaje", "actualizado"}. Mismo cuidado que con pausa.json: si
	// el archivo está roto, null y un aviso en el log (si no existe es lo normal en un análisis viejo: sin aviso).
	private Avance leerProgreso(int idAnalisis, String etapaDeLaBase) {
		Path archivo = raiz.resolve("analisis").resolve(String.valueOf(idAnalisis)).resolve("progreso.json");
		if (!Files.isRegularFile(archivo)) {
			return null;
		}
		try {
			JsonNode progreso = JSON.readTree(Files.readString(archivo));
			if (!etapaDeLaBase.equals(progreso.path("etapa").asText())) {
				return null;
			}
			if (!progreso.has("porcentaje") || !progreso.has("porcentajeEtapa")) {
				return null;
			}
			int general = Math.max(0, Math.min(100, progreso.path("porcentaje").asInt()));
			int deLaEtapa = Math.max(0, Math.min(100, progreso.path("porcentajeEtapa").asInt()));
			return new Avance(general, deLaEtapa);
		} catch (IOException | JacksonException e) {
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
	private Map<String, Object> tiempos(int idAnalisis) {
		JsonNode t = leerTiempos(idAnalisis);
		if (t == null) {
			return null;
		}
		List<Map<String, Object>> etapas = new ArrayList<>();
		for (JsonNode e : t.path("etapas")) {
			Map<String, Object> etapa = new LinkedHashMap<>();
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
		return datos;
	}

	private Map<String, Object> equipo(JsonNode t) {
		Map<String, Object> equipo = new LinkedHashMap<>();
		for (String campo : List.of("nombre", "cpu", "nucleos", "memoriaGB", "dockerCPU")) {
			equipo.put(campo, valor(t.path("equipo").path(campo)));
		}
		return equipo;
	}

	// El valor de un campo de tiempos.json como número o texto, o null si falta o es null.
	private static Object valor(JsonNode nodo) {
		if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
			return null;
		}
		return nodo.isNumber() ? nodo.numberValue() : nodo.asText();
	}

	// Lo que /results muestra de cada análisis: cuánto tardó y en qué equipo (el nombre y, si no tiene, el CPU).
	private Map<String, Object> tiempoAnalisis(int idAnalisis) {
		JsonNode t = leerTiempos(idAnalisis);
		if (t == null) {
			return null;
		}
		Object nombre = valor(t.path("equipo").path("nombre"));
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("totalSegundos", valor(t.path("totalSegundos")));
		datos.put("equipo", nombre != null ? nombre : valor(t.path("equipo").path("cpu")));
		return datos;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Map<String, Object>> tiemposDelExperimento(Integer idExperimento) {
		existe(idExperimento);
		if (!modoDesarrollador) {
			return List.of();      // sin modo desarrollador el PDF no lleva la sección de tiempos
		}
		return jdbc.query("""
				SELECT a.idanalisis, g.etiqueta, t.ordinal, v.duracion
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
			item.put("tiempos", tiempos(rs.getInt("idanalisis")));
			return item;
		}, idExperimento);
	}

	// Avance en porcentaje por etapa (el de antes de progreso.json, y el respaldo): en cola 0, completado 100 y,
	// en cualquier otro estado, el de su etapa.
	private static int porcentaje(String estado, String etapa) {
		if ("en cola".equals(estado)) {
			return 0;
		}
		if ("completado".equals(estado)) {
			return 100;
		}
		return switch (etapa) {
			case "preprocesamiento" -> 33;
			case "cilindros" -> 67;
			case "clasificacion" -> 100;
			default -> 0;
		};
	}

	// Lo que dice analisis/{id}/pausa.json (lo escribe worker_fake; el worker real no pausa): en qué cilindro
	// y desde qué segundo, y cuántos segundos quedan antes de que se cancele. Si el archivo no existe o está
	// roto, null y un aviso en el log.
	private Map<String, Object> pausa(int idAnalisis) {
		Path archivo = raiz.resolve("analisis").resolve(String.valueOf(idAnalisis)).resolve("pausa.json");
		try {
			JsonNode pausa = JSON.readTree(Files.readString(archivo));
			OffsetDateTime horaPausa = OffsetDateTime.parse(pausa.path("hora_pausa").asText());
			long restantes = Duration.between(OffsetDateTime.now(), horaPausa.plusMinutes(minutosDePausa)).getSeconds();

			Map<String, Object> datos = new LinkedHashMap<>();
			datos.put("cilindro", pausa.path("cilindro").asInt());
			datos.put("desdeSegundo", pausa.path("desde_segundo").asInt());
			datos.put("segundosRestantes", Math.max(0, restantes));
			return datos;
		} catch (IOException | JacksonException | DateTimeParseException e) {
			log.warn("El análisis {} está en pausa pero no se pudo leer {}: {}", idAnalisis, archivo, e.getMessage());
			return null;
		}
	}

	private boolean diagnosticoDisponible(String estado, String rutaDiagnostico) {
		if (!"error".equals(estado) || rutaDiagnostico == null) {
			return false;
		}
		Path pdf = rutaSegura(rutaDiagnostico);
		return pdf != null && Files.isRegularFile(pdf);
	}

	// Une la raíz con una ruta que viene de la base y se asegura de que siga DENTRO de la raíz: la ruta se
	// guarda relativa, pero no se sigue a ciegas (un "../" la sacaría del volumen). Null si se sale.
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
		return respuestaDeEspecimenes(leerEspecimenes(idExperimento), false);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> resultadosPorMinuto(Integer idExperimento) {
		existe(idExperimento);
		return respuestaDeEspecimenes(leerEspecimenes(idExperimento), true);
	}

	// /results y /results/byminute comparten la consulta y el armado; el segundo solo agrega los minutos.
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
			item.put("totales", totales(e));
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

	// Las CUATRO conductas siempre: segundos y porcentaje (con un decimal) sobre todos los segundos del
	// espécimen.
	private Map<String, Object> totales(Espec e) {
		Map<String, Object> totales = new LinkedHashMap<>();
		for (String conducta : CONDUCTAS) {
			long segundos = e.total.getOrDefault(conducta, 0L);
			Map<String, Object> par = new LinkedHashMap<>();
			par.put("segundos", segundos);
			par.put("porcentaje", e.filas == 0 ? 0.0 : redondear(100.0 * segundos / e.filas));
			totales.put(conducta, par);
		}
		return totales;
	}

	// minuto = ceil(segundo / 60), del 1 al último; las cuatro conductas siempre, con 0 si no hay.
	private List<Map<String, Object>> minutos(Espec e) {
		int ultimo = (int) Math.ceil(e.filas / 60.0);
		List<Map<String, Object>> minutos = new ArrayList<>();
		for (int minuto = 1; minuto <= ultimo; minuto++) {
			Map<String, Long> delMinuto = e.porMinuto.getOrDefault(minuto, Map.of());
			Map<String, Object> fila = new LinkedHashMap<>();
			fila.put("minuto", minuto);
			for (String conducta : CONDUCTAS) {
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
	private List<Espec> leerEspecimenes(Integer idExperimento) {
		List<FilaConteo> filas = jdbc.query("""
				SELECT g.idgrupo, g.etiqueta, t.ordinal, e.numerocilindro, a.nivelclasif, a.idanalisis,
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
				GROUP BY g.idgrupo, g.etiqueta, t.ordinal, e.numerocilindro, a.nivelclasif, a.idanalisis,
				         ((s.segundo - 1) / 60) + 1, s.conducta
				ORDER BY g.idgrupo, t.ordinal, e.numerocilindro, minuto
				""", (rs, fila) -> new FilaConteo(rs.getInt("idgrupo"), rs.getString("etiqueta"),
				rs.getInt("ordinal"), rs.getInt("numerocilindro"), rs.getString("nivelclasif"), rs.getInt("idanalisis"),
				rs.getInt("minuto"), rs.getString("conducta"), rs.getLong("segundos"), rs.getBoolean("revisado")),
				idExperimento);

		Map<String, Espec> porEspecimen = new LinkedHashMap<>();
		for (FilaConteo f : filas) {
			Espec e = porEspecimen.computeIfAbsent(f.idGrupo() + "|" + f.ordinal() + "|" + f.cilindro(), k -> {
				Espec nuevo = new Espec();
				nuevo.idGrupo = f.idGrupo();
				nuevo.etiqueta = f.etiqueta();
				nuevo.ordinal = f.ordinal();
				nuevo.cilindro = f.cilindro();
				nuevo.nivel = f.nivel();
				nuevo.idAnalisis = f.idAnalisis();
				return nuevo;
			});
			e.filas += f.segundos();
			e.revisado |= f.revisado();
			e.total.merge(f.conducta(), f.segundos(), Long::sum);
			e.porMinuto.computeIfAbsent(f.minuto(), k -> new HashMap<>()).merge(f.conducta(), f.segundos(), Long::sum);
		}
		return new ArrayList<>(porEspecimen.values());
	}

	@Override
	@Transactional(readOnly = true)
	public List<Map<String, Object>> resultadosPorGrupo(Integer idExperimento) {
		existe(idExperimento);

		List<Espec> especimenes = leerEspecimenes(idExperimento);
		// Todas las tandas con el estado del análisis de su Día 2: de ahí salen nTotal y las faltantes.
		List<FilaTanda> tandas = jdbc.query("""
				SELECT t.idgrupo, t.ordinal, t.ncilindros, a.estado
				FROM tanda t
				JOIN grupo g ON g.idgrupo = t.idgrupo
				LEFT JOIN video v    ON v.idtanda = t.idtanda AND v.sesion = 'Dia 2'
				LEFT JOIN analisis a ON a.idvideo = v.idvideo
				WHERE g.idexperimento = ?
				ORDER BY t.idgrupo, t.ordinal
				""", (rs, fila) -> new FilaTanda(rs.getInt("idgrupo"), rs.getInt("ordinal"), rs.getInt("ncilindros"),
				rs.getString("estado")), idExperimento);

		List<Map<String, Object>> grupos = jdbc.query(
				"SELECT idgrupo, etiqueta, tipo, tratamiento FROM grupo WHERE idexperimento = ? ORDER BY idgrupo",
				(rs, fila) -> {
					Map<String, Object> g = new LinkedHashMap<>();
					g.put("idGrupo", rs.getInt("idgrupo"));
					g.put("etiqueta", rs.getString("etiqueta"));
					g.put("tipo", rs.getString("tipo"));
					g.put("tratamiento", rs.getString("tratamiento"));
					return g;
				}, idExperimento);

		for (Map<String, Object> grupo : grupos) {
			int idGrupo = (Integer) grupo.get("idGrupo");
			List<Espec> delGrupo = especimenes.stream().filter(e -> e.idGrupo == idGrupo).toList();

			int nTotal = 0;
			List<Map<String, Object>> faltantes = new ArrayList<>();
			for (FilaTanda t : tandas) {
				if (t.idGrupo() != idGrupo) {
					continue;
				}
				nTotal += t.cilindros();
				if (!"completado".equals(t.estado())) {
					Map<String, Object> falta = new LinkedHashMap<>();
					falta.put("letra", letra(t.ordinal()));
					falta.put("nCilindros", t.cilindros());
					falta.put("estado", t.estado());
					faltantes.add(falta);
				}
			}

			// Las tandas completadas con niveles de clasificación distintos no se promedian juntas.
			boolean mixto = delGrupo.stream().map(e -> e.nivel).distinct().count() > 1;

			List<Map<String, Object>> porNivel = null;
			if (mixto) {
				porNivel = new ArrayList<>();
				Map<Integer, List<Espec>> porTanda = new LinkedHashMap<>();
				for (Espec e : delGrupo) {
					porTanda.computeIfAbsent(e.ordinal, k -> new ArrayList<>()).add(e);
				}
				for (Map.Entry<Integer, List<Espec>> tanda : porTanda.entrySet()) {
					Map<String, Object> renglon = new LinkedHashMap<>();
					renglon.put("letra", letra(tanda.getKey()));
					renglon.put("nivelClasif", tanda.getValue().get(0).nivel);
					renglon.put("n", tanda.getValue().size());
					renglon.put("promedios", promedios(tanda.getValue()));
					porNivel.add(renglon);
				}
			}

			grupo.put("n", delGrupo.size());
			grupo.put("nTotal", nTotal);
			grupo.put("faltantes", faltantes);
			grupo.put("nivelMixto", mixto);
			grupo.put("promedios", mixto ? null : promedios(delGrupo));
			grupo.put("porNivel", porNivel);
		}
		return grupos;
	}

	// Promedio (no suma: el tamaño del grupo no distorsiona, guía 7) y desviación estándar MUESTRAL (n − 1) de
	// los segundos de cada conducta por espécimen, con un decimal. Con un solo espécimen la desviación es 0 y
	// sin especímenes los dos valores son null.
	private Map<String, Object> promedios(List<Espec> especimenes) {
		Map<String, Object> promedios = new LinkedHashMap<>();
		for (String conducta : CONDUCTAS) {
			Map<String, Object> par = new LinkedHashMap<>();
			if (especimenes.isEmpty()) {
				par.put("media", null);
				par.put("de", null);
			} else {
				double suma = 0;
				for (Espec e : especimenes) {
					suma += e.total.getOrDefault(conducta, 0L);
				}
				double media = suma / especimenes.size();
				double de = 0;
				if (especimenes.size() > 1) {
					double cuadrados = 0;
					for (Espec e : especimenes) {
						double diferencia = e.total.getOrDefault(conducta, 0L) - media;
						cuadrados += diferencia * diferencia;
					}
					de = Math.sqrt(cuadrados / (especimenes.size() - 1));
				}
				par.put("media", redondear(media));
				par.put("de", redondear(de));
			}
			promedios.put(conducta, par);
		}
		return promedios;
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
		if (!"error".equals(estado) || ruta == null) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El análisis " + idAnalisis + " no tiene reporte de diagnóstico");
		}
		Path pdf = rutaSegura(ruta);
		if (pdf == null || !Files.isRegularFile(pdf)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El archivo de diagnóstico del análisis " + idAnalisis + " ya no existe");
		}
		return pdf;
	}

	// ---------------------------------------------------------------------------

	private void existe(Integer idExperimento) {
		if (!experimentoRepository.existsById(idExperimento)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + idExperimento + " no existe");
		}
	}

	// 1 = A, 2 = B, ...
	private static String letra(int ordinal) {
		return String.valueOf((char) ('A' + ordinal - 1));
	}

	private static double redondear(double valor) {
		return Math.round(valor * 10.0) / 10.0;
	}

}
