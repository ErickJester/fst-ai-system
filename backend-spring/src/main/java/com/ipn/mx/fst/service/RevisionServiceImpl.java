package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.valores.EstadoAnalisis;
import com.ipn.mx.fst.domain.valores.OrigenSegundo;
import com.ipn.mx.fst.domain.valores.TipoConducta;
import com.ipn.mx.fst.utilerias.ErrorApi;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Revisión segundo a segundo de un análisis completado. Las consultas son explícitas con JdbcTemplate, como en
// ResultadosServiceImpl, y los totales de cada espécimen se piden a ResultadosService para que tengan
// exactamente la misma forma que /results.
@Service
public class RevisionServiceImpl implements RevisionService {

	private static final Logger log = LoggerFactory.getLogger(RevisionServiceImpl.class);

	@Autowired
	private ReporteAlmacen reportes;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	// Dominio tipo_conducta de la base, tomado del enum. La conducta nula también es válida (no se ve o no se decidió).
	private static final List<String> CONDUCTAS = Arrays.stream(TipoConducta.values()).map(TipoConducta::valor).toList();

	// Dominio origen_segundo menos 'maquina': lo que escribe una persona al revisar.
	private static final List<String> ORIGENES = Arrays.stream(OrigenSegundo.values())
			.filter(origen -> origen != OrigenSegundo.MAQUINA).map(OrigenSegundo::valor).toList();

	// Tamaño del video si falta cajas.json: el de la pausa de ejemplo de la guía.
	private static final int ANCHO_POR_OMISION = 1920;
	private static final int ALTO_POR_OMISION = 1080;

	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	// Vigencia del token del video (VIDEO_TOKEN_MINUTES): lo que dura una revisión larga sin pedir otro.
	@Value("${app.video.minutos}")
	private long minutosDelToken;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private SecretKey llaveJwt;

	@Autowired
	private ResultadosService resultadosService;

	private Path raiz;

	// Un decodificador APARTE del de la sesión (misma llave, otro validador): acepta solo tipo = "video". No es
	// un bean porque Spring Security toma el JwtDecoder de la sesión por tipo y dos beans lo harían ambiguo.
	private JwtDecoder decodificadorDeVideo;

