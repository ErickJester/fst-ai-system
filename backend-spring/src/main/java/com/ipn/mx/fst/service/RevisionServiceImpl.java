// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Los tres enums de dominio que se validan aquí.
import com.ipn.mx.fst.domain.valores.EstadoAnalisis;
import com.ipn.mx.fst.domain.valores.OrigenSegundo;
import com.ipn.mx.fst.domain.valores.TipoConducta;
import com.ipn.mx.fst.utilerias.ErrorApi;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
// Duration e Instant: la vigencia del token del video.
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
// Las clases de JWT, para el token del video.
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Jackson 3 (paquete tools.jackson), para leer cajas.json.
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Revisión segundo a segundo de un análisis completado. Las consultas son explícitas con JdbcTemplate, como en
// ResultadosServiceImpl, y los totales de cada espécimen se piden a ResultadosService para que tengan
// exactamente la misma forma que /results.
//
// El contrato completo de los cinco métodos está en RevisionService.
// Es la pantalla donde una persona comprueba el trabajo del modelo. Lo que escribe aquí queda en la
// columna origen de la tabla segundo (ver OrigenSegundo), que es la que da trazabilidad al dato y permite
// decir después qué parte de la medición es automática y cuál está revisada.
// Esa última frase del encabezado es la regla de siempre en este proyecto: no recalcular lo que ya calcula
// otro, para que dos pantallas nunca enseñen números distintos.
@Service
public class RevisionServiceImpl implements RevisionService {

	private static final Logger log = LoggerFactory.getLogger(RevisionServiceImpl.class);

	@Autowired
	private ReporteAlmacen reportes;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	// Dominio tipo_conducta de la base, tomado del enum. La conducta nula también es válida (no se ve o no se decidió).
	// Se saca del enum y no se escribe a mano: si el dominio cambia, esta lista cambia con él.
	private static final List<String> CONDUCTAS = Arrays.stream(TipoConducta.values()).map(TipoConducta::valor).toList();

	// Dominio origen_segundo menos 'maquina': lo que escribe una persona al revisar.
	//
	// El filtro es la regla de seguridad del método corregir: por esta vía NADIE puede marcar un segundo
	// como 'maquina'. Si se pudiera, se perdería la trazabilidad, porque una corrección humana pasaría por
	// medición automática.
	private static final List<String> ORIGENES = Arrays.stream(OrigenSegundo.values())
			.filter(origen -> origen != OrigenSegundo.MAQUINA).map(OrigenSegundo::valor).toList();

	// Tamaño del video si falta cajas.json: el de la pausa de ejemplo de la guía.
	// Un valor razonable es mejor que fallar: sin él, la pantalla no podría dibujar nada.
	private static final int ANCHO_POR_OMISION = 1920;
	private static final int ALTO_POR_OMISION = 1080;

	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	// Vigencia del token del video (VIDEO_TOKEN_MINUTES): lo que dura una revisión larga sin pedir otro.
	// 120 minutos: suficiente para revisar 300 segundos con calma, y poco para un token que viaja en la URL.
	@Value("${app.video.minutos}")
	private long minutosDelToken;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private SecretKey llaveJwt;

	// Para devolver los totales con la misma forma que /results, sin recalcularlos.
	@Autowired
	private ResultadosService resultadosService;

	private Path raiz;

	// Un decodificador APARTE del de la sesión (misma llave, otro validador): acepta solo tipo = "video". No es
	// un bean porque Spring Security toma el JwtDecoder de la sesión por tipo y dos beans lo harían ambiguo.
	//
	// Es el mismo caso que el decodificador de enlaces de AuthServiceImpl: tres tipos de token, tres
	// validadores, un solo bean (el de la sesión) para que Spring no dude.
	private JwtDecoder decodificadorDeVideo;

