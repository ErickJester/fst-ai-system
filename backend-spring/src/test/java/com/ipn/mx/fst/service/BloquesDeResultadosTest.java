// Mismo paquete que las clases que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
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

// Encargo 37, parte A: GET /experiments/{id}/results/blocks contra una PostgreSQL de verdad. Mismas condiciones que
// AdministracionYRecuperacionTest: solo corre con FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el esquema de
// db/init (cada prueba borra todos los datos). La regla en sí se prueba aparte, en BloquesDeCincoTest.
//
// El reparto es el de siempre: BloquesDeCincoTest comprueba la REGLA de decisión (sin base, en
// milisegundos) y esta comprueba el CAMINO COMPLETO: que los segundos salgan de la base en orden, se
// agrupen por espécimen y lleguen al JSON con la forma que espera la pantalla.
// La estructura de una prueba de integración está explicada en SegundosYExcelTest.
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-bloques"
})
@AutoConfigureMockMvc
class BloquesDeResultadosTest {

	private static final String CLAVE = "clave-de-prueba-1";
	// Abreviaturas de las conductas, para que las listas de segundos se lean de un vistazo.
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
	// Se guarda la observación del espécimen 1 porque una de las pruebas modifica sus segundos.
	private int observacion1;

	@BeforeEach
	void base_limpia() {
		// De las hojas hacia arriba, por las llaves foráneas.
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		// Dos cuentas: una creadora y otra ajena, para comprobar que la lectura no depende de quién sea.
		cuenta("1", "creador@fst.mx");
		cuenta("2", "otro@fst.mx");
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Exp', current_date)");
		experimento = jdbc.queryForObject("SELECT idexperimento FROM experimento", Integer.class);
		int control = grupo("Control");
		int referencia = grupo("Referencia");

		// Tanda A de Control: completada, con dos especímenes. El 1 mide 12 s (el último bloque queda de 2 s) y el 2 tiene 5 sin ver.
		// Los doce segundos del primero están diseñados para cubrir tres bloques distintos a la vez:
		//   1-5   N N N I I   → gana nado (3 contra 2);
		//   6-10  I I N N E   → empate dos a dos más uno: conducta activa;
		//   11-12 E null      → bloque incompleto, con un sin ver.
		int tandaA = tanda(control, 1, "completado");
		observacion1 = observacion(tandaA, 1);
		segundos(observacion1, N, N, N, I, I, I, I, N, N, E, E, null);
		segundos(observacion(tandaA, 2), null, null, null, null, null);

		// Tanda A de Referencia: todavía procesando, no entra.
		// Es el control negativo: comprueba que los análisis a medias se descartan.
		int tandaB = tanda(referencia, 1, "procesando");
		observacion(tandaB, 1);
	}

	@Test
	@Tag("trabajo-terminal")
	void devuelve_los_bloques_de_cada_especimen_con_la_forma_de_la_guia() throws Exception {
		MvcResult r = mvc.perform(get(ruta(experimento)).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk())
				// Dos especímenes, y el primero con tres bloques (12 segundos = 5 + 5 + 2).
				.andExpect(jsonPath("$.data.especimenes.length()").value(2))
				// "Control · A" es el mismo texto que arman los demás servicios para identificar la tanda.
				.andExpect(jsonPath("$.data.especimenes[0].tanda").value("Control · A"))
				.andExpect(jsonPath("$.data.especimenes[0].ordinal").value(1))
				.andExpect(jsonPath("$.data.especimenes[0].numeroCilindro").value(1))
				// isNumber comprueba que sea un número sin fijar cuál: el id lo asigna la base.
				.andExpect(jsonPath("$.data.especimenes[0].idGrupo").isNumber())
				.andExpect(jsonPath("$.data.especimenes[0].bloques.length()").value(3))
				.andReturn();
		String json = r.getResponse().getContentAsString();

		// Bloque 1 (segundos 1-5): 3 de nado y 2 de inmovilidad.
		assertEquals(1, (int) JsonPath.read(json, "$.data.especimenes[0].bloques[0].inicio"));
		assertEquals(N, JsonPath.read(json, "$.data.especimenes[0].bloques[0].conducta"));
		assertEquals(false, JsonPath.read(json, "$.data.especimenes[0].bloques[0].matiz"));
		// Los segundos originales viajan dentro del bloque, para poder ver de dónde salió la decisión.
		assertEquals(List.of(N, N, N, I, I), JsonPath.read(json, "$.data.especimenes[0].bloques[0].segundos"));
		// Bloque 2 (6-10): 2 de inmovilidad, 2 de nado y 1 de escalamiento: empate con inmovilidad, conducta activa.
		assertEquals(6, (int) JsonPath.read(json, "$.data.especimenes[0].bloques[1].inicio"));
		assertEquals("conducta activa", JsonPath.read(json, "$.data.especimenes[0].bloques[1].conducta"));
		assertEquals(false, JsonPath.read(json, "$.data.especimenes[0].bloques[1].matiz"));
		// Bloque 3 (11-12): incompleto, con 2 segundos; uno sin ver.
		assertEquals(11, (int) JsonPath.read(json, "$.data.especimenes[0].bloques[2].inicio"));
		assertEquals(E, JsonPath.read(json, "$.data.especimenes[0].bloques[2].conducta"));
		// matiz = true porque el segundo 12 no se vio; recuerda que los segundos que NO EXISTEN (el
		// video se acabó) no dan matiz, pero este sí existe y es null.
		assertEquals(true, JsonPath.read(json, "$.data.especimenes[0].bloques[2].matiz"));
		List<Object> ultimos = JsonPath.read(json, "$.data.especimenes[0].bloques[2].segundos");
		// Solo dos segundos, no cinco: el último bloque trae únicamente los que existen.
		assertEquals(2, ultimos.size());
		assertEquals(E, ultimos.get(0));
		// El «sin ver» llega como null en el JSON.
		assertEquals(null, ultimos.get(1));

		// El espécimen 2: los cinco sin ver.
		assertEquals(2, (int) JsonPath.read(json, "$.data.especimenes[1].numeroCilindro"));
		assertEquals("conducta activa", JsonPath.read(json, "$.data.especimenes[1].bloques[0].conducta"));
		assertEquals(true, JsonPath.read(json, "$.data.especimenes[1].bloques[0].matiz"));
	}

