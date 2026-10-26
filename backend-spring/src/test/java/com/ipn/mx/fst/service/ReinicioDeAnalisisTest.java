package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Encargo 36: POST /experiments/{id}/analyses/{idAnalisis}/restart (R-04), contra una PostgreSQL de verdad. Mismas condiciones que
// AdministracionYRecuperacionTest: solo corre con FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el esquema de
// db/init (cada prueba borra todos los datos).
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-reinicio"
})
@AutoConfigureMockMvc
class ReinicioDeAnalisisTest {

	private static final String CLAVE = "clave-de-prueba-1";
	private static final Path RAIZ = Path.of(System.getProperty("java.io.tmpdir"), "fst-pruebas-reinicio");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private int experimento;
	private int analisis;
	private int video;

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
		experimento = experimento("1", "Exp");
		int grupo = grupo(experimento, "Control");
		int tanda = tanda(grupo, 1);
		video = video(tanda, "videos/1.mp4");
		analisis = analisis(video, "completado");
		resultados(tanda, video);
	}

	// ---------------------------------------------------------------- las reglas que rechazan

	@Test
	void solo_se_reinicia_un_completado_y_en_cualquier_otro_estado_responde_409() throws Exception {
		String creador = login("creador@fst.mx");
		for (String estado : List.of("en cola", "procesando", "en pausa", "error", "cancelado")) {
			jdbc.update("UPDATE analisis SET estado = ?::estado_analisis, rutadiagnostico = ? WHERE idanalisis = ?", estado,
					"error".equals(estado) ? "analisis/" + analisis + "/diagnostico.pdf" : null, analisis);
			mvc.perform(reiniciar(creador)).andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString(estado)));
			assertEquals(estado, estadoDe(analisis), estado);
		}
		// Los resultados siguen donde estaban: un rechazo no borra nada.
		assertEquals(2, cuenta("SELECT COUNT(*) FROM segundo"));
	}

	@Test
	void con_el_video_borrado_responde_409_y_no_toca_nada() throws Exception {
		jdbc.update("UPDATE video SET archivo = NULL WHERE idvideo = ?", video);

		mvc.perform(reiniciar(login("creador@fst.mx"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Sin el video no se puede reiniciar el análisis."));
		assertEquals("completado", estadoDe(analisis));
		assertEquals(2, cuenta("SELECT COUNT(*) FROM segundo"));
		assertEquals(1, cuenta("SELECT COUNT(*) FROM reporte"));
	}

	@Test
	void otra_cuenta_recibe_403_y_sin_sesion_401() throws Exception {
		mvc.perform(reiniciar(login("otro@fst.mx"))).andExpect(status().isForbidden());
		mvc.perform(post(ruta(experimento, analisis))).andExpect(status().isUnauthorized());
		assertEquals("completado", estadoDe(analisis));
		assertEquals(2, cuenta("SELECT COUNT(*) FROM segundo"));
	}

	@Test
	void un_analisis_que_no_existe_o_es_de_otro_experimento_responde_404_antes_que_403() throws Exception {
		String otro = login("otro@fst.mx");
		mvc.perform(post(ruta(experimento, 9999)).header("Authorization", "Bearer " + otro)).andExpect(status().isNotFound());
		mvc.perform(post(ruta(9999, analisis)).header("Authorization", "Bearer " + otro)).andExpect(status().isNotFound());

		int experimentoAjeno = experimento("2", "Ajeno");
		mvc.perform(post(ruta(experimentoAjeno, analisis)).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isNotFound());
		assertEquals("completado", estadoDe(analisis));
	}

	// ---------------------------------------------------------------- el reinicio bueno

	@Test
	void el_creador_reinicia_y_el_analisis_queda_en_cola_sin_resultados_ni_reportes_ni_archivos_viejos() throws Exception {
		Path carpeta = RAIZ.resolve("analisis").resolve(String.valueOf(analisis));
		for (String nombre : List.of("pausa.json", "pausa.jpg", "correcciones.json", "rachas_aceptadas.json", "web.mp4",
				"cajas.json", "progreso.json", "tiempos.json")) {
			archivo(carpeta.resolve(nombre));
		}
		archivo(carpeta.resolve("trabajo").resolve("1_rasgos.csv"));
		archivo(carpeta.resolve("trabajo").resolve("pipeline.log"));
		archivo(RAIZ.resolve("videos").resolve("1.mp4"));
		archivo(RAIZ.resolve("reportes").resolve(String.valueOf(experimento)).resolve("experimento.pdf"));
		jdbc.update("UPDATE analisis SET etapa = 'clasificacion', nivelclasif = 'agrupado', fechaanalisis = now() - interval '2 days' "
				+ "WHERE idanalisis = ?", analisis);
		Timestamp antes = jdbc.queryForObject("SELECT fechaanalisis FROM analisis WHERE idanalisis = ?", Timestamp.class, analisis);

		mvc.perform(reiniciar(login("creador@fst.mx"))).andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.idAnalisis").value(analisis))
				.andExpect(jsonPath("$.data.estado").value("en cola"));

		// Base: en cola con los valores de arranque, fecha nueva, sin resultados ni reportes.
		assertEquals("en cola", estadoDe(analisis));
		assertEquals("preprocesamiento", jdbc.queryForObject("SELECT etapa::text FROM analisis WHERE idanalisis = ?", String.class, analisis));
		assertEquals("preciso", jdbc.queryForObject("SELECT nivelclasif::text FROM analisis WHERE idanalisis = ?", String.class, analisis));
		Timestamp despues = jdbc.queryForObject("SELECT fechaanalisis FROM analisis WHERE idanalisis = ?", Timestamp.class, analisis);
		assertTrue(despues.after(antes), "fechaAnalisis debe ser la hora del reinicio");
		assertTrue(despues.getTime() > System.currentTimeMillis() - 60_000);
		assertEquals(0, cuenta("SELECT COUNT(*) FROM segundo"));
		assertEquals(0, cuenta("SELECT COUNT(*) FROM observacion"));
		assertEquals(0, cuenta("SELECT COUNT(*) FROM reporte"));
		// El video y la estructura del experimento siguen.
		assertEquals(1, cuenta("SELECT COUNT(*) FROM video WHERE archivo IS NOT NULL"));
		assertEquals(2, cuenta("SELECT COUNT(*) FROM especimen"));

		// Disco: lo de la corrida anterior se va; el video, web.mp4, cajas.json y los que el worker reescribe se quedan.
		for (String nombre : List.of("pausa.json", "pausa.jpg", "correcciones.json", "rachas_aceptadas.json")) {
			assertFalse(Files.exists(carpeta.resolve(nombre)), nombre);
		}
		assertFalse(Files.exists(carpeta.resolve("trabajo")));
		assertFalse(Files.exists(RAIZ.resolve("reportes").resolve(String.valueOf(experimento))));
		for (String nombre : List.of("web.mp4", "cajas.json", "progreso.json", "tiempos.json")) {
			assertTrue(Files.exists(carpeta.resolve(nombre)), nombre);
		}
		assertTrue(Files.exists(RAIZ.resolve("videos").resolve("1.mp4")));
	}

	@Test
	void un_administrador_tambien_puede_y_el_reinicio_no_toca_a_otros_analisis_del_experimento() throws Exception {
		int tanda2 = tanda(jdbc.queryForObject("SELECT idgrupo FROM grupo", Integer.class), 2);
		int video2 = video(tanda2, "videos/2.mp4");
		int analisis2 = analisis(video2, "completado");
		resultados(tanda2, video2);
		Path carpeta2 = RAIZ.resolve("analisis").resolve(String.valueOf(analisis2));
		archivo(carpeta2.resolve("trabajo").resolve("x.csv"));

		mvc.perform(reiniciar(login("admin@fst.mx"))).andExpect(status().isAccepted());

		assertEquals("en cola", estadoDe(analisis));
		assertEquals("completado", estadoDe(analisis2));
		assertEquals(2, cuenta("SELECT COUNT(*) FROM segundo WHERE idobservacion IN (SELECT idobservacion FROM observacion WHERE idvideo = " + video2 + ")"));
		assertTrue(Files.exists(carpeta2.resolve("trabajo").resolve("x.csv")));
	}

	@Test
	void un_reinicio_ya_hecho_no_se_repite_responde_409() throws Exception {
		String creador = login("creador@fst.mx");
		mvc.perform(reiniciar(creador)).andExpect(status().isAccepted());
		mvc.perform(reiniciar(creador)).andExpect(status().isConflict());
	}

	@Test
	void dos_reinicios_a_la_vez_solo_pasa_uno() throws Exception {
		String creador = login("creador@fst.mx");
		for (int vuelta = 0; vuelta < 15; vuelta++) {
			jdbc.update("UPDATE analisis SET estado = 'completado' WHERE idanalisis = ?", analisis);
			jdbc.update("DELETE FROM segundo");
			jdbc.update("DELETE FROM observacion");
			resultados(jdbc.queryForObject("SELECT idtanda FROM tanda", Integer.class), video);

			Callable<Integer> reinicio = () -> mvc.perform(reiniciar(creador)).andReturn().getResponse().getStatus();
			List<Integer> codigos = enParalelo(reinicio, reinicio);

			assertTrue(codigos.contains(202) && codigos.contains(409) && codigos.size() == 2, "vuelta " + vuelta + ": " + codigos);
			assertEquals("en cola", estadoDe(analisis));
			assertEquals(0, cuenta("SELECT COUNT(*) FROM segundo"));
		}
	}

	// ---------------------------------------------------------------- ayudas

	private MockHttpServletRequestBuilder reiniciar(String token) {
		return post(ruta(experimento, analisis)).header("Authorization", "Bearer " + token);
	}

	private static String ruta(int experimento, int analisis) {
		return "/experiments/" + experimento + "/analyses/" + analisis + "/restart";
	}

	private void cuenta(String id, String correo, boolean administrador) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, true, false)", id, correo, passwordEncoder.encode(CLAVE));
		if (administrador) {
			jdbc.update("INSERT INTO administrador (idinstitucional) VALUES (?)", id);
		}
	}

	private int experimento(String creador, String nombre) {
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES (?, ?, current_date)", creador, nombre);
		return jdbc.queryForObject("SELECT idexperimento FROM experimento WHERE nombre = ?", Integer.class, nombre);
	}

	private int grupo(int experimento, String etiqueta) {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, 'control', 'agua')", experimento, etiqueta);
		return jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE etiqueta = ?", Integer.class, etiqueta);
	}

	private int tanda(int grupo, int ordinal) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, 2)", grupo, ordinal);
		return jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, grupo, ordinal);
	}

	private int video(int tanda, String archivo) {
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', ?, 300)", tanda, archivo);
		return jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
	}

	private int analisis(int video, String estado) {
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, ?::estado_analisis)", video, estado);
		return jdbc.queryForObject("SELECT idanalisis FROM analisis WHERE idvideo = ?", Integer.class, video);
	}

	// Un espécimen con una observación de 2 segundos (uno corregido por una persona) y un reporte guardado del experimento.
	private void resultados(int tanda, int video) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) SELECT ?, 1 WHERE NOT EXISTS "
				+ "(SELECT 1 FROM especimen WHERE idtanda = ? AND numerocilindro = 1)", tanda, tanda);
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) SELECT ?, 2 WHERE NOT EXISTS "
				+ "(SELECT 1 FROM especimen WHERE idtanda = ? AND numerocilindro = 2)", tanda, tanda);
		int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = 1", Integer.class, tanda);
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
		int observacion = jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idvideo = ?", Integer.class, video);
		jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 1, 'nado activo', 'maquina')", observacion);
		jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 2, 'inmovilidad', 'corregida')", observacion);
		jdbc.update("INSERT INTO reporte (idexperimento, formato, ruta) VALUES (?, 'PDF', ?) ON CONFLICT (ruta) DO NOTHING",
				experimento, "reportes/" + experimento + "/experimento.pdf");
	}

	private String estadoDe(int idAnalisis) {
		return jdbc.queryForObject("SELECT estado::text FROM analisis WHERE idanalisis = ?", String.class, idAnalisis);
	}

	private int cuenta(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

	private static void archivo(Path ruta) throws Exception {
		Files.createDirectories(ruta.getParent());
		Files.writeString(ruta, "x");
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

	@SafeVarargs
	private static List<Integer> enParalelo(Callable<Integer>... tareas) throws Exception {
		ExecutorService hilos = Executors.newFixedThreadPool(tareas.length);
		try {
			CountDownLatch salida = new CountDownLatch(tareas.length);
			List<Future<Integer>> futuros = new ArrayList<>();
			for (Callable<Integer> tarea : tareas) {
				futuros.add(hilos.submit(() -> {
					salida.countDown();
					salida.await();
					return tarea.call();
				}));
			}
			List<Integer> resultados = new ArrayList<>();
			for (Future<Integer> futuro : futuros) {
				resultados.add(futuro.get());
			}
			return resultados;
		} finally {
			hilos.shutdownNow();
		}
	}

}
