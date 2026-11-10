package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Encargo 37, parte B: las reglas de la revisión (§7) que estaban sin probar, contra una PostgreSQL de verdad: POST video-token,
// GET video con ?vt=, PUT segundos y puedeCorregir de GET review. Mismas condiciones que AdministracionYRecuperacionTest: solo
// corre con FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el esquema de db/init (cada prueba borra todos los datos).
//
// Contexto para entenderla: la «revisión» es la pantalla donde el investigador ve el video con la conducta que la máquina
// marcó segundo a segundo y puede corregirla. Un <video> del navegador no puede mandar la cabecera Authorization, por eso
// el video NO se pide con el JWT de sesión sino con un token corto y propio, de tipo «video», en la URL (?vt=). Casi todas
// las pruebas de aquí giran en torno a que ese token abra SOLO su video y a que corregir exija permiso y estado correcto.
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		// Raíz de archivos propia de esta clase (la copia web.mp4 de cada análisis vive aquí).
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-revision"
})
@AutoConfigureMockMvc
class RevisionYVideoTest {

	// Contraseña común de las cuentas de prueba (se guarda cifrada para que el login real funcione).
	private static final String CLAVE = "clave-de-prueba-1";
	// Misma carpeta que app.archivos.raiz de arriba, como Path para crear y borrar los archivos de prueba.
	private static final Path RAIZ = Path.of(System.getProperty("java.io.tmpdir"), "fst-pruebas-revision");
	// Abreviaturas de las tres conductas, para que las listas esperadas de las pruebas se lean cortas.
	private static final String N = "nado activo";
	private static final String I = "inmovilidad";
	private static final String E = "escalamiento";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// Ids de la cadena que arma @BeforeEach; las pruebas los usan para armar rutas y consultar la base.
	private int experimento;
	private int analisis;
	private int video;
	private int observacion;
	// Un segundo análisis del mismo experimento (otra tanda), para probar que el token de uno no abre el otro.
	private int otroAnalisis;

	// Antes de cada prueba: vacía la base y el disco y arma un escenario base con dos análisis completados.
	@BeforeEach
	void base_limpia() throws Exception {
		// De las hojas hacia arriba por las llaves foráneas.
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		borrarRecursivo(RAIZ);
		// Tres cuentas: la 1 creó el experimento y sube el video, la 2 es una cuenta ajena y la 3 es administradora.
		cuenta("1", "creador@fst.mx", false);
		cuenta("2", "otro@fst.mx", false);
		cuenta("3", "admin@fst.mx", true);
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Exp', current_date)");
		experimento = jdbc.queryForObject("SELECT idexperimento FROM experimento", Integer.class);
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, 'Control', 'control', 'agua')", experimento);
		int grupo = jdbc.queryForObject("SELECT idgrupo FROM grupo", Integer.class);

		// Primer análisis (Tanda A): video, análisis completado, un espécimen con cinco segundos de «nado activo»
		// dictados por la máquina, y la copia web.mp4 en disco (contenido de 10 bytes, suficiente para probar Range).
		int tanda1 = tanda(grupo, 1);
		video = video(tanda1);
		analisis = analisis(video, "completado");
		observacion = observacion(tanda1, video);
		for (int s = 1; s <= 5; s++) {
			segundo(observacion, s, N, "maquina");
		}
		archivo(carpeta(analisis).resolve("web.mp4"), "0123456789");