	@PostConstruct
	void iniciar() {
		raiz = Path.of(raizConfigurada).toAbsolutePath().normalize();
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(llaveJwt)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefault(),
				new JwtClaimValidator<String>("tipo", "video"::equals)));
		decodificadorDeVideo = decodificador;
	}

	// Un análisis con la tanda y el creador del experimento (que por ahora es quien subió los videos).
	private record Datos(String estado, String archivo, String etiqueta, int ordinal, int idVideo, int idTanda,
			String creador, String nombreVideo) {
	}

	// ---------------------------------------------------------------------------
	//  GET /review
	// ---------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> revision(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		Datos a = cargar(idExperimento, idAnalisis);
		exigirCompletado(a);

		boolean hayVideo = videoDisponible(a, idAnalisis);

		// Todos los segundos de cada espécimen de la tanda, por cilindro y por segundo.
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
			s.put("conducta", rs.getString("conducta"));
			s.put("origen", rs.getString("origen"));
			return s;
		}, a.idVideo(), a.idTanda());
		for (Map<String, Object> s : filas) {
			int cilindro = (Integer) s.remove("cilindro");
			duracion = Math.max(duracion, (Integer) s.get("segundo"));
			porCilindro.computeIfAbsent(cilindro, k -> new ArrayList<>()).add(s);
		}

		// cajas.json (lo escribe el worker): el recuadro de cada tubo en píxeles del video, y el tamaño del video.
		Map<Integer, Map<String, Object>> recuadros = new LinkedHashMap<>();
		int ancho = ANCHO_POR_OMISION;
		int alto = ALTO_POR_OMISION;
		Path cajas = carpeta(idAnalisis).resolve("cajas.json");
		try {
			JsonNode raizJson = JSON.readTree(Files.readString(cajas));
			ancho = raizJson.path("ancho").asInt(ANCHO_POR_OMISION);
			alto = raizJson.path("alto").asInt(ALTO_POR_OMISION);
			for (JsonNode r : raizJson.path("recuadros")) {
				Map<String, Object> recuadro = new LinkedHashMap<>();
				for (String lado : List.of("x0", "y0", "x1", "y1")) {
					recuadro.put(lado, r.path(lado).asInt());
				}
				recuadros.put(r.path("cilindro").asInt(), recuadro);
			}
		} catch (IOException | JacksonException e) {
			log.warn("El análisis {} no tiene un cajas.json legible ({}): los recuadros quedan vacíos y el video se "
					+ "supone de {}x{}", idAnalisis, e.getMessage(), ANCHO_POR_OMISION, ALTO_POR_OMISION);
		}

		List<Map<String, Object>> especimenes = new ArrayList<>();
		for (Map.Entry<Integer, List<Map<String, Object>>> e : porCilindro.entrySet()) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("numeroCilindro", e.getKey());
			item.put("tanda", a.etiqueta() + " · " + letra(a.ordinal()));
			item.put("recuadro", recuadros.get(e.getKey()));
			item.put("segundos", e.getValue());
			especimenes.add(item);
		}

		Map<String, Object> datos = new LinkedHashMap<>();
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
		Instant ahora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(authentication.getName())
				.claim("tipo", "video")
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
		if (token == null || token.isBlank()) {
			throw sesionNoValida();
		}
		Jwt jwt;
		try {
			jwt = decodificadorDeVideo.decode(token);
		} catch (JwtException e) {
			throw sesionNoValida();
		}
		Object delToken = jwt.getClaims().get("analisis");
		if (!(delToken instanceof Number numero) || numero.intValue() != idAnalisis) {
			throw sesionNoValida();
		}

		Datos a = cargar(idExperimento, idAnalisis);
		Path video = carpeta(idAnalisis).resolve("web.mp4").normalize();
		if (a.archivo() == null || !video.startsWith(raiz) || !Files.isRegularFile(video)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El video ya no existe.");
		}
		return video;
	}

	// ---------------------------------------------------------------------------
	//  PUT /segundos
	// ---------------------------------------------------------------------------

	// Un cambio ya validado: el segundo de un espécimen con su conducta nueva (puede ser null) y su origen.
	private record Cambio(int idObservacion, int segundo, String conducta, String origen) {
	}

	@Override
	@Transactional
	public Map<String, Object> corregir(Integer idExperimento, Integer idAnalisis, Map<String, Object> cuerpo,
			Authentication authentication) {
		// Primer paso de la transacción: el candado del experimento (orden de candados, ver ReporteAlmacen).
		reportes.bloquearExperimento(idExperimento);
		Datos a = cargar(idExperimento, idAnalisis);
		if (!puedeCorregir(a, authentication)) {
			throw new ErrorApi(HttpStatus.FORBIDDEN,
					"Solo quien subió el video o un Administrador puede corregir este análisis.");
		}
		exigirCompletado(a);
		if (a.archivo() == null) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Sin el video no se puede corregir.");
		}

		List<Cambio> cambios = validar(cuerpo, a);

		// Todo o nada: si algo falla después de empezar, la transacción deshace lo escrito. Solo se toca SEGUNDO:
		// el estado, la etapa y el nivel del análisis los escribe el worker, no el backend (4.7).
		for (Cambio c : cambios) {
			int filas = jdbc.update("UPDATE segundo SET conducta = ?, origen = ? WHERE idobservacion = ? AND segundo = ?",
					c.conducta(), c.origen(), c.idObservacion(), c.segundo());
			if (filas == 0) {
				throw malaPeticion("El segundo " + c.segundo() + " no existe en este análisis.");
			}
		}
		// Las correcciones cambian los resultados: el reporte guardado (filas y carpeta reportes/{id}/) ya no los refleja.
		// Se regenera en la próxima descarga del PDF.
		reportes.invalidar(idExperimento);

		// Los totales nuevos, de los especímenes de ESTA tanda, con la misma forma que /results.
		String tanda = a.etiqueta() + " · " + letra(a.ordinal());
		List<?> todos = (List<?>) resultadosService.resultados(idExperimento).get("especimenes");
		List<Object> delAnalisis = new ArrayList<>();
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
	private List<Cambio> validar(Map<String, Object> cuerpo, Datos a) {
		Object lista = cuerpo == null ? null : cuerpo.get("especimenes");
		if (!(lista instanceof List<?> especimenes) || especimenes.isEmpty()) {
			throw malaPeticion("Falta la lista de especímenes con los segundos que cambiaron.");
		}
		List<Cambio> cambios = new ArrayList<>();
		for (Object elemento : especimenes) {
			if (!(elemento instanceof Map<?, ?> espec) || !(espec.get("numeroCilindro") instanceof Number cilindro)) {
				throw malaPeticion("Cada espécimen necesita su numeroCilindro.");
			}
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
				if (conducta != null && !CONDUCTAS.contains(conducta)) {
					throw malaPeticion("La conducta '" + conducta + "' no existe.");
				}
				Object origen = segundo.get("origen");
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
		if (!EstadoAnalisis.COMPLETADO.es(a.estado())) {
			throw new ErrorApi(HttpStatus.CONFLICT,
					"Solo se puede reiniciar un análisis completado; este está " + a.estado() + ".");
		}
		if (a.archivo() == null) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Sin el video no se puede reiniciar el análisis.");
		}

		// De completado a en cola con un UPDATE condicional, con los valores de arranque de la subida de un video (los DEFAULT de
		// la tabla): etapa y nivel de arranque y rutadiagnostico nula. fechaAnalisis = ahora: entra al final de la cola.
		int cambios = jdbc.update("UPDATE analisis SET estado = ?::estado_analisis, etapa = DEFAULT, nivelclasif = DEFAULT, "
				+ "rutadiagnostico = NULL, fechaanalisis = now() WHERE idanalisis = ? AND estado = ?::estado_analisis",
				EstadoAnalisis.EN_COLA.valor(), idAnalisis, EstadoAnalisis.COMPLETADO.valor());
		if (cambios == 0) {
			throw new ErrorApi(HttpStatus.CONFLICT, "El análisis ya no está completado.");
		}
		// El resultado anterior, con la revisión humana. El video no se toca.
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
		try (var rutas = Files.walk(trabajo)) {
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
	private Datos cargar(Integer idExperimento, Integer idAnalisis) {
		Integer hay = jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class,
				idExperimento);
		if (hay == null || hay == 0) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + idExperimento + " no existe");
		}
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
		return filas.get(0);
	}

	private static void exigirCompletado(Datos a) {
		if (!EstadoAnalisis.COMPLETADO.es(a.estado())) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Solo se puede revisar un análisis completado.");
		}
	}

	// D-57: corrige quien subió el video (por ahora, el creador del experimento) o un Administrador. Los permisos
	// ya vienen en el Authentication (ConvertidorJwt), como en ExperimentoServiceImpl.eliminar.
	private static boolean puedeCorregir(Datos a, Authentication authentication) {
		boolean esAdministrador = authentication.getAuthorities().stream()
				.anyMatch(permiso -> permiso.getAuthority().equals("ROLE_ADMINISTRADOR"));
		return esAdministrador || a.creador().equals(authentication.getName());
	}

	// El video se borra poniendo VIDEO.archivo en nulo; además hace falta la copia web.mp4 que deja el worker.
	private boolean videoDisponible(Datos a, Integer idAnalisis) {
		return a.archivo() != null && Files.isRegularFile(carpeta(idAnalisis).resolve("web.mp4"));
	}

	private Path carpeta(Integer idAnalisis) {
		return raiz.resolve("analisis").resolve(String.valueOf(idAnalisis));
	}

	private static ErrorApi sesionNoValida() {
		return new ErrorApi(HttpStatus.UNAUTHORIZED, "Sesión no válida o vencida");
	}

	private static ErrorApi malaPeticion(String mensaje) {
		return new ErrorApi(HttpStatus.BAD_REQUEST, mensaje);
	}

	// 1 = A, 2 = B, ...
	private static String letra(int ordinal) {
		return String.valueOf((char) ('A' + ordinal - 1));
	}

}
