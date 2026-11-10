package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipn.mx.fst.utilerias.Ffprobe;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

// Crear, eliminar y subir video contra una PostgreSQL de verdad. Mismas condiciones que AdministracionYRecuperacionTest:
// corre solo con FST_TEST_DB_URL apuntando a una base VACÍA con el esquema de db/init/001_esquema.sql (cada prueba borra
// todos los datos). ffprobe está simulado (Ffprobe es un bean): no hace falta un video real. Los archivos van a un
// directorio temporal propio (fst-pruebas-crear-subir), que se vacía antes de cada prueba.
@Tag("trabajo-terminal")
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-crear-subir",
		"app.reset.minutos=60"
})
@AutoConfigureMockMvc
class CrearEliminarSubirTest {

	private static final String CLAVE = "clave-de-prueba-1";
	private static final String NOMBRE_RAIZ = "fst-pruebas-crear-subir";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private Ffprobe ffprobe;

	@BeforeEach
	void base_limpia() throws IOException {
		reset(ffprobe);
		jdbc.update("DELETE FROM segundo");
		jdbc.update("DELETE FROM observacion");
		jdbc.update("DELETE FROM especimen");
		jdbc.update("DELETE FROM reporte");
		jdbc.update("DELETE FROM analisis");
		jdbc.update("DELETE FROM video");
		jdbc.update("DELETE FROM tanda");
		jdbc.update("DELETE FROM grupo");
		jdbc.update("DELETE FROM notificacion");
		jdbc.update("DELETE FROM experimento");
		jdbc.update("DELETE FROM administrador");
		jdbc.update("DELETE FROM usuario");
		vaciarRaiz();
	}

	// ---------------------------------------------------------------- 1. crear experimento

	@Test
	void crear_un_experimento_lo_guarda_y_aparece_en_la_lista() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		String token = login("inv@fst.mx");

