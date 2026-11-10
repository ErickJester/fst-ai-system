package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

// Datos de las gráficas (M1A/M1B): GET /experiments/{id}/results/charts contra una PostgreSQL de verdad. Mismas condiciones
// que BloquesDeResultadosTest: solo corre con FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el esquema de db/init
// (cada prueba borra todos los datos). El cálculo en sí se prueba sin base en GraficasDeResultadosTest.
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-graficas"
})
@AutoConfigureMockMvc
class GraficasEndpointTest {

	private static final String CLAVE = "clave-de-prueba-1";
	private static final String N = "nado activo";
	private static final String I = "inmovilidad";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private int experimento;

	@BeforeEach
	void base_limpia() {
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		cuenta("1", "creador@fst.mx");
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Exp', current_date)");
		experimento = jdbc.queryForObject("SELECT idexperimento FROM experimento", Integer.class);

		// Se insertan fuera de orden: la respuesta debe salir control, referencia y luego el experimental.
		grupo("Dosis 1", "tratamiento experimental");
		int control = grupo("Control", "control");
		int referencia = grupo("Fluoxetina", "referencia");

		// Control, tanda A completada con dos especímenes: el 1 nada 3 s y se inmoviliza 2; el 2 no se ve en 5 s.
		int tandaA = tanda(control, 1, "completado");
		int obs1 = observacion(tandaA, 1);
		segundos(obs1, N, N, N, I, I);
		segundos(observacion(tandaA, 2), null, null, null, null, null);

		// Referencia: tanda A todavía procesando, así que entra como faltante. Experimental: sin videos.
		tanda(referencia, 1, "procesando");
	}

	@Test
	@Tag("trabajo-terminal")
	void da_los_grupos_en_orden_con_sus_faltantes_y_sus_estadisticos() throws Exception {
		MvcResult r = mvc.perform(get(ruta(experimento)).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.dia").value(2))
				.andExpect(jsonPath("$.data.grupos.length()").value(3))
				.andExpect(jsonPath("$.data.grupos[0].etiqueta").value("Control"))
				.andExpect(jsonPath("$.data.grupos[1].etiqueta").value("Fluoxetina"))
				.andExpect(jsonPath("$.data.grupos[2].etiqueta").value("Dosis 1"))
				.andReturn();
		String json = r.getResponse().getContentAsString();

		// Control: dos especímenes, sus valores de nado en orden de cilindro, y la media, DE y varianza muestrales.
		assertEquals(2, (int) JsonPath.read(json, "$.data.grupos[0].n"));
		assertEquals(List.of(3, 0), JsonPath.read(json, "$.data.grupos[0].porConducta['nado activo'].valoresS"));
		assertEquals(1.5, (Double) JsonPath.read(json, "$.data.grupos[0].porConducta['nado activo'].media"), 1e-9);
		assertEquals(4.5, (Double) JsonPath.read(json, "$.data.grupos[0].porConducta['nado activo'].varianza"), 1e-9);
		assertEquals(2.1, (Double) JsonPath.read(json, "$.data.grupos[0].porConducta['nado activo'].de"), 1e-9);
		assertEquals(1.414, (Double) JsonPath.read(json, "$.data.grupos[0].porConducta['nado activo'].cv"), 1e-9);
		assertEquals(List.of(0, 5), JsonPath.read(json, "$.data.grupos[0].porConducta['sin ver'].valoresS"));

		// Las cinco categorías, «sin ver» incluido, suman la duración real de cada espécimen: G4 depende de ello.
		for (int i = 0; i < 2; i++) {
			int suma = 0;
			for (String categoria : List.of("nado activo", "inmovilidad", "escalamiento", "conducta activa", "sin ver")) {
				List<?> valores = JsonPath.read(json, "$.data.grupos[0].porConducta['" + categoria + "'].valoresS");
				suma += ((Number) valores.get(i)).intValue();
			}
			int duracion = ((Number) JsonPath.read(json, "$.data.grupos[0].especimenes[" + i + "].duracionS")).intValue();
			assertEquals(duracion, suma);
		}

		// El minuto 1 tiene menos de 60 s: se marca como incompleto y sus segundos son los reales.
		assertEquals(true, JsonPath.read(json, "$.data.grupos[0].porMinuto[0].incompleto"));
		assertEquals(5, (int) JsonPath.read(json, "$.data.grupos[0].porMinuto[0].segundosMax"));

		// Referencia: sin especímenes con datos; su tanda A aparece como faltante y no se dibuja ningún cero.
		assertEquals(0, (int) JsonPath.read(json, "$.data.grupos[1].n"));
		assertEquals(2, (int) JsonPath.read(json, "$.data.grupos[1].nTotal"));
		assertEquals("A", JsonPath.read(json, "$.data.grupos[1].faltantes[0].letra"));
		assertNull(JsonPath.read(json, "$.data.grupos[1].porConducta['nado activo'].media"));
	}

	@Test
	void sin_sesion_401_experimento_inexistente_404() throws Exception {
		mvc.perform(get(ruta(experimento))).andExpect(status().isUnauthorized());
		mvc.perform(get(ruta(9999)).header("Authorization", "Bearer " + login("creador@fst.mx"))).andExpect(status().isNotFound());
	}

	// ----------------------------------------------------------------- apoyo

	private static String ruta(int id) {
		return "/experiments/" + id + "/results/charts";
	}

	private void cuenta(String id, String correo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, true, false)", id, correo, passwordEncoder.encode(CLAVE));
	}

	private int grupo(String etiqueta, String tipo) {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, ?, 'agua')", experimento, etiqueta, tipo);
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

	private int observacion(int tanda, int cilindro) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, ?)", tanda, cilindro);
		int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = ?",
				Integer.class, tanda, cilindro);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
		return jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idespecimen = ?", Integer.class, especimen);
	}

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