		// Segundo análisis (Tanda B) del mismo experimento, con su propio web.mp4 distinto.
		int tanda2 = tanda(grupo, 2);
		int video2 = video(tanda2);
		otroAnalisis = analisis(video2, "completado");
		archivo(carpeta(otroAnalisis).resolve("web.mp4"), "abcdefghij");
	}

	// ----------------------------------------------------------------- POST video-token
	//
	// Quién puede pedir el token del video y en qué condiciones se niega.

	@Test
	@Tag("trabajo-terminal")
	void el_token_de_video_lo_piden_quien_subio_el_video_y_un_administrador_y_otra_cuenta_recibe_403() throws Exception {
		mvc.perform(pedirToken(login("creador@fst.mx"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString());
		mvc.perform(pedirToken(login("admin@fst.mx"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString());
		// Una cuenta ajena: 403 (autenticada pero sin permiso). Sin sesión: 401 (ni siquiera sabemos quién es).
		mvc.perform(pedirToken(login("otro@fst.mx"))).andExpect(status().isForbidden());
		mvc.perform(post(rutaToken(analisis))).andExpect(status().isUnauthorized());
	}

	@Test
	void el_token_de_video_se_rechaza_si_el_analisis_no_esta_completado() throws Exception {
		String creador = login("creador@fst.mx");
		// Un bucle por los cinco estados distintos de «completado»: en todos, 409 (el video aún no es visible).
		for (String estado : List.of("en cola", "procesando", "en pausa", "error", "cancelado")) {
			jdbc.update("UPDATE analisis SET estado = ?::estado_analisis WHERE idanalisis = ?", estado, analisis);
			mvc.perform(pedirToken(creador)).andExpect(status().isConflict());
		}
	}

	@Test
	void el_token_de_video_se_rechaza_sin_video() throws Exception {
		String creador = login("creador@fst.mx");
		// Borrado (VIDEO.archivo nulo)...
		jdbc.update("UPDATE video SET archivo = NULL WHERE idvideo = ?", video);
		mvc.perform(pedirToken(creador)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("El video ya no está disponible."));
		// ...o sin la copia web.mp4 que deja el worker.
		jdbc.update("UPDATE video SET archivo = 'videos/1.mp4' WHERE idvideo = ?", video);
		Files.delete(carpeta(analisis).resolve("web.mp4"));
		mvc.perform(pedirToken(creador)).andExpect(status().isConflict());
	}

	@Test
	void el_token_de_video_de_un_analisis_que_no_existe_o_es_de_otro_experimento_es_404() throws Exception {
		String creador = login("creador@fst.mx");
		// Análisis inexistente (9999)...
		mvc.perform(post(rutaToken(9999)).header("Authorization", "Bearer " + creador)).andExpect(status().isNotFound());
		// ...o un análisis real pedido bajo un experimento que no es el suyo (9999): también 404, no se revela que existe.
		mvc.perform(post("/experiments/9999/analyses/" + analisis + "/video-token").header("Authorization", "Bearer " + creador))
				.andExpect(status().isNotFound());
	}

	// ----------------------------------------------------------------- GET video con ?vt=
	//
	// El endpoint que sirve el archivo. Se autentica solo con el token de video en la URL.

	@Test
	void el_video_con_su_token_responde_200_y_con_range_206() throws Exception {
		String vt = tokenDeVideo(login("creador@fst.mx"), analisis);

		mvc.perform(get(rutaVideo(analisis)).param("vt", vt)).andExpect(status().isOk());
		// 206 Partial Content: el navegador pide solo un tramo (Range) para poder saltar dentro del video sin bajarlo entero.
		mvc.perform(get(rutaVideo(analisis)).param("vt", vt).header("Range", "bytes=2-5")).andExpect(status().isPartialContent());
	}

	@Test
	void el_token_de_video_de_otro_analisis_se_rechaza() throws Exception {
		String delOtro = tokenDeVideo(login("creador@fst.mx"), otroAnalisis);

		// El token lleva dentro el id del análisis para el que se emitió; con otro análisis no sirve (401)...
		mvc.perform(get(rutaVideo(analisis)).param("vt", delOtro)).andExpect(status().isUnauthorized());
		// ...pero con el suyo sí: el control que demuestra que el token estaba bien y lo rechazado fue la discrepancia.
		mvc.perform(get(rutaVideo(otroAnalisis)).param("vt", delOtro)).andExpect(status().isOk());
	}

	@Test
	@Tag("trabajo-terminal")
	void el_jwt_de_sesion_en_la_url_se_rechaza() throws Exception {
		String sesion = login("creador@fst.mx");

		// El JWT de sesión es la llave de TODA la cuenta; en una URL quedaría en historiales y bitácoras. Por eso esta
		// ruta solo acepta el token de video (de alcance mínimo) y rechaza el de sesión, venga como venga.
		mvc.perform(get(rutaVideo(analisis)).param("vt", sesion)).andExpect(status().isUnauthorized());
		// Ni siquiera con la cabecera: la ruta solo se abre con el token de video.
		mvc.perform(get(rutaVideo(analisis)).header("Authorization", "Bearer " + sesion)).andExpect(status().isUnauthorized());
	}

	@Test
	void sin_token_o_con_un_token_inventado_el_video_es_401() throws Exception {
		mvc.perform(get(rutaVideo(analisis))).andExpect(status().isUnauthorized());
		mvc.perform(get(rutaVideo(analisis)).param("vt", "")).andExpect(status().isUnauthorized());
		mvc.perform(get(rutaVideo(analisis)).param("vt", "no.es.un.token")).andExpect(status().isUnauthorized());
	}

	@Test
	void el_token_de_video_no_sirve_en_otras_rutas() throws Exception {
		String vt = tokenDeVideo(login("creador@fst.mx"), analisis);
		String base = "/experiments/" + experimento;

		// Es la inversa de la prueba anterior: el token de video solo abre el video y nada más. Si pudiera usarse como
		// sesión, quien lo copie de la URL tendría acceso a toda la API.
		// Como sesión (cabecera Authorization): ninguna ruta lo acepta.
		mvc.perform(get(base + "/analyses/" + analisis + "/review").header("Authorization", "Bearer " + vt)).andExpect(status().isUnauthorized());
		mvc.perform(get(base + "/results").header("Authorization", "Bearer " + vt)).andExpect(status().isUnauthorized());
		mvc.perform(post(rutaToken(analisis)).header("Authorization", "Bearer " + vt)).andExpect(status().isUnauthorized());
		mvc.perform(post(base + "/analyses/" + analisis + "/restart").header("Authorization", "Bearer " + vt)).andExpect(status().isUnauthorized());
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + vt)).andExpect(status().isUnauthorized());
		// Como ?vt= en otra ruta: sin sesión, sigue pidiéndola.
		mvc.perform(get(base + "/results").param("vt", vt)).andExpect(status().isUnauthorized());
		mvc.perform(get(base + "/analyses/" + analisis + "/review").param("vt", vt)).andExpect(status().isUnauthorized());
		// Y el intento de reiniciar con ese token no tuvo efecto: el análisis sigue completado.
		assertEquals("completado", estadoDe(analisis));
	}

	@Test
	void si_el_video_ya_se_borro_es_404_aunque_el_token_sea_valido() throws Exception {
		// El token se pidió cuando el video aún existía; después el video se borra (retención de archivos).
		String vt = tokenDeVideo(login("creador@fst.mx"), analisis);

		jdbc.update("UPDATE video SET archivo = NULL WHERE idvideo = ?", video);
		mvc.perform(get(rutaVideo(analisis)).param("vt", vt)).andExpect(status().isNotFound());

		// También si solo falta la copia web.mp4.
		jdbc.update("UPDATE video SET archivo = 'videos/1.mp4' WHERE idvideo = ?", video);
		mvc.perform(get(rutaVideo(analisis)).param("vt", vt)).andExpect(status().isOk());
		Files.delete(carpeta(analisis).resolve("web.mp4"));
		mvc.perform(get(rutaVideo(analisis)).param("vt", vt)).andExpect(status().isNotFound());
	}

	// ----------------------------------------------------------------- PUT segundos
	//
	// La corrección: el investigador cambia la conducta de segundos concretos. Cada segundo corregido lleva su origen
	// (confirmada / corregida / dudosa) para saber de dónde salió el dato.

	@Test
	@Tag("trabajo-terminal")
	void solo_se_guardan_los_segundos_que_vienen_y_el_resto_queda_como_estaba() throws Exception {
		// Se mandan solo los segundos 2 (→ inmovilidad, corregida) y 4 (→ sin ver, dudosa); null = «sin ver».
		mvc.perform(corregir(login("creador@fst.mx"), cuerpo(1, segundo(2, I, "corregida"), segundo(4, null, "dudosa"))))
				.andExpect(status().isOk())
				// La respuesta trae los totales recalculados: 3 de nado (1, 3, 5), 1 de inmovilidad (2); el 4 no cuenta.
				.andExpect(jsonPath("$.data.especimenes[0].totales['nado activo'].segundos").value(3))
				.andExpect(jsonPath("$.data.especimenes[0].totales['inmovilidad'].segundos").value(1));

		// En la base solo cambiaron el 2 y el 4; el 1, 3 y 5 siguen como los dejó la máquina ("-" = conducta nula).
		assertEquals(List.of(N, I, N, "-", N), conductasEnBase());
		assertEquals(List.of("maquina", "corregida", "maquina", "dudosa", "maquina"), origenesEnBase());
	}

	@Test
	void el_origen_solo_puede_ser_confirmada_corregida_o_dudosa() throws Exception {
		String creador = login("creador@fst.mx");
		// Los tres orígenes que puede mandar una persona: todos válidos.
		for (String origen : List.of("confirmada", "corregida", "dudosa")) {
			mvc.perform(corregir(creador, cuerpo(1, segundo(1, N, origen)))).andExpect(status().isOk());
		}
		// «maquina» es del worker (una persona no puede decir que lo dijo la máquina), «humano» no existe y vacío no es nada.
		for (String origen : new String[] {"maquina", "humano", ""}) {
			mvc.perform(corregir(creador, cuerpo(1, segundo(1, I, origen)))).andExpect(status().isBadRequest());
		}
		// Sin origen (null en el mapa: segundo() lo omite del JSON)...
		mvc.perform(corregir(creador, cuerpo(1, segundo(1, I, null)))).andExpect(status().isBadRequest());
		// ...ni escrito a mano un JSON al que le falta el campo origen.
		mvc.perform(corregir(creador, "{\"especimenes\":[{\"numeroCilindro\":1,\"segundos\":[{\"segundo\":1,\"conducta\":\"nado activo\"}]}]}"))
				.andExpect(status().isBadRequest());
		// Los rechazos no escribieron nada: el último valor bueno sigue ahí.
		assertEquals("dudosa", origenesEnBase().get(0));
		assertEquals(N, conductasEnBase().get(0));
	}

	@Test
	void un_cuerpo_con_un_error_no_guarda_ni_los_segundos_buenos() throws Exception {
		String creador = login("creador@fst.mx");
		// La corrección es todo o nada (una sola transacción): un solo dato malo tira el cuerpo entero, incluidos los
		// segundos buenos que iban junto con él. Cuatro maneras de estar mal:
		// una conducta que no existe...
		mvc.perform(corregir(creador, cuerpo(1, segundo(2, I, "corregida"), segundo(3, "nadar", "corregida")))).andExpect(status().isBadRequest());
		// ...un segundo fuera de 1–300...
		mvc.perform(corregir(creador, cuerpo(1, segundo(2, I, "corregida"), segundo(99, I, "corregida")))).andExpect(status().isBadRequest());
		// ...un cilindro que el análisis no tiene (7)...
		mvc.perform(corregir(creador, cuerpo(7, segundo(2, I, "corregida")))).andExpect(status().isBadRequest());
		// ...o un cuerpo vacío.
		mvc.perform(corregir(creador, "{}")).andExpect(status().isBadRequest());

		// Nada cambió: ni el segundo 2, que venía bien.
		assertEquals(List.of(N, N, N, N, N), conductasEnBase());
		assertEquals(List.of("maquina", "maquina", "maquina", "maquina", "maquina"), origenesEnBase());
	}

	@Test
	void el_administrador_puede_corregir_y_otra_cuenta_recibe_403_sin_cambiar_nada() throws Exception {
		mvc.perform(corregir(login("admin@fst.mx"), cuerpo(1, segundo(1, E, "corregida")))).andExpect(status().isOk());
		assertEquals(E, conductasEnBase().get(0));

		// Una cuenta ajena: 403 y el segundo 2 queda como estaba. Sin sesión: 401.
		mvc.perform(corregir(login("otro@fst.mx"), cuerpo(1, segundo(2, I, "corregida")))).andExpect(status().isForbidden());
		mvc.perform(put(rutaSegundos(analisis)).contentType(MediaType.APPLICATION_JSON).content(cuerpo(1, segundo(2, I, "corregida"))))
				.andExpect(status().isUnauthorized());
		assertEquals(N, conductasEnBase().get(1));
	}

	@Test
	void solo_se_corrige_un_analisis_completado() throws Exception {
		String creador = login("creador@fst.mx");
		// Mientras el análisis no está completado, todavía no hay resultados definitivos que corregir (409).
		for (String estado : List.of("en cola", "procesando", "en pausa", "error", "cancelado")) {
			jdbc.update("UPDATE analisis SET estado = ?::estado_analisis WHERE idanalisis = ?", estado, analisis);
			mvc.perform(corregir(creador, cuerpo(1, segundo(2, I, "corregida")))).andExpect(status().isConflict());
		}
		assertEquals(N, conductasEnBase().get(1));
	}

	@Test
	void sin_el_video_no_se_corrige_y_responde_409() throws Exception {
		// Corregir es contrastar con lo que se ve en el video; si el video ya se borró no hay cómo verificarlo.
		jdbc.update("UPDATE video SET archivo = NULL WHERE idvideo = ?", video);

		mvc.perform(corregir(login("creador@fst.mx"), cuerpo(1, segundo(2, I, "corregida")))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Sin el video no se puede corregir."));
		assertEquals(N, conductasEnBase().get(1));
	}

	// ----------------------------------------------------------------- GET review
	//
	// La pantalla de revisión le dice al frontend con puedeCorregir si debe mostrar o no los controles de edición.

	@Test
	void puede_corregir_es_verdadero_para_quien_subio_el_video_y_el_administrador_y_falso_para_otra_cuenta() throws Exception {
		mvc.perform(get(rutaRevision()).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.puedeCorregir").value(true));
		mvc.perform(get(rutaRevision()).header("Authorization", "Bearer " + login("admin@fst.mx")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.puedeCorregir").value(true));
		// Otra cuenta puede ver la revisión (R-07), pero no corregir.
		mvc.perform(get(rutaRevision()).header("Authorization", "Bearer " + login("otro@fst.mx")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.puedeCorregir").value(false));
	}

	@Test
	void sin_el_video_puede_corregir_es_falso_hasta_para_el_creador() throws Exception {
		jdbc.update("UPDATE video SET archivo = NULL WHERE idvideo = ?", video);

		// Se puede VER la revisión (los segundos siguen en la base) pero sin video: ni se puede corregir ni hay videoUrl.
		mvc.perform(get(rutaRevision()).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.puedeCorregir").value(false))
				.andExpect(jsonPath("$.data.videoUrl").doesNotExist());
	}

	// ----------------------------------------------------------------- apoyo
	//
	// Métodos de apoyo: rutas, peticiones y datos de prueba.

	// Las rutas de los endpoints que se prueban, armadas con el experimento del escenario.
	private String rutaToken(int idAnalisis) {
		return "/experiments/" + experimento + "/analyses/" + idAnalisis + "/video-token";
	}

	private String rutaVideo(int idAnalisis) {
		return "/experiments/" + experimento + "/analyses/" + idAnalisis + "/video";
	}

	private String rutaSegundos(int idAnalisis) {
		return "/experiments/" + experimento + "/analyses/" + idAnalisis + "/segundos";
	}

	private String rutaRevision() {
		return "/experiments/" + experimento + "/analyses/" + analisis + "/review";
	}

	// POST del token de video del análisis principal, con la sesión dada.
	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedirToken(String sesion) {
		return post(rutaToken(analisis)).header("Authorization", "Bearer " + sesion);
	}

	// Pide el token de video de verdad (debe dar 200) y devuelve su texto, para usarlo en ?vt=.
	private String tokenDeVideo(String sesion, int idAnalisis) throws Exception {
		MvcResult r = mvc.perform(post(rutaToken(idAnalisis)).header("Authorization", "Bearer " + sesion))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

	// PUT de corrección de segundos del análisis principal, con la sesión y el cuerpo JSON dados.
	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder corregir(String sesion, String cuerpo) {
		return put(rutaSegundos(analisis)).header("Authorization", "Bearer " + sesion).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	// Arma un segundo del cuerpo JSON. Si origen es null, se omite el campo (para probar que es obligatorio).
	private static Map<String, Object> segundo(int numero, String conducta, String origen) {
		// LinkedHashMap para conservar el orden de los campos en el JSON.
		Map<String, Object> s = new java.util.LinkedHashMap<>();
		s.put("segundo", numero);
		s.put("conducta", conducta);
		if (origen != null) {
			s.put("origen", origen);
		}
		return s;
	}

	// Arma el cuerpo JSON de una corrección para UN espécimen (por número de cilindro) con los segundos dados.
	// Se serializa con Jackson 3 (tools.jackson), el mismo que usa la aplicación.
	@SafeVarargs
	private static String cuerpo(int cilindro, Map<String, Object>... segundos) throws Exception {
		return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(
				Map.of("especimenes", List.of(Map.of("numeroCilindro", cilindro, "segundos", List.of(segundos)))));
	}

	// La conducta de cada segundo del espécimen 1 en orden; "-" es nula.
	private List<String> conductasEnBase() {
		return jdbc.queryForList("SELECT COALESCE(conducta::text, '-') FROM segundo WHERE idobservacion = ? ORDER BY segundo",
				String.class, observacion);
	}

	// El origen (maquina / confirmada / corregida / dudosa) de cada segundo, en orden.
	private List<String> origenesEnBase() {
		return jdbc.queryForList("SELECT origen::text FROM segundo WHERE idobservacion = ? ORDER BY segundo", String.class, observacion);
	}

	private String estadoDe(int idAnalisis) {
		return jdbc.queryForObject("SELECT estado::text FROM analisis WHERE idanalisis = ?", String.class, idAnalisis);
	}

	// Crea una cuenta activa; si es administradora, también su fila en la tabla administrador.
	private void cuenta(String id, String correo, boolean administrador) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, true, false)", id, correo, passwordEncoder.encode(CLAVE));
		if (administrador) {
			jdbc.update("INSERT INTO administrador (idinstitucional) VALUES (?)", id);
		}
	}

	private int tanda(int grupo, int ordinal) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, 2)", grupo, ordinal);
		return jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, grupo, ordinal);
	}

	// Un video del Día 2 que todavía tiene archivo (archivo no nulo) y dura 5 s.
	private int video(int tanda) {
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/1.mp4', 5)", tanda);
		return jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
	}

	// ?::estado_analisis convierte el texto al tipo de dominio de la base.
	private int analisis(int video, String estado) {
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, ?::estado_analisis)", video, estado);
		return jdbc.queryForObject("SELECT idanalisis FROM analisis WHERE idvideo = ?", Integer.class, video);
	}

	// Crea el espécimen del cilindro 1 de la tanda y su observación (la que cuelga del video).
	private int observacion(int tanda, int video) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, 1)", tanda);
		int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = 1", Integer.class, tanda);
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
		return jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idespecimen = ?", Integer.class, especimen);
	}

	// Inserta un segundo con su conducta y su origen (con los casts a los tipos de dominio de la base).
	private void segundo(int observacion, int numero, String conducta, String origen) {
		jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, ?, ?::tipo_conducta, ?::origen_segundo)",
				observacion, numero, conducta, origen);
	}

	// Carpeta donde el worker deja los archivos de un análisis: <raíz>/analisis/<id>/.
	private static Path carpeta(int idAnalisis) {
		return RAIZ.resolve("analisis").resolve(String.valueOf(idAnalisis));
	}

	// Escribe un archivo de texto, creando antes las carpetas que falten.
	private static void archivo(Path ruta, String contenido) throws Exception {
		Files.createDirectories(ruta.getParent());
		Files.writeString(ruta, contenido);
	}

	// Inicia sesión por el endpoint real y devuelve el JWT de sesión.
	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		String token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
		assertNotEquals("", token);
		return token;
	}

	// Borra una carpeta con todo lo que contiene (si existe). Orden inverso: primero lo más profundo, porque no se
	// puede borrar una carpeta que aún tiene contenido.
	private static void borrarRecursivo(Path carpeta) throws Exception {
		if (!Files.exists(carpeta)) {
			return;
		}
		try (var rutas = Files.walk(carpeta)) {
			for (Path ruta : rutas.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.delete(ruta);
			}
		}
	}

}
