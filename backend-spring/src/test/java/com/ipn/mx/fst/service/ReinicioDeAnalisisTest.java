// Mismo paquete que las clases que prueba, dentro de src/test.
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
// Timestamp: una fecha y hora de la base de datos, para comparar instantes.
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
// Las clases de concurrencia de Java, para lanzar dos peticiones a la vez (ver enParalelo):
// Callable es una tarea que devuelve un valor, CountDownLatch una barrera de salida, ExecutorService un
// grupo de hilos y Future el resultado que llegará de cada hilo.
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Encargo 36: POST /experiments/{id}/analyses/{idAnalisis}/restart (R-04), contra una PostgreSQL de verdad. Mismas condiciones que
// AdministracionYRecuperacionTest: solo corre con FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el esquema de
// db/init (cada prueba borra todos los datos).
//
// Prueba la operación más destructiva del sistema (ver RevisionService.reiniciar): tira los resultados,
// incluidas las correcciones humanas. Por eso comprueba con tanto detalle QUÉ se borra y, sobre todo, qué
// NO se borra, y que un rechazo no toque nada.
// Esta clase tiene además una prueba de CONCURRENCIA (dos reinicios a la vez), que es lo más delicado.
// La estructura de una prueba de integración está explicada en SegundosYExcelTest.
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
	// La carpeta del volumen de la prueba, igual que la propiedad app.archivos.raiz de arriba. Es estática
	// porque los métodos de apoyo estáticos (archivo, borrarRecursivo) también la necesitan.
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
		// De las hojas hacia arriba, por las llaves foráneas.
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		borrarRecursivo(RAIZ);
		// Tres cuentas con tres papeles: la creadora, una ajena y una administradora.
		cuenta("1", "creador@fst.mx", false);
		cuenta("2", "otro@fst.mx", false);
		cuenta("3", "admin@fst.mx", true);
		experimento = experimento("1", "Exp");
		int grupo = grupo(experimento, "Control");
		int tanda = tanda(grupo, 1);
		video = video(tanda, "videos/1.mp4");
		// El análisis nace COMPLETADO, que es la única situación en que se puede reiniciar.
		analisis = analisis(video, "completado");
		resultados(tanda, video);
	}

	// ---------------------------------------------------------------- las reglas que rechazan
	//
	// Las pruebas de rechazo comparten una idea: además de comprobar el código de error, comprueban que
	// NADA se haya tocado. Un rechazo que dejara el análisis a medias sería peor que no rechazar.

	@Test
	void solo_se_reinicia_un_completado_y_en_cualquier_otro_estado_responde_409() throws Exception {
		String creador = login("creador@fst.mx");
		// Un bucle sobre los cinco estados en que NO se puede reiniciar.
		for (String estado : List.of("en cola", "procesando", "en pausa", "error", "cancelado")) {
			// En 'error' se pone además la ruta del diagnóstico, que es lo que dejaría el worker.
			jdbc.update("UPDATE analisis SET estado = ?::estado_analisis, rutadiagnostico = ? WHERE idanalisis = ?", estado,
					"error".equals(estado) ? "analisis/" + analisis + "/diagnostico.pdf" : null, analisis);
			// El mensaje del 409 nombra el estado actual (ver RevisionServiceImpl.reiniciar).
			mvc.perform(reiniciar(creador)).andExpect(status().isConflict())
					.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString(estado)));
			// El segundo argumento imprime el estado si falla, para saber en cuál vuelta del bucle fue.
			assertEquals(estado, estadoDe(analisis), estado);
		}
		// Los resultados siguen donde estaban: un rechazo no borra nada.
		assertEquals(2, cuenta("SELECT COUNT(*) FROM segundo"));
	}

	@Test
	void con_el_video_borrado_responde_409_y_no_toca_nada() throws Exception {
		// archivo = NULL es lo que queda cuando el video se borró por antigüedad (ver la entidad Video).
		// Sin video no hay nada que volver a analizar.
		jdbc.update("UPDATE video SET archivo = NULL WHERE idvideo = ?", video);

		mvc.perform(reiniciar(login("creador@fst.mx"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Sin el video no se puede reiniciar el análisis."));
		// Se comprueba que sigue todo: el estado, los segundos Y el reporte guardado.
		assertEquals("completado", estadoDe(analisis));
		assertEquals(2, cuenta("SELECT COUNT(*) FROM segundo"));
		assertEquals(1, cuenta("SELECT COUNT(*) FROM reporte"));
	}

	@Test
	void otra_cuenta_recibe_403_y_sin_sesion_401() throws Exception {
		// Una cuenta que no creó el experimento y no es administradora: 403.
		mvc.perform(reiniciar(login("otro@fst.mx"))).andExpect(status().isForbidden());
		// Sin token: 401, puesto por la cadena de seguridad antes de llegar al servicio.
		mvc.perform(post(ruta(experimento, analisis))).andExpect(status().isUnauthorized());
		assertEquals("completado", estadoDe(analisis));
		assertEquals(2, cuenta("SELECT COUNT(*) FROM segundo"));
	}

	@Test
	void un_analisis_que_no_existe_o_es_de_otro_experimento_responde_404_antes_que_403() throws Exception {
		// "404 antes que 403" es el orden de las comprobaciones, y es de seguridad: con una cuenta SIN
		// permiso, un análisis que no existe da 404 y no 403. Si diera 403, alguien podría ir probando
		// ids para averiguar cuáles existen (el 403 delataría que ese análisis sí está).
		String otro = login("otro@fst.mx");
		// Un análisis inexistente...
		mvc.perform(post(ruta(experimento, 9999)).header("Authorization", "Bearer " + otro)).andExpect(status().isNotFound());
		// ...y un experimento inexistente.
		mvc.perform(post(ruta(9999, analisis)).header("Authorization", "Bearer " + otro)).andExpect(status().isNotFound());

		// El análisis existe, pero se pide con el id de OTRO experimento: también 404, aunque quien pregunta
		// sea la creadora. Comprueba que el análisis tiene que ser de ESE experimento.
		int experimentoAjeno = experimento("2", "Ajeno");
		mvc.perform(post(ruta(experimentoAjeno, analisis)).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isNotFound());
		assertEquals("completado", estadoDe(analisis));
	}

	// ---------------------------------------------------------------- el reinicio bueno

	@Test
	@Tag("trabajo-terminal")
	void el_creador_reinicia_y_el_analisis_queda_en_cola_sin_resultados_ni_reportes_ni_archivos_viejos() throws Exception {
		// Se prepara el escenario de una corrida anterior COMPLETA en disco: los archivos que hay que
		// borrar (los de ARCHIVOS_DE_LA_CORRIDA y la carpeta trabajo/) y los que NO (el video, web.mp4,
		// cajas.json, progreso.json, tiempos.json).
		Path carpeta = RAIZ.resolve("analisis").resolve(String.valueOf(analisis));
		for (String nombre : List.of("pausa.json", "pausa.jpg", "correcciones.json", "rachas_aceptadas.json", "web.mp4",
				"cajas.json", "progreso.json", "tiempos.json")) {
			archivo(carpeta.resolve(nombre));
		}
		archivo(carpeta.resolve("trabajo").resolve("1_rasgos.csv"));
		archivo(carpeta.resolve("trabajo").resolve("pipeline.log"));
		archivo(RAIZ.resolve("videos").resolve("1.mp4"));
		archivo(RAIZ.resolve("reportes").resolve(String.valueOf(experimento)).resolve("experimento.pdf"));
		// Valores de una corrida terminada: etapa final, nivel agrupado y una fecha de hace dos días. Así
		// se puede comprobar que el reinicio los devuelve a los de arranque.
		jdbc.update("UPDATE analisis SET etapa = 'clasificacion', nivelclasif = 'agrupado', fechaanalisis = now() - interval '2 days' "
				+ "WHERE idanalisis = ?", analisis);
		Timestamp antes = jdbc.queryForObject("SELECT fechaanalisis FROM analisis WHERE idanalisis = ?", Timestamp.class, analisis);

		// 202 y no 200: el trabajo sigue, el worker lo hará después (ver RevisionController.reiniciar).
		mvc.perform(reiniciar(login("creador@fst.mx"))).andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.idAnalisis").value(analisis))
				.andExpect(jsonPath("$.data.estado").value("en cola"));

		// Base: en cola con los valores de arranque, fecha nueva, sin resultados ni reportes.
		assertEquals("en cola", estadoDe(analisis));
		// Los valores de arranque son los DEFAULT de la tabla (etapa = DEFAULT en el UPDATE).
		assertEquals("preprocesamiento", jdbc.queryForObject("SELECT etapa::text FROM analisis WHERE idanalisis = ?", String.class, analisis));
		assertEquals("preciso", jdbc.queryForObject("SELECT nivelclasif::text FROM analisis WHERE idanalisis = ?", String.class, analisis));
		Timestamp despues = jdbc.queryForObject("SELECT fechaanalisis FROM analisis WHERE idanalisis = ?", Timestamp.class, analisis);
		// La fecha es AHORA, y no se conserva la de antes: a diferencia de la pausa, un reinicio es trabajo
		// nuevo y entra al final de la cola.
		assertTrue(despues.after(antes), "fechaAnalisis debe ser la hora del reinicio");
		// "Ahora" con un margen de un minuto, porque entre el UPDATE y esta lectura pasan milisegundos.
		assertTrue(despues.getTime() > System.currentTimeMillis() - 60_000);
		// Los resultados desaparecen: segundos, observaciones y reportes.
		assertEquals(0, cuenta("SELECT COUNT(*) FROM segundo"));
		assertEquals(0, cuenta("SELECT COUNT(*) FROM observacion"));
		assertEquals(0, cuenta("SELECT COUNT(*) FROM reporte"));
		// El video y la estructura del experimento siguen.
		assertEquals(1, cuenta("SELECT COUNT(*) FROM video WHERE archivo IS NOT NULL"));
		assertEquals(2, cuenta("SELECT COUNT(*) FROM especimen"));

		// Disco: lo de la corrida anterior se va; el video, web.mp4, cajas.json y los que el worker reescribe se quedan.
		// Es la comprobación de la regla que documenta ARCHIVOS_DE_LA_CORRIDA en RevisionServiceImpl.
		for (String nombre : List.of("pausa.json", "pausa.jpg", "correcciones.json", "rachas_aceptadas.json")) {
			assertFalse(Files.exists(carpeta.resolve(nombre)), nombre);
		}
		assertFalse(Files.exists(carpeta.resolve("trabajo")));
		// La carpeta de reportes del experimento también desaparece (la borra ReporteAlmacen.invalidar).
		assertFalse(Files.exists(RAIZ.resolve("reportes").resolve(String.valueOf(experimento))));
		for (String nombre : List.of("web.mp4", "cajas.json", "progreso.json", "tiempos.json")) {
			assertTrue(Files.exists(carpeta.resolve(nombre)), nombre);
		}
		assertTrue(Files.exists(RAIZ.resolve("videos").resolve("1.mp4")));
	}

	@Test
	void un_administrador_tambien_puede_y_el_reinicio_no_toca_a_otros_analisis_del_experimento() throws Exception {
		// Se añade una SEGUNDA tanda con su propio análisis completado y sus archivos.
		int tanda2 = tanda(jdbc.queryForObject("SELECT idgrupo FROM grupo", Integer.class), 2);
		int video2 = video(tanda2, "videos/2.mp4");
		int analisis2 = analisis(video2, "completado");
		resultados(tanda2, video2);
		Path carpeta2 = RAIZ.resolve("analisis").resolve(String.valueOf(analisis2));
		archivo(carpeta2.resolve("trabajo").resolve("x.csv"));

		// Quien reinicia es la administradora, que no creó el experimento: también puede.
		mvc.perform(reiniciar(login("admin@fst.mx"))).andExpect(status().isAccepted());

		assertEquals("en cola", estadoDe(analisis));
		// El OTRO análisis queda intacto: completado, con sus segundos y sus archivos. El reinicio es de
		// UN análisis, no del experimento entero.
		assertEquals("completado", estadoDe(analisis2));
		assertEquals(2, cuenta("SELECT COUNT(*) FROM segundo WHERE idobservacion IN (SELECT idobservacion FROM observacion WHERE idvideo = " + video2 + ")"));
		assertTrue(Files.exists(carpeta2.resolve("trabajo").resolve("x.csv")));
	}

	@Test
	void un_reinicio_ya_hecho_no_se_repite_responde_409() throws Exception {
		String creador = login("creador@fst.mx");
		// El primero pasa y deja el análisis 'en cola'; el segundo ya no lo encuentra 'completado'.
		mvc.perform(reiniciar(creador)).andExpect(status().isAccepted());
		mvc.perform(reiniciar(creador)).andExpect(status().isConflict());
	}

	@Test
	@Tag("trabajo-terminal")
	void dos_reinicios_a_la_vez_solo_pasa_uno() throws Exception {
		// La prueba de CONCURRENCIA. Lo que se comprueba es la protección de RevisionServiceImpl.reiniciar:
		// el candado del experimento y el UPDATE condicional ("AND estado = 'completado'") garantizan que,
		// si dos personas pulsan a la vez, gane una (202) y la otra reciba 409, en vez de duplicar el trabajo.
		String creador = login("creador@fst.mx");
		// Quince vueltas: una carrera sale bien por casualidad muchas veces, y repetirla da más
		// probabilidad de pillar un fallo de verdad.
		for (int vuelta = 0; vuelta < 15; vuelta++) {
			// Se deja el análisis como al principio en cada vuelta: completado y con resultados.
			jdbc.update("UPDATE analisis SET estado = 'completado' WHERE idanalisis = ?", analisis);
			jdbc.update("DELETE FROM segundo");
			jdbc.update("DELETE FROM observacion");
			resultados(jdbc.queryForObject("SELECT idtanda FROM tanda", Integer.class), video);

			// Una tarea que lanza el reinicio y devuelve solo el código de la respuesta. Se usa DOS veces:
			// dos peticiones idénticas.
			Callable<Integer> reinicio = () -> mvc.perform(reiniciar(creador)).andReturn().getResponse().getStatus();
			List<Integer> codigos = enParalelo(reinicio, reinicio);

			// Exactamente uno 202 y uno 409, sea cual sea el orden en que terminaron. Si pasaran las dos
			// (dos 202) se habría duplicado el trabajo; si fallaran las dos, se habría perdido.
			assertTrue(codigos.contains(202) && codigos.contains(409) && codigos.size() == 2, "vuelta " + vuelta + ": " + codigos);
			assertEquals("en cola", estadoDe(analisis));
			assertEquals(0, cuenta("SELECT COUNT(*) FROM segundo"));
		}
	}

	// ---------------------------------------------------------------- ayudas

	// La petición de reinicio, con el token puesto. Devuelve el constructor sin ejecutarlo.
	private MockHttpServletRequestBuilder reiniciar(String token) {
		return post(ruta(experimento, analisis)).header("Authorization", "Bearer " + token);
	}

	private static String ruta(int experimento, int analisis) {
		return "/experiments/" + experimento + "/analyses/" + analisis + "/restart";
	}

	// Una cuenta con su contraseña cifrada y, si se pide, fila de administrador.
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
	//
	// Los INSERT de especimen llevan "WHERE NOT EXISTS": este método se llama más de una vez para la misma
	// tanda (en la prueba de concurrencia, una vez por vuelta), y así no choca contra la restricción única
	// de (tanda, cilindro). Es la forma de hacer un "insertar si no existe" en SQL estándar.
	// ON CONFLICT (ruta) DO NOTHING hace lo mismo con el reporte, cuya ruta es única.
	private void resultados(int tanda, int video) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) SELECT ?, 1 WHERE NOT EXISTS "
				+ "(SELECT 1 FROM especimen WHERE idtanda = ? AND numerocilindro = 1)", tanda, tanda);
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) SELECT ?, 2 WHERE NOT EXISTS "
				+ "(SELECT 1 FROM especimen WHERE idtanda = ? AND numerocilindro = 2)", tanda, tanda);
		int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = 1", Integer.class, tanda);
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
		int observacion = jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idvideo = ?", Integer.class, video);
		// El segundo con origen 'corregida': es el dato que tira el reinicio y que más duele perder.
		jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 1, 'nado activo', 'maquina')", observacion);
		jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 2, 'inmovilidad', 'corregida')", observacion);
		jdbc.update("INSERT INTO reporte (idexperimento, formato, ruta) VALUES (?, 'PDF', ?) ON CONFLICT (ruta) DO NOTHING",
				experimento, "reportes/" + experimento + "/experimento.pdf");
	}

	// El estado de un análisis, leído directo de la base. ::text convierte el dominio a texto.
	private String estadoDe(int idAnalisis) {
		return jdbc.queryForObject("SELECT estado::text FROM analisis WHERE idanalisis = ?", String.class, idAnalisis);
	}

	// Cuenta filas con la consulta que se le dé. Hace de atajo para las muchas comprobaciones de arriba.
	private int cuenta(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

	// Crea un archivo de un byte, y las carpetas que haga falta hasta llegar a él. El contenido da igual:
	// las pruebas solo miran si el archivo existe o no.
	private static void archivo(Path ruta) throws Exception {
		Files.createDirectories(ruta.getParent());
		Files.writeString(ruta, "x");
	}

	// Borra una carpeta con todo lo que tiene dentro: ordenar las rutas AL REVÉS hace que las más profundas
	// se borren primero, así cada carpeta ya está vacía cuando le toca.
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

	// Ejecuta varias tareas AL MISMO TIEMPO y devuelve sus resultados en el orden en que se pasaron.
	//
	// El problema que resuelve: lanzar dos hilos no garantiza que las peticiones lleguen a la vez. El
	// primero podría terminar antes de que arranque el segundo, y entonces no habría competencia y la
	// prueba no probaría nada. El CountDownLatch es una BARRERA: cada hilo cuenta atrás y se queda
	// esperando hasta que TODOS hayan llegado; en ese instante salen juntos.
	// @SafeVarargs silencia el aviso del compilador sobre los argumentos variables con tipos genéricos;
	// es seguro porque el método solo los lee.
	@SafeVarargs
	private static List<Integer> enParalelo(Callable<Integer>... tareas) throws Exception {
		// Un grupo de tantos hilos como tareas: todas pueden correr a la vez.
		ExecutorService hilos = Executors.newFixedThreadPool(tareas.length);
		try {
			CountDownLatch salida = new CountDownLatch(tareas.length);
			List<Future<Integer>> futuros = new ArrayList<>();
			for (Callable<Integer> tarea : tareas) {
				futuros.add(hilos.submit(() -> {
					// countDown avisa "ya llegué"; await espera a que lleguen todos los demás.
					salida.countDown();
					salida.await();
					return tarea.call();
				}));
			}
			// get() espera a que cada hilo termine y trae su resultado.
			List<Integer> resultados = new ArrayList<>();
			for (Future<Integer> futuro : futuros) {
				resultados.add(futuro.get());
			}
			return resultados;
		} finally {
			// El finally garantiza que los hilos se detengan aunque algo falle: sin él, quedarían vivos
			// y acumularían entre vueltas.
			hilos.shutdownNow();
		}
	}

}