		mvc.perform(conToken(post("/experiments"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Exp creado\",\"fecha\":\"2026-11-01\",\"notas\":\"prueba\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.idExperimento").isNumber());

		assertEquals("2", jdbc.queryForObject("SELECT idinstitucional FROM experimento WHERE nombre = 'Exp creado'", String.class));
		assertEquals("2026-11-01", jdbc.queryForObject("SELECT fecha::text FROM experimento WHERE nombre = 'Exp creado'", String.class));
		mvc.perform(conToken(get("/experiments"), token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].nombre").value("Exp creado"));
	}

	@Test
	void un_nombre_repetido_responde_409_con_su_mensaje() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		String token = login("inv@fst.mx");
		experimento("2", "Exp repetido");

		mvc.perform(conToken(post("/experiments"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Exp repetido\",\"fecha\":\"2026-11-01\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Ya existe un experimento con el nombre 'Exp repetido'"));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM experimento", Integer.class));
	}

	@Test
	void un_nombre_vacio_o_una_fecha_invalida_responden_400() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		String token = login("inv@fst.mx");

		mvc.perform(conToken(post("/experiments"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"\",\"fecha\":\"2026-11-01\"}")).andExpect(status().isBadRequest());
		mvc.perform(conToken(post("/experiments"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Exp sin fecha\"}")).andExpect(status().isBadRequest());
		mvc.perform(conToken(post("/experiments"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Exp fecha mala\",\"fecha\":\"no-es-fecha\"}")).andExpect(status().isBadRequest());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM experimento", Integer.class));
	}

	@Test
	void crear_sin_sesion_responde_401() throws Exception {
		mvc.perform(post("/experiments").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Exp anonimo\",\"fecha\":\"2026-11-01\"}")).andExpect(status().isUnauthorized());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM experimento", Integer.class));
	}

	// ---------------------------------------------------------------- 2. eliminar experimento

	@Test
	void el_creador_puede_eliminar_su_experimento() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp propio");

		mvc.perform(conToken(delete("/experiments/" + experimento), login("inv@fst.mx"))).andExpect(status().isNoContent());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class, experimento));
	}

	@Test
	void un_administrador_puede_eliminar_el_experimento_de_otro() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp ajeno");

		mvc.perform(conToken(delete("/experiments/" + experimento), login("admin@fst.mx"))).andExpect(status().isNoContent());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class, experimento));
	}

	@Test
	void otro_investigador_recibe_403_y_no_se_borra_nada() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		cuenta("3", "otro@fst.mx", false, true);
		int experimento = experimento("2", "Exp de inv");
		int grupo = grupo(experimento, "Control");
		tanda(grupo, 1);

		mvc.perform(conToken(delete("/experiments/" + experimento), login("otro@fst.mx")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Solo quien creó el experimento o un Administrador puede eliminarlo"));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class, experimento));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE idexperimento = ?", Integer.class, experimento));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tanda", Integer.class));
	}

	@Test
	void con_un_analisis_procesando_responde_409_y_no_borra_nada() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp procesando");
		analisis(tanda(grupo(experimento, "Control"), 1), "procesando");

		mvc.perform(conToken(delete("/experiments/" + experimento), login("inv@fst.mx")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("No se puede eliminar mientras un análisis está procesando o en pausa"));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class, experimento));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM analisis WHERE estado = 'procesando'", Integer.class));
	}

	@Test
	void con_un_analisis_en_pausa_responde_409_y_no_borra_nada() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp en pausa");
		analisis(tanda(grupo(experimento, "Control"), 1), "en pausa");

		mvc.perform(conToken(delete("/experiments/" + experimento), login("inv@fst.mx")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("No se puede eliminar mientras un análisis está procesando o en pausa"));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class, experimento));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM analisis WHERE estado = 'en pausa'", Integer.class));
	}

	@Test
	void al_eliminar_desaparecen_sus_grupos_tandas_y_notificaciones_y_los_de_otro_experimento_quedan() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int borrar = experimento("2", "Exp a borrar");
		int conservar = experimento("2", "Exp a conservar");
		tanda(grupo(borrar, "Control"), 1);
		tanda(grupo(conservar, "Control"), 1);
		notificacion("2", borrar);
		notificacion("2", conservar);

		mvc.perform(conToken(delete("/experiments/" + borrar), login("inv@fst.mx"))).andExpect(status().isNoContent());

		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE idexperimento = ?", Integer.class, borrar));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE idexperimento = ?", Integer.class, conservar));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tanda", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE idexperimento = ?", Integer.class, borrar));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE idexperimento = ?", Integer.class, conservar));
	}

	// ---------------------------------------------------------------- 3. subir video

	@Test
	void otro_investigador_recibe_403_con_el_mensaje_de_creador() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		cuenta("3", "otro@fst.mx", false, true);
		int experimento = experimento("2", "Exp de subida");

		subir(login("otro@fst.mx"), experimento, "Dia 2", "video.mp4", null)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Solo quien creó el experimento sube sus videos."));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM video", Integer.class));
	}

	@Test
	void un_archivo_que_no_es_mp4_ni_mov_se_rechaza_con_422() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp formato");

		subir(login("inv@fst.mx"), experimento, "Dia 2", "video.avi", null)
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("Solo se aceptan videos .mp4 o .mov."));
		// Se rechaza antes de sondear el archivo y antes de crear la tanda.
		verify(ffprobe, never()).sondear(any());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tanda", Integer.class));
	}

	@Test
	void un_video_vertical_responde_422_y_no_deja_archivos_ni_filas() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp vertical");
		when(ffprobe.sondear(any())).thenReturn(new Ffprobe.Sonda(1080, 1920, 300.0));

		subir(login("inv@fst.mx"), experimento, "Dia 2", "vertical.mp4", null)
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value(
						"El video es vertical: se necesita un video horizontal, con los cilindros de lado a lado."));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM video", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tanda", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM grupo", Integer.class));
		assertEquals(0, archivosEn("videos"));
	}

	@Test
	void un_video_horizontal_del_dia_2_crea_la_tanda_el_video_y_el_analisis_en_cola() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp horizontal");
		when(ffprobe.sondear(any())).thenReturn(new Ffprobe.Sonda(1920, 1080, 300.0));

		subir(login("inv@fst.mx"), experimento, "Dia 2", "horizontal.mp4", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.estadoAnalisis").value("en cola"));

		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tanda", Integer.class));
		assertEquals("A", jdbc.queryForObject("SELECT CHR(64 + ordinal) FROM tanda", String.class));
		int idVideo = jdbc.queryForObject("SELECT idvideo FROM video", Integer.class);
		assertEquals("videos/" + idVideo + ".mp4",
				jdbc.queryForObject("SELECT archivo FROM video WHERE idvideo = ?", String.class, idVideo));
		assertTrue(Files.exists(raizDePruebas().resolve("videos").resolve(idVideo + ".mp4")));
		assertEquals("en cola", jdbc.queryForObject("SELECT estado FROM analisis WHERE idvideo = ?", String.class, idVideo));
	}

	@Test
	void un_video_horizontal_del_dia_1_se_guarda_sin_analisis() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp dia 1");
		when(ffprobe.sondear(any())).thenReturn(new Ffprobe.Sonda(1920, 1080, 1200.0));

		subir(login("inv@fst.mx"), experimento, "Dia 1", "habituacion.mp4", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.estadoAnalisis").doesNotExist());

		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM video WHERE sesion = 'Dia 1'", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM analisis", Integer.class));
	}

	@Test
	void un_segundo_video_del_mismo_dia_en_la_misma_tanda_responde_409() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp dos videos");
		String token = login("inv@fst.mx");
		when(ffprobe.sondear(any())).thenReturn(new Ffprobe.Sonda(1920, 1080, 300.0));

		subir(token, experimento, "Dia 2", "primero.mp4", null).andExpect(status().isCreated());
		subir(token, experimento, "Dia 2", "segundo.mp4", "1")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("La Tanda A ya tiene su video de Día 2. Cada tanda lleva un solo video por sesión."));

		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM video", Integer.class));
		assertEquals(1, archivosEn("videos"));
	}

	// ---------------------------------------------------------------- ayudas

	private ResultActions subir(String token, int experimento, String sesion, String nombreArchivo, String ordinal)
			throws Exception {
		MockMultipartHttpServletRequestBuilder peticion = multipart("/experiments/{id}/groups/{grupo}/videos", experimento, "Control")
				.file(new MockMultipartFile("archivo", nombreArchivo, "video/mp4", new byte[] {0, 1, 2}));
		peticion.param("sesion", sesion);
		peticion.param("tipo", "control");
		peticion.param("tratamiento", "agua");
		peticion.param("nCilindros", "2");
		if (ordinal != null) {
			peticion.param("ordinal", ordinal);
		}
		return mvc.perform(peticion.header("Authorization", "Bearer " + token));
	}

	private void cuenta(String id, String correo, boolean administrador, boolean activo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, ?, false)", id, correo, passwordEncoder.encode(CLAVE), activo);
		if (administrador) {
			jdbc.update("INSERT INTO administrador (idinstitucional) VALUES (?)", id);
		}
	}

	private int experimento(String creador, String nombre) {
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES (?, ?, current_date)", creador, nombre);
		return jdbc.queryForObject("SELECT idexperimento FROM experimento WHERE nombre = ?", Integer.class, nombre);
	}

	private int grupo(int experimento, String etiqueta) {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, 'control', 'agua')",
				experimento, etiqueta);
		return jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE idexperimento = ? AND etiqueta = ?", Integer.class,
				experimento, etiqueta);
	}

	private int tanda(int grupo, int ordinal) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, 2)", grupo, ordinal);
		return jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, grupo, ordinal);
	}

	// Un video del Día 2 con su análisis en el estado dado (la subida normal crea el análisis en cola).
	private void analisis(int tanda, String estado) {
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', NULL, 300)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		jdbc.update("INSERT INTO analisis (idvideo, estado, fechaanalisis) VALUES (?, ?::estado_analisis, now())", video, estado);
	}

	private void notificacion(String idInstitucional, int experimento) {
		jdbc.update("INSERT INTO notificacion (idinstitucional, idexperimento, tipo, mensaje) "
				+ "VALUES (?, ?, 'analisis completado', 'prueba')", idInstitucional, experimento);
	}

	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header("Authorization", "Bearer " + token);
	}

	private static Path raizDePruebas() {
		return Path.of(System.getProperty("java.io.tmpdir")).resolve(NOMBRE_RAIZ);
	}

	private static int archivosEn(String subcarpeta) throws IOException {
		Path carpeta = raizDePruebas().resolve(subcarpeta);
		if (!Files.exists(carpeta)) {
			return 0;
		}
		try (Stream<Path> archivos = Files.list(carpeta)) {
			return (int) archivos.count();
		}
	}

	// Deja la raíz de pruebas vacía, con sus subcarpetas, para que cada prueba cuente solo lo suyo.
	private static void vaciarRaiz() throws IOException {
		Path raiz = raizDePruebas();
		if (Files.exists(raiz)) {
			try (Stream<Path> todo = Files.walk(raiz)) {
				for (Path p : todo.sorted(Comparator.reverseOrder()).toList()) {
					if (!p.equals(raiz)) {
						Files.delete(p);
					}
				}
			}
		}
		Files.createDirectories(raiz.resolve("videos"));
		Files.createDirectories(raiz.resolve("analisis"));
		Files.createDirectories(raiz.resolve("reportes"));
	}
}
