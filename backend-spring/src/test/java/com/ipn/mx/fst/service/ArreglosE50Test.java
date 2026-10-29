package com.ipn.mx.fst.service;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

// Encargo 50: los arreglos de la revisión de diagramas (13, 15, 19, 20, 87, 70, 73, 78). Mismas condiciones que las demás
// pruebas de integración: solo corre con FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el esquema de db/init
// (cada prueba borra todos los datos). Los archivos van a una carpeta temporal propia.
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-e50"
})
@AutoConfigureMockMvc
class ArreglosE50Test {

	private static final String CLAVE = "clave-de-prueba-1";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private int experimento;

	// Borra todo al empezar y al terminar: así no deja observaciones que rompan las demás pruebas de la base.
	@AfterEach
	void limpia_al_terminar() {
		vaciar();
	}

	@BeforeEach
	void base_limpia() {
		vaciar();
		cuenta("1", "admin@fst.mx", true, true);
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Exp', current_date)");
		experimento = jdbc.queryForObject("SELECT idexperimento FROM experimento", Integer.class);
	}

	private void vaciar() {
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
	}

	// ----------------------------------------------------------------- 13: el alta nace como Investigador

	@Test
	void el_alta_ignora_el_rol_y_nace_como_investigador() throws Exception {
		mvc.perform(conToken(post("/admin/users"), login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idInstitucional\":\"3001\",\"nombre\":\"Ana\",\"apellidos\":\"López\",\"correo\":\"ana@fst.mx\","
						+ "\"rol\":\"Administrador\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.rol").value("Investigador"));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM administrador WHERE idinstitucional = '3001'", Integer.class));
	}

	// ----------------------------------------------------------------- 15: idInstitucional solo dígitos, recortado antes

	@Test
	void el_identificador_recortado_es_valido_y_se_guarda_sin_espacios() throws Exception {
		mvc.perform(conToken(post("/admin/users"), login()).contentType(MediaType.APPLICATION_JSON)
				.content(cuentaNueva("  2021  ", "recortado@fst.mx")))
				.andExpect(status().isCreated());
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE idinstitucional = '2021'", Integer.class));
	}

	@Test
	void el_identificador_con_letras_se_rechaza_con_el_texto_claro() throws Exception {
		mvc.perform(conToken(post("/admin/users"), login()).contentType(MediaType.APPLICATION_JSON)
				.content(cuentaNueva("20a1", "letra@fst.mx")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(containsString("solo puede llevar dígitos")));
	}

	@Test
	void el_identificador_de_mas_de_diez_digitos_se_rechaza() throws Exception {
		mvc.perform(conToken(post("/admin/users"), login()).contentType(MediaType.APPLICATION_JSON)
				.content(cuentaNueva("12345678901", "largo@fst.mx")))
				.andExpect(status().isBadRequest());
	}

	// ----------------------------------------------------------------- 19 y 20: la tanda de A a Z; la etiqueta de 100

	@Test
	void la_tanda_fuera_de_a_z_se_rechaza_con_422() throws Exception {
		mvc.perform(subida("Control", 27))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("La tanda debe ir de A a Z."));
	}