	@Test
	void solo_entran_las_tandas_con_analisis_completado() throws Exception {
		// La tanda de Referencia está 'procesando': sus segundos todavía no son resultados. El filtro
		// [?(@.tanda=='...')] busca un espécimen con ese texto y isEmpty comprueba que no hay ninguno.
		mvc.perform(get(ruta(experimento)).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(jsonPath("$.data.especimenes[?(@.tanda=='Referencia · A')]").isEmpty());
	}

	@Test
	void usa_los_segundos_ya_corregidos() throws Exception {
		String token = login("creador@fst.mx");
		// Los segundos 3, 4 y 5 pasan a escalamiento (corregida): el bloque 1 queda con 2 de nado y 3 de escalamiento.
		// Se modifica la tabla directamente, como si una persona hubiera corregido en la revisión. Es la
		// prueba de que los bloques salen de SEGUNDO al pedirlos y no de ninguna copia guardada (D-52).
		jdbc.update("UPDATE segundo SET conducta = ?::tipo_conducta, origen = 'corregida' WHERE idobservacion = ? AND segundo IN (3, 4, 5)",
				E, observacion1);
		mvc.perform(get(ruta(experimento)).header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.data.especimenes[0].bloques[0].conducta").value(E))
				.andExpect(jsonPath("$.data.especimenes[0].bloques[0].segundos[2]").value(E));
	}

	@Test
	void sin_sesion_401_experimento_inexistente_404_y_cualquier_cuenta_con_sesion_puede_verlos() throws Exception {
		// Sin token: 401 de la cadena de seguridad.
		mvc.perform(get(ruta(experimento))).andExpect(status().isUnauthorized());
		// Con token pero un id que no existe: 404 del servicio.
		mvc.perform(get(ruta(9999)).header("Authorization", "Bearer " + login("creador@fst.mx"))).andExpect(status().isNotFound());
		// Como /results, /results/byminute y /results/groups (R-07): lectura para cualquier usuario autenticado.
		// Una cuenta que NO creó el experimento también puede verlos: es un laboratorio, no un sistema de
		// documentos privados.
		mvc.perform(get(ruta(experimento)).header("Authorization", "Bearer " + login("otro@fst.mx"))).andExpect(status().isOk());
		mvc.perform(get("/experiments/" + experimento + "/results").header("Authorization", "Bearer " + login("otro@fst.mx")))
				.andExpect(status().isOk());
	}

	@Test
	void un_experimento_sin_analisis_completados_responde_una_lista_vacia() throws Exception {
		// Se pone el análisis en 'error': ya no hay ninguno completado. La respuesta es una lista vacía
		// con 200, no un 404 ni un 409: que todavía no haya resultados no es un error.
		jdbc.update("UPDATE analisis SET estado = 'error'");
		mvc.perform(get(ruta(experimento)).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.especimenes.length()").value(0));
	}

	// ----------------------------------------------------------------- apoyo
	//
	// Arman los datos directamente en la base. Están comentados uno a uno en SegundosYExcelTest.

	private static String ruta(int id) {
		return "/experiments/" + id + "/results/blocks";
	}

	private void cuenta(String id, String correo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, true, false)", id, correo, passwordEncoder.encode(CLAVE));
	}

	private int grupo(String etiqueta) {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, 'control', 'agua')", experimento, etiqueta);
		return jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE etiqueta = ?", Integer.class, etiqueta);
	}

	private int tanda(int grupo, int ordinal, String estado) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, 2)", grupo, ordinal);
		int tanda = jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, grupo, ordinal);
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/x.mp4', 12)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, ?::estado_analisis)", video, estado);
		return tanda;
	}

	// Un espécimen del cilindro dado con su observación del video de la tanda; devuelve el id de la observación.
	private int observacion(int tanda, int cilindro) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, ?)", tanda, cilindro);
		int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = ?",
				Integer.class, tanda, cilindro);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
		return jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idespecimen = ?", Integer.class, especimen);
	}

	// Los segundos de una observación, numerados desde 1, todos con origen 'maquina'. String... admite
	// número variable de argumentos; un null es el «sin ver».
	private void segundos(int observacion, String... conductas) {
		for (int i = 0; i < conductas.length; i++) {
			jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, ?, ?::tipo_conducta, 'maquina')",
					observacion, i + 1, conductas[i]);
		}
	}

	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

}
