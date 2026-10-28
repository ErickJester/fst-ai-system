package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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

// Encargo 46: GET /experiments/{id}/status con un análisis «en pausa» y su analisis/{id}/pausa.json (campo extra
// pausa.desdeSegundo). Mismas condiciones que BloquesDeResultadosTest: solo corre con FST_TEST_DB_URL apuntando a una base de
// pruebas VACÍA con el esquema de db/init (cada prueba borra todos los datos). Los archivos van a una carpeta temporal propia.
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-estado-pausa"
})
@AutoConfigureMockMvc
class EstadoConPausaTest {

	private static final String CLAVE = "clave-de-prueba-1";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Path raiz;
	private int experimento;
	private int analisis;

	@BeforeEach
	void base_limpia() throws Exception {
		raiz = Path.of(System.getProperty("java.io.tmpdir"), "fst-pruebas-estado-pausa");
		borrarCarpeta(raiz);
		Files.createDirectories(raiz);

		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES ('1', 'Nombre', 'Apellidos', 'creador@fst.mx', ?, true, false)", passwordEncoder.encode(CLAVE));
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Exp', current_date)");
		experimento = jdbc.queryForObject("SELECT idexperimento FROM experimento", Integer.class);
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, 'Control', 'control', 'agua')",
				experimento);
		int grupo = jdbc.queryForObject("SELECT idgrupo FROM grupo", Integer.class);
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 1, 2)", grupo);
		int tanda = jdbc.queryForObject("SELECT idtanda FROM tanda", Integer.class);
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/x.mp4', 300)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video", Integer.class);
		jdbc.update("INSERT INTO analisis (idvideo, estado, etapa) VALUES (?, 'en pausa'::estado_analisis, 'cilindros'::etapa_analisis)",
				video);
		analisis = jdbc.queryForObject("SELECT idanalisis FROM analisis", Integer.class);
	}

	@AfterEach
	void limpia_archivos() throws Exception {
		borrarCarpeta(raiz);
	}

	@Test
	void con_pausa_json_devuelve_cilindro_desde_segundo_y_el_plazo_que_queda() throws Exception {
		escribirPausa("{\"cilindro\": 2, \"desde_segundo\": 137, \"hora_pausa\": \"" + OffsetDateTime.now().minusHours(2) + "\"}");

		MvcResult r = mvc.perform(get(ruta()).header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analisis[0].estado").value("en pausa"))
				.andExpect(jsonPath("$.data.analisis[0].pausa.cilindro").value(2))
				.andExpect(jsonPath("$.data.analisis[0].pausa.desdeSegundo").value(137))
				.andReturn();

		// Plazo de 72 h (PAUSE_TIMEOUT_HOURS por omisión) menos las 2 h que lleva en pausa: 70 h, con un margen de un minuto.
		long restantes = ((Number) JsonPath.read(r.getResponse().getContentAsString(),
				"$.data.analisis[0].pausa.segundosRestantes")).longValue();
		long esperado = 70L * 3600;
		assertEquals(true, Math.abs(restantes - esperado) < 60, "segundosRestantes = " + restantes);
	}

	@Test
	void sin_pausa_json_la_pausa_es_null_y_el_estado_sigue_respondiendo_200() throws Exception {
		mvc.perform(get(ruta()).header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analisis[0].estado").value("en pausa"))
				.andExpect(jsonPath("$.data.analisis[0].pausa").value((Object) null));
	}

	@Test
	void con_pausa_json_danado_la_pausa_es_null_y_no_se_cae_el_estado() throws Exception {
		escribirPausa("{\"cilindro\": 2, \"desde_segundo\": 13");     // cortado a la mitad

		mvc.perform(get(ruta()).header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analisis[0].pausa").value((Object) null));
	}

	// JSON válido pero sin hora_pausa: no se puede calcular el plazo, así que también queda en null (hoy el código lo trata
	// como archivo roto). Documenta el comportamiento actual.
	@Test
	void con_pausa_json_sin_hora_pausa_la_pausa_tambien_es_null() throws Exception {
		escribirPausa("{\"cilindro\": 2, \"desde_segundo\": 137}");

		mvc.perform(get(ruta()).header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analisis[0].pausa").value((Object) null));
	}

	// Un análisis que no está en pausa no lee pausa.json aunque el archivo exista (quedó de una pausa anterior).
	@Test
	void un_analisis_que_no_esta_en_pausa_trae_pausa_null_aunque_exista_el_archivo() throws Exception {
		escribirPausa("{\"cilindro\": 1, \"desde_segundo\": 50, \"hora_pausa\": \"" + OffsetDateTime.now() + "\"}");
		jdbc.update("UPDATE analisis SET estado = 'en cola'::estado_analisis");

		mvc.perform(get(ruta()).header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analisis[0].estado").value("en cola"))
				.andExpect(jsonPath("$.data.analisis[0].pausa").value((Object) null));
	}

	// ----------------------------------------------------------------- apoyo

	private String ruta() {
		return "/experiments/" + experimento + "/status";
	}

	private void escribirPausa(String contenido) throws Exception {
		Path carpeta = raiz.resolve("analisis").resolve(String.valueOf(analisis));
		Files.createDirectories(carpeta);
		Files.writeString(carpeta.resolve("pausa.json"), contenido);
	}

	private String login() throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"creador@fst.mx\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

	private static void borrarCarpeta(Path carpeta) throws Exception {
		if (!Files.exists(carpeta)) {
			return;
		}
		try (Stream<Path> rutas = Files.walk(carpeta)) {
			for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(ruta);
			}
		}
	}

}