	@Test
	void la_etiqueta_de_mas_de_cien_caracteres_se_rechaza_con_422() throws Exception {
		mvc.perform(subida("a".repeat(101), 1))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value(containsString("100 caracteres")));
	}

	@Test
	void la_etiqueta_de_cien_caracteres_pasa_la_revision_de_largo() throws Exception {
		// El archivo no es un video: ffprobe lo rechaza, pero eso es posterior. Lo que se prueba es que el largo no lo impide.
		mvc.perform(subida("a".repeat(100), 1))
				.andExpect(jsonPath("$.error").value(not(containsString("caracteres"))));
	}

	// ----------------------------------------------------------------- 87: el Administrador no se quita el acceso a sí mismo

	@Test
	void el_administrador_no_se_desactiva_ni_se_quita_el_rol_a_si_mismo() throws Exception {
		String token = login();
		mvc.perform(conToken(patch("/admin/users/1"), token).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Para cambiar tu propia cuenta usa Mi perfil."));
		mvc.perform(conToken(patch("/admin/users/1"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"rol\":\"Investigador\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Para cambiar tu propia cuenta usa Mi perfil."));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM administrador WHERE idinstitucional = '1'", Integer.class));
	}

	@Test
	void el_administrador_si_puede_cambiar_su_nombre_y_su_correo() throws Exception {
		mvc.perform(conToken(patch("/admin/users/1"), login()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Otro\",\"correo\":\"nuevo@fst.mx\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nombre").value("Otro"));
	}

	@Test
	void un_administrador_si_puede_desactivar_a_otro() throws Exception {
		cuenta("2", "otro@fst.mx", true, true);
		mvc.perform(conToken(patch("/admin/users/2"), login()).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
				.andExpect(status().isOk());
	}

	// ----------------------------------------------------------------- 70 y 73: el PDF con «Sin ver» y sin caché si no hay resultados

	@Test
	void el_pdf_sin_completados_se_entrega_pero_no_se_guarda_en_cache() throws Exception {
		mvc.perform(get("/experiments/" + experimento + "/report/pdf").header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM reporte", Integer.class));
	}

	@Test
	void el_pdf_con_resultados_se_guarda_y_trae_la_columna_sin_ver() throws Exception {
		crearAnalisisCompletado();
		MvcResult r = mvc.perform(get("/experiments/" + experimento + "/report/pdf").header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk()).andReturn();
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reporte", Integer.class));
		PdfReader lector = new PdfReader(r.getResponse().getContentAsByteArray());
		String texto = PdfTextExtractor.getTextFromPage(lector, 1);
		lector.close();
		assertTrue(texto.contains("Sin ver"), texto);
	}

	// ----------------------------------------------------------------- 78: contador de no leídas del servidor

	@Test
	void el_contador_de_no_leidas_cuenta_todas_las_de_la_cuenta() throws Exception {
		for (int i = 0; i < 3; i++) {
			jdbc.update("INSERT INTO notificacion (idinstitucional, tipo, mensaje, leido) VALUES ('1', "
					+ "'analisis completado'::tipo_notificacion, 'm', false)");
		}
		jdbc.update("INSERT INTO notificacion (idinstitucional, tipo, mensaje, leido) VALUES ('1', "
				+ "'analisis completado'::tipo_notificacion, 'm', true)");
		mvc.perform(conToken(get("/notifications/unread-count"), login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.noLeidas").value(3));
	}

	@Test
	void el_contador_no_pasa_del_tope_de_la_lista() throws Exception {
		for (int i = 0; i < 105; i++) {
			jdbc.update("INSERT INTO notificacion (idinstitucional, tipo, mensaje, leido) VALUES ('1', "
					+ "'analisis completado'::tipo_notificacion, 'm', false)");
		}
		mvc.perform(conToken(get("/notifications"), login())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(100));
		mvc.perform(conToken(get("/notifications/unread-count"), login())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.noLeidas").value(105));
	}

	// ----------------------------------------------------------------- apoyo

	private MockMultipartHttpServletRequestBuilder subida(String grupo, int ordinal) throws Exception {
		MockMultipartHttpServletRequestBuilder peticion = multipart("/experiments/" + experimento + "/groups/" + grupo + "/videos")
				.file(new MockMultipartFile("archivo", "x.mp4", "video/mp4", "x".getBytes()))
				.param("sesion", "Dia 2").param("tipo", "control").param("tratamiento", "agua")
				.param("nCilindros", "2").param("ordinal", String.valueOf(ordinal));
		return peticion.header("Authorization", "Bearer " + login());
	}

	// Un grupo con una tanda, un video del Día 2 y un análisis completado de dos cilindros con segundos sin conducta.
	private void crearAnalisisCompletado() {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, 'Control', 'control', 'agua')",
				experimento);
		int grupo = jdbc.queryForObject("SELECT idgrupo FROM grupo", Integer.class);
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 1, 2)", grupo);
		int tanda = jdbc.queryForObject("SELECT idtanda FROM tanda", Integer.class);
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/x.mp4', 10)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video", Integer.class);
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, 'completado'::estado_analisis)", video);
		for (int cilindro = 1; cilindro <= 2; cilindro++) {
			jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, ?)", tanda, cilindro);
			int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = ?",
					Integer.class, tanda, cilindro);
			jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
			int observacion = jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idespecimen = ?", Integer.class,
					especimen);
			for (int s = 1; s <= 6; s++) {
				String conducta = s <= 3 ? "nado activo" : null;
				jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, ?, ?::tipo_conducta, 'maquina')",
						observacion, s, conducta);
			}
		}
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header("Authorization", "Bearer " + token);
	}

	private String login() {
		try {
			MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"correo\":\"admin@fst.mx\",\"contrasena\":\"" + CLAVE + "\"}"))
					.andExpect(status().isOk()).andReturn();
			return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private void cuenta(String id, String correo, boolean administrador, boolean activo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, ?, false)", id, correo, passwordEncoder.encode(CLAVE), activo);
		if (administrador) {
			jdbc.update("INSERT INTO administrador (idinstitucional) VALUES (?)", id);
		}
	}

	private static String cuentaNueva(String id, String correo) {
		return "{\"idInstitucional\":\"" + id + "\",\"nombre\":\"Ana\",\"apellidos\":\"López\",\"correo\":\"" + correo + "\"}";
	}

}
