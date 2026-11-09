package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

// Los segundos de una tanda y el Excel de todo el experimento, contra una PostgreSQL de verdad. Solo corre con
// FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el esquema de db/init (cada prueba borra todos los datos).
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-segundos"
})
@AutoConfigureMockMvc
class SegundosYExcelTest {

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
	private int control;

	@BeforeEach
	void base_limpia() {
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		cuenta("1", "creador@fst.mx");
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Exp', current_date)");
		experimento = jdbc.queryForObject("SELECT idexperimento FROM experimento", Integer.class);
		control = grupo("Control", "control");
		int referencia = grupo("Referencia", "referencia");

		// Control, tanda A completada: el espécimen 1 nada 3 s, se inmoviliza 2 (el último, con origen dudosa); el 2 no se ve.
		int tandaA = tanda(control, 1, "completado");
		int obs1 = observacion(tandaA, 1);
		segundos(obs1, new String[] {N, N, N, I, null}, new String[] {"maquina", "maquina", "maquina", "corregida", "dudosa"});
		segundos(observacion(tandaA, 2), new String[] {null, null, null, null, null}, new String[] {"maquina", "maquina", "maquina", "maquina", "maquina"});

		// Referencia: tanda A todavía procesando; no entra en ningún reporte.
		tanda(referencia, 1, "procesando");
	}

	@Test
	void los_segundos_de_una_tanda_salen_con_conducta_origen_y_sin_ver_null() throws Exception {
		MvcResult r = mvc.perform(get(ruta(experimento, control, "A")).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.duracionS").value(5))
				.andExpect(jsonPath("$.data.grupo.etiqueta").value("Control"))
				.andExpect(jsonPath("$.data.letra").value("A"))
				.andExpect(jsonPath("$.data.especimenes.length()").value(2))
				.andReturn();
		String json = r.getResponse().getContentAsString();
		assertEquals(N, JsonPath.read(json, "$.data.especimenes[0].segundos[0].conducta"));
		assertEquals("corregida", JsonPath.read(json, "$.data.especimenes[0].segundos[3].origen"));
		assertEquals("dudosa", JsonPath.read(json, "$.data.especimenes[0].segundos[4].origen"));
		org.junit.jupiter.api.Assertions.assertNull(JsonPath.read(json, "$.data.especimenes[0].segundos[4].conducta"));
	}

	@Test
	void una_tanda_sin_analisis_completado_responde_404_y_falta_la_tanda_responde_400() throws Exception {
		String token = login("creador@fst.mx");
		int referencia = jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE etiqueta = 'Referencia'", Integer.class);
		mvc.perform(get(ruta(experimento, referencia, "A")).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
		mvc.perform(get("/experiments/" + experimento + "/results/seconds?grupo=" + control).header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());
	}

	@Test
	void el_excel_del_experimento_tiene_las_dos_hojas() throws Exception {
		byte[] xlsx = mvc.perform(get("/experiments/" + experimento + "/report/excel").header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertEquals("Resultados", libro.getSheetAt(0).getSheetName());
			assertEquals("Segundo a segundo", libro.getSheetAt(1).getSheetName());
			// Cinco segundos de un espécimen y cinco del otro: diez filas bajo el encabezado.
			assertEquals(11, libro.getSheetAt(1).getPhysicalNumberOfRows());
		}
	}

	// ----------------------------------------------------------------- apoyo

	private static String ruta(int experimento, int grupo, String tanda) {
		return "/experiments/" + experimento + "/results/seconds?grupo=" + grupo + "&tanda=" + tanda;
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
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/x.mp4', 5)", tanda);
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

	private void segundos(int observacion, String[] conductas, String[] origenes) {
		for (int i = 0; i < conductas.length; i++) {
			jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, ?, ?::tipo_conducta, ?::origen_segundo)",
					observacion, i + 1, conductas[i], origenes[i]);
		}
	}

	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}
}
