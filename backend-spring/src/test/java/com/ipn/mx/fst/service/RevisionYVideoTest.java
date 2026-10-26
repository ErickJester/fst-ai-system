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
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-revision"
})
@AutoConfigureMockMvc
class RevisionYVideoTest {

	private static final String CLAVE = "clave-de-prueba-1";
	private static final Path RAIZ = Path.of(System.getProperty("java.io.tmpdir"), "fst-pruebas-revision");
	private static final String N = "nado activo";
	private static final String I = "inmovilidad";
	private static final String E = "escalamiento";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private int experimento;
	private int analisis;
	private int video;
	private int observacion;
	// Un segundo análisis del mismo experimento (otra tanda), para probar que el token de uno no abre el otro.
	private int otroAnalisis;

	@BeforeEach
	void base_limpia() throws Exception {
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		borrarRecursivo(RAIZ);
		cuenta("1", "creador@fst.mx", false);
		cuenta("2", "otro@fst.mx", false);
		cuenta("3", "admin@fst.mx", true);
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Exp', current_date)");
		experimento = jdbc.queryForObject("SELECT idexperimento FROM experimento", Integer.class);
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, 'Control', 'control', 'agua')", experimento);
		int grupo = jdbc.queryForObject("SELECT idgrupo FROM grupo", Integer.class);

		int tanda1 = tanda(grupo, 1);
		video = video(tanda1);
		analisis = analisis(video, "completado");
		observacion = observacion(tanda1, video);
		for (int s = 1; s <= 5; s++) {
			segundo(observacion, s, N, "maquina");
		}
		archivo(carpeta(analisis).resolve("web.mp4"), "0123456789");

