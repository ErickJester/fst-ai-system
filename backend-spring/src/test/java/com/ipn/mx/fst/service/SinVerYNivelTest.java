package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
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

// Encargo 49: «sin ver» (los segundos con conducta NULL) como quinta clave de totales y de cada minuto en /results y
// /results/byminute, y nivelclasif recalculado después de un PUT .../segundos. Mismas condiciones que BloquesDeResultadosTest:
// solo corre con FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el esquema de db/init (cada prueba borra todos los datos).
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-sin-ver"
})
@AutoConfigureMockMvc
class SinVerYNivelTest {

	private static final String CLAVE = "clave-de-prueba-1";
	private static final String N = "nado activo";
	private static final String I = "inmovilidad";
	private static final String E = "escalamiento";
	private static final String A = "conducta activa";
	private static final String SIN_VER = "sin ver";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private int experimento;
	private int analisis;

	@BeforeEach
	void base_limpia() {
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES ('1', 'Nombre', 'Apellidos', 'creador@fst.mx', ?, true, false)", passwordEncoder.encode(CLAVE));
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Exp', current_date)");
		experimento = jdbc.queryForObject("SELECT idexperimento FROM experimento", Integer.class);
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, 'Control', 'control', 'agua')", experimento);
		int grupo = jdbc.queryForObject("SELECT idgrupo FROM grupo", Integer.class);
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 1, 2)", grupo);
		int tanda = jdbc.queryForObject("SELECT idtanda FROM tanda", Integer.class);
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/x.mp4', 70)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video", Integer.class);
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, 'completado'::estado_analisis)", video);
		analisis = jdbc.queryForObject("SELECT idanalisis FROM analisis", Integer.class);

		// Espécimen 1: 70 s (dos minutos, el segundo de 10 s): 1 sin ver en el minuto 1 y 3 en el 2. Espécimen 2: 5 s sin ver.
		String[] uno = new String[70];
		for (int s = 0; s < 70; s++) {
			uno[s] = s < 30 ? N : (s < 60 ? I : E);
		}
		uno[3] = null;
		uno[60] = null;
		uno[61] = null;
		uno[62] = null;
		segundos(observacion(tanda, video, 1), uno);
		segundos(observacion(tanda, video, 2), null, null, null, null, null);
	}

	@Test
	void totales_y_minutos_traen_las_cinco_claves_y_suman_el_cien_por_ciento() throws Exception {
		MvcResult r = mvc.perform(get("/experiments/" + experimento + "/results/byminute").header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				// Las cuatro de siempre siguen con su nombre y se agrega «sin ver».
				.andExpect(jsonPath("$.data.especimenes[0].totales['nado activo'].segundos").value(29))
				.andExpect(jsonPath("$.data.especimenes[0].totales['inmovilidad'].segundos").value(30))
				.andExpect(jsonPath("$.data.especimenes[0].totales['escalamiento'].segundos").value(7))
				.andExpect(jsonPath("$.data.especimenes[0].totales['conducta activa'].segundos").value(0))
				.andExpect(jsonPath("$.data.especimenes[0].totales['" + SIN_VER + "'].segundos").value(4))
				.andExpect(jsonPath("$.data.especimenes[0].minutos[0]['" + SIN_VER + "']").value(1))
				.andExpect(jsonPath("$.data.especimenes[0].minutos[1]['" + SIN_VER + "']").value(3))
				.andExpect(jsonPath("$.data.especimenes[1].totales['" + SIN_VER + "'].segundos").value(5))
				.andExpect(jsonPath("$.data.especimenes[1].totales['" + SIN_VER + "'].porcentaje").value(100.0))
				.andReturn();
		String json = r.getResponse().getContentAsString();

		// Los segundos de las cinco claves suman los segundos del espécimen, en el total y en cada minuto.
		for (int e = 0; e < 2; e++) {
			int suma = 0;
			for (String clave : List.of(N, I, E, A, SIN_VER)) {
				suma += (int) JsonPath.read(json, "$.data.especimenes[" + e + "].totales['" + clave + "'].segundos");
			}
			assertEquals(e == 0 ? 70 : 5, suma);
		}
		int minuto1 = 0;
		for (String clave : List.of(N, I, E, A, SIN_VER)) {
			minuto1 += (int) JsonPath.read(json, "$.data.especimenes[0].minutos[0]['" + clave + "']");
		}
		assertEquals(60, minuto1);
	}

	@Test
	void results_sin_minutos_tambien_trae_sin_ver_en_los_totales() throws Exception {
		mvc.perform(get("/experiments/" + experimento + "/results").header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.especimenes[0].totales['" + SIN_VER + "'].segundos").value(4));
	}

	// ----------------------------------------------------------------- nivelclasif después de corregir

	@Test
	void una_correccion_a_conducta_activa_sube_el_nivel_a_agrupado_y_quitarla_lo_baja_a_preciso() throws Exception {
		String token = login();
		assertEquals("preciso", nivel());

		mvc.perform(corregir(token, "[{\"segundo\":1,\"conducta\":\"conducta activa\",\"origen\":\"corregida\"}]")).andExpect(status().isOk());
		assertEquals("agrupado", nivel());

		// Un cambio que no toca esa conducta no baja el nivel mientras quede algún segundo así.
		mvc.perform(corregir(token, "[{\"segundo\":2,\"conducta\":\"nado activo\",\"origen\":\"confirmada\"}]")).andExpect(status().isOk());
		assertEquals("agrupado", nivel());

		mvc.perform(corregir(token, "[{\"segundo\":1,\"conducta\":\"nado activo\",\"origen\":\"corregida\"}]")).andExpect(status().isOk());
		assertEquals("preciso", nivel());
	}

	@Test
	void un_nivel_agrupado_del_worker_baja_si_la_revision_quita_la_ultima_conducta_activa() throws Exception {
		jdbc.update("UPDATE segundo SET conducta = 'conducta activa'::tipo_conducta WHERE segundo = 10 AND idobservacion = "
				+ "(SELECT MIN(idobservacion) FROM observacion)");
		jdbc.update("UPDATE analisis SET nivelclasif = 'agrupado'::nivel_clasif_analisis");

		// Marcarlo como no visible (NULL, dudosa) deja de ser conducta activa: el nivel baja a preciso.
		mvc.perform(corregir(login(), "[{\"segundo\":10,\"conducta\":null,\"origen\":\"dudosa\"}]")).andExpect(status().isOk());
		assertEquals("preciso", nivel());
	}

	@Test
	void una_correccion_rechazada_no_cambia_el_nivel() throws Exception {
		String token = login();
		mvc.perform(corregir(token, "[{\"segundo\":1,\"conducta\":\"vuela\",\"origen\":\"corregida\"}]")).andExpect(status().isBadRequest());
		assertEquals("preciso", nivel());
	}

	// ----------------------------------------------------------------- apoyo

	private String nivel() {
		return jdbc.queryForObject("SELECT nivelclasif FROM analisis WHERE idanalisis = ?", String.class, analisis);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder corregir(String token, String segundos) {
		return put("/experiments/" + experimento + "/analyses/" + analisis + "/segundos").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"especimenes\":[{\"numeroCilindro\":1,\"segundos\":" + segundos + "}]}");
	}

	private int observacion(int tanda, int video, int cilindro) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, ?)", tanda, cilindro);
		int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = ?",
				Integer.class, tanda, cilindro);
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
		return jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idespecimen = ?", Integer.class, especimen);
	}

	private void segundos(int observacion, String... conductas) {
		for (int i = 0; i < conductas.length; i++) {
			jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, ?, ?::tipo_conducta, 'maquina')",
					observacion, i + 1, conductas[i]);
		}
	}

	private String login() throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"creador@fst.mx\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

}