	@PostConstruct
	void iniciar() {
		raiz = Path.of(raizConfigurada).toAbsolutePath().normalize();
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(llaveJwt)
				// Se fija el algoritmo en vez de aceptar el que diga el token: aceptarlo es un fallo
				// clásico de seguridad en JWT.
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		// createDefault() (firma y vigencia) se vuelve a incluir porque setJwtValidator REEMPLAZA la
		// validación por omisión.
		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefault(),
				new JwtClaimValidator<String>("tipo", "video"::equals)));
		decodificadorDeVideo = decodificador;
	}

	// Un análisis con la tanda y el creador del experimento (que por ahora es quien subió los videos).
	//
	// El paréntesis es importante: la tabla video NO guarda quién la subió, y el backend usa al creador
	// del experimento en su lugar. Funciona porque solo el creador puede subir (ver VideoServiceImpl y
	// D-107); si eso cambiara, habría que añadir la columna.
	// Un record local para no andar pasando ocho valores sueltos entre métodos.
	private record Datos(String estado, String archivo, String etiqueta, int ordinal, int idVideo, int idTanda,
			String creador, String nombreVideo) {
	}

	// ---------------------------------------------------------------------------
	//  GET /review
	// ---------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> revision(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		// cargar() da el 404 si el experimento no existe o el análisis es de otro.
		Datos a = cargar(idExperimento, idAnalisis);
		exigirCompletado(a);

		boolean hayVideo = videoDisponible(a, idAnalisis);

		// Todos los segundos de cada espécimen de la tanda, por cilindro y por segundo.
		// Son hasta 1 200 filas (4 especímenes × 300 s) y se mandan juntas a propósito: así la pantalla se
		// mueve por el video sin pedir nada más al servidor.
		Map<Integer, List<Map<String, Object>>> porCilindro = new LinkedHashMap<>();
		int duracion = 0;
		List<Map<String, Object>> filas = jdbc.query("""
				SELECT e.numerocilindro, s.segundo, s.conducta, s.origen
				FROM especimen e
				JOIN observacion o ON o.idespecimen = e.idespecimen AND o.idvideo = ?
				JOIN segundo s     ON s.idobservacion = o.idobservacion
				WHERE e.idtanda = ?
				ORDER BY e.numerocilindro, s.segundo
				""", (rs, fila) -> {
			Map<String, Object> s = new LinkedHashMap<>();
			s.put("cilindro", rs.getInt("numerocilindro"));
			s.put("segundo", rs.getInt("segundo"));
			// getString devuelve null si la columna es NULL, que es justo lo que se quiere para conducta.
			s.put("conducta", rs.getString("conducta"));
			s.put("origen", rs.getString("origen"));
			return s;
		}, a.idVideo(), a.idTanda());
		for (Map<String, Object> s : filas) {
			// remove saca el cilindro Y lo devuelve: se usa para agrupar y desaparece del mapa, porque
			// dentro de cada espécimen ya no hace falta repetirlo en cada segundo.
			int cilindro = (Integer) s.remove("cilindro");
			// La duración es el número de segundo más alto, calculado en el mismo recorrido.
			duracion = Math.max(duracion, (Integer) s.get("segundo"));
			porCilindro.computeIfAbsent(cilindro, k -> new ArrayList<>()).add(s);
		}

		// cajas.json (lo escribe el worker): el recuadro de cada tubo en píxeles del video, y el tamaño del video.
		// Es otro archivo del canal por volumen entre worker y backend, como pausa.json.
		// La pantalla los necesita para dibujar el recuadro de cada espécimen encima del video.
		Map<Integer, Map<String, Object>> recuadros = new LinkedHashMap<>();
		int ancho = ANCHO_POR_OMISION;
		int alto = ALTO_POR_OMISION;
		Path cajas = carpeta(idAnalisis).resolve("cajas.json");
		try {
			JsonNode raizJson = JSON.readTree(Files.readString(cajas));
			// asInt(valorPorOmision) devuelve el de por omisión si la clave no está.
			ancho = raizJson.path("ancho").asInt(ANCHO_POR_OMISION);
			alto = raizJson.path("alto").asInt(ALTO_POR_OMISION);
			for (JsonNode r : raizJson.path("recuadros")) {
				Map<String, Object> recuadro = new LinkedHashMap<>();
				// Las cuatro esquinas, en un bucle sobre sus nombres para no repetir cuatro líneas.
				for (String lado : List.of("x0", "y0", "x1", "y1")) {
					recuadro.put(lado, r.path(lado).asInt());
				}
				recuadros.put(r.path("cilindro").asInt(), recuadro);
			}
		} catch (IOException | JacksonException e) {
			// No se lanza nada: sin cajas.json la revisión sigue siendo útil (se ven los segundos y el
			// video), solo faltan los recuadros. Fallar aquí haría inservible la pantalla entera por un
			// archivo auxiliar.
			log.warn("El análisis {} no tiene un cajas.json legible ({}): los recuadros quedan vacíos y el video se "
					+ "supone de {}x{}", idAnalisis, e.getMessage(), ANCHO_POR_OMISION, ALTO_POR_OMISION);
		}

		// El mapa agrupado se convierte en la lista de objetos que espera el cliente.
		List<Map<String, Object>> especimenes = new ArrayList<>();
		for (Map.Entry<Integer, List<Map<String, Object>>> e : porCilindro.entrySet()) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("numeroCilindro", e.getKey());
			// "Control · A": la etiqueta del grupo y la letra de la tanda. El mismo texto se usa abajo
			// para filtrar los totales, de ahí que se arme igual en los dos sitios.
			item.put("tanda", a.etiqueta() + " · " + letra(a.ordinal()));
			// get devuelve null si no había recuadro para ese cilindro, y eso llega como null al JSON.
			item.put("recuadro", recuadros.get(e.getKey()));
			item.put("segundos", e.getValue());
			especimenes.add(item);
		}

		Map<String, Object> datos = new LinkedHashMap<>();
		// Viaja para que la pantalla desactive los botones en vez de dejar intentarlo y responder 403.
		// Es comodidad, no seguridad: el permiso se vuelve a comprobar al corregir de verdad.
		datos.put("puedeCorregir", hayVideo && puedeCorregir(a, authentication));
		datos.put("videoUrl", hayVideo ? "/experiments/" + idExperimento + "/analyses/" + idAnalisis + "/video" : null);
		datos.put("videoDisponible", hayVideo);
		datos.put("nombreVideo", a.nombreVideo());      // como lo subió el usuario; null en los videos anteriores
		datos.put("duracion", duracion);
		datos.put("ancho", ancho);
		datos.put("alto", alto);
		datos.put("especimenes", especimenes);
		return datos;
	}

	// ---------------------------------------------------------------------------
	//  POST /video-token y GET /video
	// ---------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> crearTokenVideo(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		Datos a = cargar(idExperimento, idAnalisis);
		// Fíjate en que VER el video exige el mismo permiso que corregir, más estricto que ver la pantalla
		// de revisión (que puede cualquiera con sesión, por R-07). El video es el material en bruto.
		if (!puedeCorregir(a, authentication)) {
			throw new ErrorApi(HttpStatus.FORBIDDEN, "Solo quien subió el video o un Administrador puede verlo.");
		}
		exigirCompletado(a);
		if (!videoDisponible(a, idAnalisis)) {
			throw new ErrorApi(HttpStatus.CONFLICT, "El video ya no está disponible.");
		}

		// Un JWT con la misma llave que la sesión, pero de tipo "video" y de UN análisis: el decodificador de la
		// sesión lo rechaza (exige tipo = "sesion"), así que no sirve como sesión. Va en la URL del <video>
		// (?vt=) porque esa etiqueta no puede mandar la cabecera Authorization.
		//
		// Es la solución al problema que describe RevisionController: el navegador descarga el video por su
		// cuenta y no hay forma de ponerle una cabecera. Como el token acaba en la URL (y por tanto en los
		// registros del servidor), se le recortan los poderes al máximo: un solo análisis, solo lectura y
		// dos horas.
		Instant ahora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(authentication.getName())
				.claim("tipo", "video")
				// El claim que lo ata a ESTE análisis: sin él, un token valdría para ver cualquier video.
				.claim("analisis", idAnalisis)
				.issuedAt(ahora)
				.expiresAt(ahora.plus(Duration.ofMinutes(minutosDelToken)))
				.build();
		JwsHeader encabezado = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(encabezado, claims)).getTokenValue();

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("token", token);
		return datos;
	}

	@Override
	@Transactional(readOnly = true)
	public Path videoPorToken(Integer idExperimento, Integer idAnalisis, String token) {
		// Esta ruta no pide sesión (SeguridadConfig): la autorización es este token, y cualquier falla es 401.
		// Fíjate en que el método NO recibe Authentication: no hay nadie identificado, solo el token.
		if (token == null || token.isBlank()) {
			throw sesionNoValida();
		}
		Jwt jwt;
		try {
			// decode comprueba a la vez la firma, la vigencia y el claim tipo = "video".
			jwt = decodificadorDeVideo.decode(token);
		} catch (JwtException e) {
			throw sesionNoValida();
		}
		Object delToken = jwt.getClaims().get("analisis");
		// La segunda mitad de la autorización: que el token sea de ESTE análisis y no de otro cualquiera.
		// instanceof Number cubre que el claim pueda llegar como Integer o Long.
		if (!(delToken instanceof Number numero) || numero.intValue() != idAnalisis) {
			throw sesionNoValida();
		}

		Datos a = cargar(idExperimento, idAnalisis);
		// web.mp4 y no el archivo original: el worker deja una copia que el navegador sabe reproducir.
		Path video = carpeta(idAnalisis).resolve("web.mp4").normalize();
		// Las tres condiciones: el video no se ha borrado por antigüedad (archivo != null), la ruta no se
		// sale del volumen, y el archivo existe de verdad.
		if (a.archivo() == null || !video.startsWith(raiz) || !Files.isRegularFile(video)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El video ya no existe.");
		}
		return video;
	}

	// ---------------------------------------------------------------------------
	//  PUT /segundos
	// ---------------------------------------------------------------------------

	// Un cambio ya validado: el segundo de un espécimen con su conducta nueva (puede ser null) y su origen.
	// Separar "lo que mandó el cliente" de "lo que ya está comprobado" es lo que permite validar TODO
	// antes de escribir nada.
	private record Cambio(int idObservacion, int segundo, String conducta, String origen) {
	}

	@Override
	@Transactional
	public Map<String, Object> corregir(Integer idExperimento, Integer idAnalisis, Map<String, Object> cuerpo,
			Authentication authentication) {
		// Primer paso de la transacción: el candado del experimento (orden de candados, ver ReporteAlmacen).
		// Impide que una descarga de reporte lea datos a medio corregir y los guarde como buenos.
		reportes.bloquearExperimento(idExperimento);
		Datos a = cargar(idExperimento, idAnalisis);
		if (!puedeCorregir(a, authentication)) {
			throw new ErrorApi(HttpStatus.FORBIDDEN,
					"Solo quien subió el video o un Administrador puede corregir este análisis.");
		}
		exigirCompletado(a);
		// Sin video no hay cómo comprobar nada: una corrección a ciegas no sería una revisión.
		if (a.archivo() == null) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Sin el video no se puede corregir.");
		}

		// TODO el cuerpo se valida antes de escribir el primer UPDATE.
		List<Cambio> cambios = validar(cuerpo, a);

		// Todo o nada: si algo falla después de empezar, la transacción deshace lo escrito. Se toca SEGUNDO y, al final,
		// el nivel del análisis (abajo): el estado y la etapa los escribe el worker, no el backend (4.7).
		for (Cambio c : cambios) {
			int filas = jdbc.update("UPDATE segundo SET conducta = ?, origen = ? WHERE idobservacion = ? AND segundo = ?",
					c.conducta(), c.origen(), c.idObservacion(), c.segundo());
			// 0 filas significa que ese segundo no existe. Se lanza, y la transacción deshace los
			// anteriores: nunca queda una corrección a medias.
			if (filas == 0) {
				throw malaPeticion("El segundo " + c.segundo() + " no existe en este análisis.");
			}
		}
		// El nivel sigue a los segundos: 'agrupado' si queda alguno como «conducta activa» (del worker o de la revisión) y
		// 'preciso' si no. Lo fijó el worker al completar; una corrección puede quitar o poner esas conductas.
		//
		// Es coherencia del dato: el nivel describe el detalle con el que quedó clasificado el análisis
		// (ver NivelClasifAnalisis), y una corrección puede cambiarlo en los dos sentidos. Si no se
		// recalculara, un análisis podría decir 'agrupado' sin que quedara ni una conducta agrupada, y los
		// avisos de "mezcla niveles" serían falsos.
		// Todo en un solo UPDATE con un CASE y un EXISTS: una sola consulta en vez de leer y decidir aquí.
		jdbc.update("""
				UPDATE analisis SET nivelclasif = (CASE WHEN EXISTS (
				    SELECT 1 FROM segundo s JOIN observacion o ON o.idobservacion = s.idobservacion
				    WHERE o.idvideo = ? AND s.conducta = 'conducta activa') THEN 'agrupado' ELSE 'preciso' END)::nivel_clasif_analisis
				WHERE idanalisis = ?
				""", a.idVideo(), idAnalisis);
		// Las correcciones cambian los resultados: el reporte guardado de cualquier formato (filas y carpeta reportes/{id}/) ya no
		// los refleja. Se regenera en la próxima descarga, de cualquier formato.
		// Es el segundo motivo de invalidación de los cuatro que enumera ReporteAlmacen.
		reportes.invalidar(idExperimento);

		// Los totales nuevos, de los especímenes de ESTA tanda, con la misma forma que /results.
		// Devolverlos ahorra una segunda petición y, sobre todo, evita que la pantalla intente
		// recalcularlos por su cuenta y acabe enseñando algo distinto de lo que dirá el reporte.
		String tanda = a.etiqueta() + " · " + letra(a.ordinal());
		List<?> todos = (List<?>) resultadosService.resultados(idExperimento).get("especimenes");
		List<Object> delAnalisis = new ArrayList<>();
		// Se filtran por el texto de la tanda, que es el mismo que arma revision() más arriba.
		for (Object e : todos) {
			if (tanda.equals(((Map<?, ?>) e).get("tanda"))) {
				delAnalisis.add(e);
			}
		}
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("especimenes", delAnalisis);
		return datos;
	}

	// Revisa TODO el cuerpo antes de escribir: especimenes no vacío, cada numeroCilindro existente en el análisis,
	// cada segundo con una conducta del dominio (o null) y un origen de los que escribe una persona.
	//
	// Validar primero y escribir después es lo que hace que un cuerpo con un error en la última fila no
	// deje las anteriores aplicadas. La transacción también lo cubriría, pero así ni se empieza.
	private List<Cambio> validar(Map<String, Object> cuerpo, Datos a) {
		Object lista = cuerpo == null ? null : cuerpo.get("especimenes");
		// instanceof con patrón: comprueba el tipo y declara la variable de una vez. El ! atrapa los tres
		// casos malos juntos: sin cuerpo, sin la clave, o con algo que no es una lista.
		if (!(lista instanceof List<?> especimenes) || especimenes.isEmpty()) {
			throw malaPeticion("Falta la lista de especímenes con los segundos que cambiaron.");
		}
		List<Cambio> cambios = new ArrayList<>();
		for (Object elemento : especimenes) {
			if (!(elemento instanceof Map<?, ?> espec) || !(espec.get("numeroCilindro") instanceof Number cilindro)) {
				throw malaPeticion("Cada espécimen necesita su numeroCilindro.");
			}
			// La observación de ese espécimen en ESTE video. Las tres condiciones del WHERE son la
			// comprobación de seguridad: el cilindro tiene que ser de la tanda de este análisis, no de
			// otro cualquiera que el cliente quisiera tocar.
			List<Integer> observaciones = jdbc.queryForList("""
					SELECT o.idobservacion
					FROM observacion o
					JOIN especimen e ON e.idespecimen = o.idespecimen
					WHERE o.idvideo = ? AND e.idtanda = ? AND e.numerocilindro = ?
					""", Integer.class, a.idVideo(), a.idTanda(), cilindro.intValue());
			if (observaciones.isEmpty()) {
				throw malaPeticion("El espécimen " + cilindro.intValue() + " no existe en este análisis.");
			}
			if (!(espec.get("segundos") instanceof List<?> segundos) || segundos.isEmpty()) {
				throw malaPeticion("El espécimen " + cilindro.intValue() + " no trae segundos.");
			}
			for (Object s : segundos) {
				if (!(s instanceof Map<?, ?> segundo) || !(segundo.get("segundo") instanceof Number numero)) {
					throw malaPeticion("Cada segundo necesita su número.");
				}
				Object conducta = segundo.get("conducta");
				// El null SÍ se acepta: es "no se ve al espécimen" o "quedó dudoso", un valor legítimo
				// (ver la entidad Segundo). Por eso la comprobación solo corre si no es null.
				if (conducta != null && !CONDUCTAS.contains(conducta)) {
					throw malaPeticion("La conducta '" + conducta + "' no existe.");
				}
				Object origen = segundo.get("origen");
				// Aquí NO se admite null, y además la lista excluye 'maquina': por esta vía nadie puede
				// hacer pasar una corrección humana por una medición automática.
				if (!ORIGENES.contains(origen)) {
					throw malaPeticion("El origen '" + origen + "' no es válido: usa confirmada, corregida o dudosa.");
				}
				cambios.add(new Cambio(observaciones.get(0), numero.intValue(), (String) conducta, (String) origen));
			}
		}
		return cambios;
	}

	// ---------------------------------------------------------------------------
	//  POST /restart (R-04)
	// ---------------------------------------------------------------------------

	// Lo que deja una corrida anterior y haría que la nueva arrancara sucia: la pausa y su foto (y el .tmp de una escritura
	// cortada), las correcciones y rachas aceptadas que el worker lee al retomar, y trabajo/ (CSV del pipeline: el worker daría
	// por buenos los viejos si el pipeline fallara sin escribir). NO se tocan el video, web.mp4 ni cajas.json (el worker los
	// reescribe al completar), ni progreso.json ni tiempos.json: el worker los reescribe desde cero apenas toma el análisis.
	// Se borran DENTRO de la transacción, no después del commit: hasta el commit el análisis no es visible como 'en cola' para el
	// worker (su SELECT ... FOR UPDATE SKIP LOCKED no ve el cambio), así que no puede empezar a escribir en la carpeta mientras
	// se borra. Si el commit fallara, solo se perdería trabajo/ de un análisis completado, que únicamente sirve para revisarlo a mano.
	//
	// Este comentario es de los más valiosos del backend, porque razona lo que NO se borra y por qué, y
	// además justifica el momento exacto del borrado.
	// La clave del tercer párrafo: aquí se hace al revés que en ReporteAlmacen (que borra DESPUÉS del
	// commit). El motivo es que el riesgo es el contrario. Allí el peligro era borrar archivos de un
	// cambio que luego se deshace; aquí el peligro es que el worker empiece a escribir en la carpeta
	// mientras se vacía. Como el worker no ve el análisis hasta el commit, borrar antes es seguro.
	// Y se admite abiertamente el caso malo (si el commit falla se pierde trabajo/), con el argumento de
	// por qué es aceptable. Eso es mejor que fingir que no existe.
	private static final List<String> ARCHIVOS_DE_LA_CORRIDA = List.of("pausa.json", "pausa.json.tmp", "pausa.jpg",
			"correcciones.json", "correcciones.json.tmp", "rachas_aceptadas.json", "rachas_aceptadas.json.tmp");

	@Override
	@Transactional
	public Map<String, Object> reiniciar(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		// Primer paso de la transacción: el candado del experimento (orden de candados, ver ReporteAlmacen). Dos reinicios a la
		// vez se esperan aquí, y el segundo ya encuentra el análisis en cola.
		reportes.bloquearExperimento(idExperimento);
		Datos a = cargar(idExperimento, idAnalisis);
		if (!puedeCorregir(a, authentication)) {
			throw new ErrorApi(HttpStatus.FORBIDDEN,
					"Solo quien subió el video o un Administrador puede reiniciar este análisis.");
		}
		// El mensaje nombra el estado actual, que ayuda a entender por qué no se puede.
		if (!EstadoAnalisis.COMPLETADO.es(a.estado())) {
			throw new ErrorApi(HttpStatus.CONFLICT,
					"Solo se puede reiniciar un análisis completado; este está " + a.estado() + ".");
		}
		// Sin el video no hay nada que volver a analizar.
		if (a.archivo() == null) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Sin el video no se puede reiniciar el análisis.");
		}

		// De completado a en cola con un UPDATE condicional, con los valores de arranque de la subida de un video (los DEFAULT de
		// la tabla): etapa y nivel de arranque y rutadiagnostico nula. fechaAnalisis = ahora: entra al final de la cola.
		//
		// "etapa = DEFAULT" deja que la base ponga su valor por omisión, el mismo que al encolar un video
		// nuevo: así los valores de arranque están definidos en un solo sitio, el esquema.
		// Fíjate en la diferencia con la pausa: allí fechaAnalisis se CONSERVA para no perder el turno;
		// aquí se pone a ahora, porque es trabajo nuevo y no debe adelantarse a lo que ya esperaba.
		// El "AND estado = 'completado'" del WHERE es la escritura condicional de siempre: resuelve de un
		// golpe la carrera entre dos reinicios simultáneos.
		int cambios = jdbc.update("UPDATE analisis SET estado = ?::estado_analisis, etapa = DEFAULT, nivelclasif = DEFAULT, "
				+ "rutadiagnostico = NULL, fechaanalisis = now() WHERE idanalisis = ? AND estado = ?::estado_analisis",
				EstadoAnalisis.EN_COLA.valor(), idAnalisis, EstadoAnalisis.COMPLETADO.valor());
		if (cambios == 0) {
			throw new ErrorApi(HttpStatus.CONFLICT, "El análisis ya no está completado.");
		}
		// El resultado anterior, con la revisión humana. El video no se toca.
		//
		// Aquí se pierden las correcciones humanas, que pueden ser horas de trabajo. No hay deshacer, y
		// por eso la operación exige permiso y la pantalla pregunta antes.
		// Los segundos van primero y las observaciones después: al revés, la llave foránea lo impediría.
		jdbc.update("DELETE FROM segundo WHERE idobservacion IN (SELECT idobservacion FROM observacion WHERE idvideo = ?)",
				a.idVideo());
		jdbc.update("DELETE FROM observacion WHERE idvideo = ?", a.idVideo());
		// Los resultados desaparecen: el reporte guardado (filas y carpeta reportes/{id}/) ya no vale.
		reportes.invalidar(idExperimento);

		// Último paso: los archivos de la corrida anterior (ver ARCHIVOS_DE_LA_CORRIDA por qué va antes del commit).
		limpiarCarpeta(carpeta(idAnalisis));

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("idAnalisis", idAnalisis);
		datos.put("estado", EstadoAnalisis.EN_COLA.valor());
		return datos;
	}

	// Un fallo al borrar solo se registra (no deshace el reinicio): el worker reescribe lo que usa.
	private void limpiarCarpeta(Path carpeta) {
		// Los archivos sueltos, uno a uno. deleteIfExists no se queja si no están, que es lo normal: casi
		// ningún análisis tiene los siete.
		for (String nombre : ARCHIVOS_DE_LA_CORRIDA) {
			try {
				Files.deleteIfExists(carpeta.resolve(nombre));
			} catch (IOException e) {
				log.warn("No se pudo borrar {}: {}", carpeta.resolve(nombre), e.getMessage());
			}
		}
		Path trabajo = carpeta.resolve("trabajo");
		if (!Files.isDirectory(trabajo)) {
			return;
		}
		// try-con-recursos: Files.walk abre descriptores del sistema y hay que cerrarlos.
		try (var rutas = Files.walk(trabajo)) {
			// reverseOrder es el truco para borrar una carpeta con contenido: ordenadas al revés, las
			// rutas más profundas van primero, así que cada carpeta ya está vacía cuando le toca.
			// Es la alternativa corta al visitante de archivos que usa ReporteAlmacen.
			for (Path ruta : rutas.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.delete(ruta);
			}
		} catch (IOException e) {
			log.warn("No se pudo borrar {}: {}", trabajo, e.getMessage());
		}
	}

	// ---------------------------------------------------------------------------

	// El análisis con su tanda y el creador del experimento. 404 si el experimento no existe o el análisis es de
	// otro experimento.
	//
	// Una sola carga al principio de cada método, en vez de consultas sueltas repartidas: así los cinco
	// métodos dan exactamente los mismos 404 ante las mismas situaciones.
	private Datos cargar(Integer idExperimento, Integer idAnalisis) {
		// Se comprueba el experimento aparte para poder distinguir los dos mensajes. Sin esto, pedir un
		// experimento inexistente diría "el análisis no existe", que despista.
		Integer hay = jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class,
				idExperimento);
		if (hay == null || hay == 0) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + idExperimento + " no existe");
		}
		// Los cuatro JOIN suben del análisis al experimento, y el WHERE exige las DOS condiciones: que el
		// análisis exista Y que sea de ese experimento. Sin la segunda, cambiando el número de la ruta se
		// podría husmear en los análisis de otro.
		List<Datos> filas = jdbc.query("""
				SELECT a.estado, v.archivo, g.etiqueta, t.ordinal, v.idvideo, t.idtanda, x.idinstitucional, v.nombreoriginal
				FROM analisis a
				JOIN video v       ON v.idvideo = a.idvideo
				JOIN tanda t       ON t.idtanda = v.idtanda
				JOIN grupo g       ON g.idgrupo = t.idgrupo
				JOIN experimento x ON x.idexperimento = g.idexperimento
				WHERE a.idanalisis = ? AND g.idexperimento = ?
				""", (rs, fila) -> new Datos(rs.getString("estado"), rs.getString("archivo"), rs.getString("etiqueta"),
				rs.getInt("ordinal"), rs.getInt("idvideo"), rs.getInt("idtanda"), rs.getString("idinstitucional"),
				rs.getString("nombreoriginal")),
				idAnalisis, idExperimento);
		if (filas.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND,
					"El análisis " + idAnalisis + " no existe en el experimento " + idExperimento);
		}
		// Solo puede haber una: idanalisis es la llave primaria.
		return filas.get(0);
	}

	// Revisar solo tiene sentido sobre un análisis terminado: en uno a medias los segundos están
	// incompletos y el worker todavía los está escribiendo.
	private static void exigirCompletado(Datos a) {
		if (!EstadoAnalisis.COMPLETADO.es(a.estado())) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Solo se puede revisar un análisis completado.");
		}
	}

	// D-57: corrige quien subió el video (por ahora, el creador del experimento) o un Administrador. Los permisos
	// ya vienen en el Authentication (ConvertidorJwt), como en ExperimentoServiceImpl.eliminar.
	//
	// "Ya vienen en el Authentication" significa que no hay que consultar la base: ConvertidorJwt lo leyó
	// al empezar la petición, así que el rol está al día aunque el token sea de hace días.
	private static boolean puedeCorregir(Datos a, Authentication authentication) {
		boolean esAdministrador = authentication.getAuthorities().stream()
				.anyMatch(permiso -> permiso.getAuthority().equals("ROLE_ADMINISTRADOR"));
		return esAdministrador || a.creador().equals(authentication.getName());
	}

	// El video se borra poniendo VIDEO.archivo en nulo; además hace falta la copia web.mp4 que deja el worker.
	//
	// Se comprueban las dos cosas porque pueden no coincidir: la fila puede decir que el video está y el
	// web.mp4 no haberse llegado a escribir, o al revés. Para revisar hacen falta las dos.
	private boolean videoDisponible(Datos a, Integer idAnalisis) {
		return a.archivo() != null && Files.isRegularFile(carpeta(idAnalisis).resolve("web.mp4"));
	}

	// La carpeta del análisis dentro del volumen: analisis/{idAnalisis}/. Armada solo con el id.
	private Path carpeta(Integer idAnalisis) {
		return raiz.resolve("analisis").resolve(String.valueOf(idAnalisis));
	}

	// Los dos errores que se repiten, en métodos cortos para no escribir el mismo texto en cinco sitios.
	// El mensaje del 401 es idéntico al de SeguridadConfig: el cliente recibe siempre lo mismo venga de
	// donde venga el rechazo.
	private static ErrorApi sesionNoValida() {
		return new ErrorApi(HttpStatus.UNAUTHORIZED, "Sesión no válida o vencida");
	}

	private static ErrorApi malaPeticion(String mensaje) {
		return new ErrorApi(HttpStatus.BAD_REQUEST, mensaje);
	}

	// 1 = A, 2 = B, ...
	// Aritmética de caracteres: 'A' + 0 es 'A', 'A' + 1 es 'B'. El -1 ajusta porque los ordinales empiezan
	// en 1. Está repetido en varios servicios por ser una línea; centralizarlo costaría más de lo que vale.
	private static String letra(int ordinal) {
		return String.valueOf((char) ('A' + ordinal - 1));
	}

}