		int tanda2 = tanda(grupo, 2);
		int video2 = video(tanda2);
		otroAnalisis = analisis(video2, "completado");
		archivo(carpeta(otroAnalisis).resolve("web.mp4"), "abcdefghij");
	}

	// ----------------------------------------------------------------- POST video-token

	@Test
	void el_token_de_video_lo_piden_quien_subio_el_video_y_un_administrador_y_otra_cuenta_recibe_403() throws Exception {
		mvc.perform(pedirToken(login("creador@fst.mx"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString());
		mvc.perform(pedirToken(login("admin@fst.mx"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString());
		mvc.perform(pedirToken(login("otro@fst.mx"))).andExpect(status().isForbidden());
		mvc.perform(post(rutaToken(analisis))).andExpect(status().isUnauthorized());
	}

	@Test
	void el_token_de_video_se_rechaza_si_el_analisis_no_esta_completado() throws Exception {
		String creador = login("creador@fst.mx");
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
		mvc.perform(post(rutaToken(9999)).header("Authorization", "Bearer " + creador)).andExpect(status().isNotFound());
		mvc.perform(post("/experiments/9999/analyses/" + analisis + "/video-token").header("Authorization", "Bearer " + creador))
				.andExpect(status().isNotFound());
	}

	// ----------------------------------------------------------------- GET video con ?vt=

	@Test
	void el_video_con_su_token_responde_200_y_con_range_206() throws Exception {
		String vt = tokenDeVideo(login("creador@fst.mx"), analisis);

		mvc.perform(get(rutaVideo(analisis)).param("vt", vt)).andExpect(status().isOk());
		mvc.perform(get(rutaVideo(analisis)).param("vt", vt).header("Range", "bytes=2-5")).andExpect(status().isPartialContent());
	}

	@Test
	void el_token_de_video_de_otro_analisis_se_rechaza() throws Exception {
		String delOtro = tokenDeVideo(login("creador@fst.mx"), otroAnalisis);

		mvc.perform(get(rutaVideo(analisis)).param("vt", delOtro)).andExpect(status().isUnauthorized());
		mvc.perform(get(rutaVideo(otroAnalisis)).param("vt", delOtro)).andExpect(status().isOk());
	}

	@Test
	void el_jwt_de_sesion_en_la_url_se_rechaza() throws Exception {
		String sesion = login("creador@fst.mx");

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

		// Como sesión (cabecera Authorization): ninguna ruta lo acepta.
		mvc.perform(get(base + "/analyses/" + analisis + "/review").header("Authorization", "Bearer " + vt)).andExpect(status().isUnauthorized());
		mvc.perform(get(base + "/results").header("Authorization", "Bearer " + vt)).andExpect(status().isUnauthorized());
		mvc.perform(post(rutaToken(analisis)).header("Authorization", "Bearer " + vt)).andExpect(status().isUnauthorized());
		mvc.perform(post(base + "/analyses/" + analisis + "/restart").header("Authorization", "Bearer " + vt)).andExpect(status().isUnauthorized());
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + vt)).andExpect(status().isUnauthorized());
		// Como ?vt= en otra ruta: sin sesión, sigue pidiéndola.
		mvc.perform(get(base + "/results").param("vt", vt)).andExpect(status().isUnauthorized());
		mvc.perform(get(base + "/analyses/" + analisis + "/review").param("vt", vt)).andExpect(status().isUnauthorized());
		assertEquals("completado", estadoDe(analisis));
	}

	@Test
	void si_el_video_ya_se_borro_es_404_aunque_el_token_sea_valido() throws Exception {
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

	@Test
	void solo_se_guardan_los_segundos_que_vienen_y_el_resto_queda_como_estaba() throws Exception {
		mvc.perform(corregir(login("creador@fst.mx"), cuerpo(1, segundo(2, I, "corregida"), segundo(4, null, "dudosa"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.especimenes[0].totales['nado activo'].segundos").value(3))
				.andExpect(jsonPath("$.data.especimenes[0].totales['inmovilidad'].segundos").value(1));

		assertEquals(List.of(N, I, N, "-", N), conductasEnBase());
		assertEquals(List.of("maquina", "corregida", "maquina", "dudosa", "maquina"), origenesEnBase());
	}

	@Test
	void el_origen_solo_puede_ser_confirmada_corregida_o_dudosa() throws Exception {
		String creador = login("creador@fst.mx");
		for (String origen : List.of("confirmada", "corregida", "dudosa")) {
			mvc.perform(corregir(creador, cuerpo(1, segundo(1, N, origen)))).andExpect(status().isOk());
		}
		for (String origen : new String[] {"maquina", "humano", ""}) {
			mvc.perform(corregir(creador, cuerpo(1, segundo(1, I, origen)))).andExpect(status().isBadRequest());
		}
		mvc.perform(corregir(creador, cuerpo(1, segundo(1, I, null)))).andExpect(status().isBadRequest());
		mvc.perform(corregir(creador, "{\"especimenes\":[{\"numeroCilindro\":1,\"segundos\":[{\"segundo\":1,\"conducta\":\"nado activo\"}]}]}"))
				.andExpect(status().isBadRequest());
		// Los rechazos no escribieron nada: el último valor bueno sigue ahí.
		assertEquals("dudosa", origenesEnBase().get(0));
		assertEquals(N, conductasEnBase().get(0));
	}

	@Test
	void un_cuerpo_con_un_error_no_guarda_ni_los_segundos_buenos() throws Exception {
		String creador = login("creador@fst.mx");
		mvc.perform(corregir(creador, cuerpo(1, segundo(2, I, "corregida"), segundo(3, "nadar", "corregida")))).andExpect(status().isBadRequest());
		mvc.perform(corregir(creador, cuerpo(1, segundo(2, I, "corregida"), segundo(99, I, "corregida")))).andExpect(status().isBadRequest());
		mvc.perform(corregir(creador, cuerpo(7, segundo(2, I, "corregida")))).andExpect(status().isBadRequest());
		mvc.perform(corregir(creador, "{}")).andExpect(status().isBadRequest());

		assertEquals(List.of(N, N, N, N, N), conductasEnBase());
		assertEquals(List.of("maquina", "maquina", "maquina", "maquina", "maquina"), origenesEnBase());
	}

	@Test
	void el_administrador_puede_corregir_y_otra_cuenta_recibe_403_sin_cambiar_nada() throws Exception {
		mvc.perform(corregir(login("admin@fst.mx"), cuerpo(1, segundo(1, E, "corregida")))).andExpect(status().isOk());
		assertEquals(E, conductasEnBase().get(0));

		mvc.perform(corregir(login("otro@fst.mx"), cuerpo(1, segundo(2, I, "corregida")))).andExpect(status().isForbidden());
		mvc.perform(put(rutaSegundos(analisis)).contentType(MediaType.APPLICATION_JSON).content(cuerpo(1, segundo(2, I, "corregida"))))
				.andExpect(status().isUnauthorized());
		assertEquals(N, conductasEnBase().get(1));
	}

	@Test
	void solo_se_corrige_un_analisis_completado() throws Exception {
		String creador = login("creador@fst.mx");
		for (String estado : List.of("en cola", "procesando", "en pausa", "error", "cancelado")) {
			jdbc.update("UPDATE analisis SET estado = ?::estado_analisis WHERE idanalisis = ?", estado, analisis);
			mvc.perform(corregir(creador, cuerpo(1, segundo(2, I, "corregida")))).andExpect(status().isConflict());
		}
		assertEquals(N, conductasEnBase().get(1));
	}

	@Test
	void sin_el_video_no_se_corrige_y_responde_409() throws Exception {
		jdbc.update("UPDATE video SET archivo = NULL WHERE idvideo = ?", video);

		mvc.perform(corregir(login("creador@fst.mx"), cuerpo(1, segundo(2, I, "corregida")))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Sin el video no se puede corregir."));
		assertEquals(N, conductasEnBase().get(1));
	}

	// ----------------------------------------------------------------- GET review

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

		mvc.perform(get(rutaRevision()).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.puedeCorregir").value(false))
				.andExpect(jsonPath("$.data.videoUrl").doesNotExist());
	}

	// ----------------------------------------------------------------- apoyo

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

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedirToken(String sesion) {
		return post(rutaToken(analisis)).header("Authorization", "Bearer " + sesion);
	}

	private String tokenDeVideo(String sesion, int idAnalisis) throws Exception {
		MvcResult r = mvc.perform(post(rutaToken(idAnalisis)).header("Authorization", "Bearer " + sesion))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder corregir(String sesion, String cuerpo) {
		return put(rutaSegundos(analisis)).header("Authorization", "Bearer " + sesion).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static Map<String, Object> segundo(int numero, String conducta, String origen) {
		Map<String, Object> s = new java.util.LinkedHashMap<>();
		s.put("segundo", numero);
		s.put("conducta", conducta);
		if (origen != null) {
			s.put("origen", origen);
		}
		return s;
	}

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

	private List<String> origenesEnBase() {
		return jdbc.queryForList("SELECT origen::text FROM segundo WHERE idobservacion = ? ORDER BY segundo", String.class, observacion);
	}

	private String estadoDe(int idAnalisis) {
		return jdbc.queryForObject("SELECT estado::text FROM analisis WHERE idanalisis = ?", String.class, idAnalisis);
	}

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

	private int video(int tanda) {
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/1.mp4', 5)", tanda);
		return jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
	}

	private int analisis(int video, String estado) {
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, ?::estado_analisis)", video, estado);
		return jdbc.queryForObject("SELECT idanalisis FROM analisis WHERE idvideo = ?", Integer.class, video);
	}

	private int observacion(int tanda, int video) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, 1)", tanda);
		int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = 1", Integer.class, tanda);
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
		return jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idespecimen = ?", Integer.class, especimen);
	}

	private void segundo(int observacion, int numero, String conducta, String origen) {
		jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, ?, ?::tipo_conducta, ?::origen_segundo)",
				observacion, numero, conducta, origen);
	}

	private static Path carpeta(int idAnalisis) {
		return RAIZ.resolve("analisis").resolve(String.valueOf(idAnalisis));
	}

	private static void archivo(Path ruta, String contenido) throws Exception {
		Files.createDirectories(ruta.getParent());
		Files.writeString(ruta, contenido);
	}

	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		String token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
		assertNotEquals("", token);
		return token;
	}

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
