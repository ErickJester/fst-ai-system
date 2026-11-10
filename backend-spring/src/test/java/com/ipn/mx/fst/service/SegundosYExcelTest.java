// Mismo paquete que las clases que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
// Los constructores de peticiones simuladas de MockMvc: GET y POST.
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
// Los comprobadores de la respuesta: por ruta JSON y por código HTTP.
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// JsonPath: leer un valor del JSON con una expresión como "$.data.token".
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
// @BeforeEach: un método que corre ANTES de cada prueba.
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
// @EnabledIfEnvironmentVariable: la prueba solo corre si existe esa variable de entorno.
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
// @SpringBootTest: levanta la aplicación entera para la prueba.
import org.springframework.boot.test.context.SpringBootTest;
// @AutoConfigureMockMvc: prepara MockMvc, que llama a la API sin abrir un puerto de red.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Los segundos de una tanda y el Excel de todo el experimento, contra una PostgreSQL de verdad. Solo corre con
// FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el esquema de db/init (cada prueba borra todos los datos).
//
// Es una prueba de INTEGRACIÓN, no unitaria: levanta Spring entero, habla con PostgreSQL de verdad y
// llama a la API por HTTP (simulado). Comprueba que todas las piezas encajan, cosa que una prueba
// unitaria no puede: la consulta SQL de verdad, la seguridad, el JSON que sale.
// El precio es que necesita una base, y por eso está condicionada: sin FST_TEST_DB_URL, JUnit la SALTA en
// vez de fallar. Así `./mvnw test` funciona en cualquier máquina, y estas se ejecutan solo donde toca.
// La advertencia de la base VACÍA es seria: el @BeforeEach borra todas las tablas. Apuntarla a la base de
// desarrollo destruiría los datos de trabajo.
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
// Las propiedades sobrescriben las de application.properties solo para esta prueba: otra base, un secreto
// fijo (el real no estaría disponible aquí) y una carpeta temporal del sistema para los archivos.
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		// ${...:valor} es el valor por omisión si la variable no existe.
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		// El secreto tiene más de 32 caracteres, como exige SeguridadConfig para arrancar.
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-segundos"
})
@AutoConfigureMockMvc
class SegundosYExcelTest {

	private static final String CLAVE = "clave-de-prueba-1";
	// Abreviaturas de las conductas, para que los datos de prueba se lean.
	private static final String N = "nado activo";
	private static final String I = "inmovilidad";

	// MockMvc llama a los controllers sin abrir un puerto: pasa por toda la cadena (seguridad, filtros,
	// serialización) pero sin red, así que es mucho más rápido que un cliente HTTP de verdad.
	@Autowired
	private MockMvc mvc;

	// Para preparar los datos directamente en la base, sin pasar por la API.
	@Autowired
	private JdbcTemplate jdbc;

	// El mismo bcrypt de la aplicación: las contraseñas de prueba tienen que estar cifradas igual para
	// que el login funcione.
	@Autowired
	private PasswordEncoder passwordEncoder;

	private int experimento;
	private int control;

	// Corre antes de CADA prueba: deja la base en un estado conocido. Sin esto, una prueba vería los datos
	// que dejó otra y el resultado dependería del orden de ejecución.
	@BeforeEach
	void base_limpia() {
		// El ORDEN del borrado es obligatorio: de las hojas hacia arriba, por las llaves foráneas. Es el
		// mismo orden que sigue ExperimentoServiceImpl.borrarDesdeLosVideos.
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
		// Los datos están pensados para cubrir los casos interesantes de una vez: una conducta normal, una
		// corregida a mano, un segundo dudoso y un espécimen entero sin ver.
		int tandaA = tanda(control, 1, "completado");
		int obs1 = observacion(tandaA, 1);
		segundos(obs1, new String[] {N, N, N, I, null}, new String[] {"maquina", "maquina", "maquina", "corregida", "dudosa"});
		segundos(observacion(tandaA, 2), new String[] {null, null, null, null, null}, new String[] {"maquina", "maquina", "maquina", "maquina", "maquina"});

		// Referencia: tanda A todavía procesando; no entra en ningún reporte.
		// Es el control negativo de la prueba: comprueba que los análisis a medias se descartan.
		tanda(referencia, 1, "procesando");
	}

	@Test
	void los_segundos_de_una_tanda_salen_con_conducta_origen_y_sin_ver_null() throws Exception {
		// La petición, con su token en la cabecera: pasa por toda la cadena de seguridad de verdad.
		MvcResult r = mvc.perform(get(ruta(experimento, control, "A")).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk())
				// jsonPath comprueba valores dentro del JSON sin convertirlo a objetos.
				// Se encadenan varias comprobaciones sobre la misma respuesta.
				.andExpect(jsonPath("$.data.duracionS").value(5))
				.andExpect(jsonPath("$.data.grupo.etiqueta").value("Control"))
				.andExpect(jsonPath("$.data.letra").value("A"))
				// Los dos especímenes de la tanda, incluido el que no se ve en ningún segundo.
				.andExpect(jsonPath("$.data.especimenes.length()").value(2))
				.andReturn();
		String json = r.getResponse().getContentAsString();
		assertEquals(N, JsonPath.read(json, "$.data.especimenes[0].segundos[0].conducta"));
		// El origen se conserva crudo: "corregida" y "dudosa" son los que escribe una persona al revisar.
		assertEquals("corregida", JsonPath.read(json, "$.data.especimenes[0].segundos[3].origen"));
		assertEquals("dudosa", JsonPath.read(json, "$.data.especimenes[0].segundos[4].origen"));
		// Y el «sin ver» llega como null, no como texto: la pantalla sabe distinguirlo.
		// Se escribe con la ruta completa en vez de importar assertNull porque es el único uso.
		org.junit.jupiter.api.Assertions.assertNull(JsonPath.read(json, "$.data.especimenes[0].segundos[4].conducta"));
	}

	@Test
	void una_tanda_sin_analisis_completado_responde_404_y_falta_la_tanda_responde_400() throws Exception {
		String token = login("creador@fst.mx");
		int referencia = jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE etiqueta = 'Referencia'", Integer.class);
		// La tanda del grupo Referencia existe, pero su análisis está 'procesando': 404.
		mvc.perform(get(ruta(experimento, referencia, "A")).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
		// Y sin el parámetro tanda: 400, no 500. Es justo el motivo de que el controller lo declare
		// opcional (ver SegundosController).
		mvc.perform(get("/experiments/" + experimento + "/results/seconds?grupo=" + control).header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());
	}

	@Test
	// Marcada para poder enseñarla en la defensa del trabajo terminal.
	@Tag("trabajo-terminal")
	void el_excel_del_experimento_tiene_las_dos_hojas() throws Exception {
		// getContentAsByteArray: la respuesta son los bytes de un archivo, no JSON.
		byte[] xlsx = mvc.perform(get("/experiments/" + experimento + "/report/excel").header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		// Y se abre con POI para comprobar lo que quedó dentro, igual que en ExcelDelExperimentoTest pero
		// con datos que vinieron de la base de verdad.
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertEquals("Resultados", libro.getSheetAt(0).getSheetName());
			assertEquals("Segundo a segundo", libro.getSheetAt(1).getSheetName());
			// Cinco segundos de un espécimen y cinco del otro: diez filas bajo el encabezado.
			// El conteo comprueba de paso que la tanda 'procesando' del grupo Referencia NO entró.
			assertEquals(11, libro.getSheetAt(1).getPhysicalNumberOfRows());
		}
	}

	// ----------------------------------------------------------------- apoyo
	//
	// Los métodos de abajo arman los datos de prueba en la base. Son el equivalente a lo que haría el
	// sistema al subir videos y analizarlos, pero escrito directo con SQL: así la prueba controla el
	// estado exacto del que parte.

	private static String ruta(int experimento, int grupo, String tanda) {
		return "/experiments/" + experimento + "/results/seconds?grupo=" + grupo + "&tanda=" + tanda;
	}

	// Una cuenta con su contraseña ya cifrada con el mismo bcrypt de la aplicación.
	private void cuenta(String id, String correo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, true, false)", id, correo, passwordEncoder.encode(CLAVE));
	}

	// Un grupo, devolviendo su id recién asignado.
	private int grupo(String etiqueta, String tipo) {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, ?, 'agua')", experimento, etiqueta, tipo);
		return jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE etiqueta = ?", Integer.class, etiqueta);
	}

	// Una tanda con su video del Día 2 y su análisis en el estado que se pida. Tres inserciones en una:
	// es la cadena mínima para que una tanda tenga resultados.
	private int tanda(int grupo, int ordinal, String estado) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, 2)", grupo, ordinal);
		int tanda = jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, grupo, ordinal);
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/x.mp4', 5)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		// El ::estado_analisis es la conversión al dominio de PostgreSQL (ver la entidad Analisis).
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, ?::estado_analisis)", video, estado);
		return tanda;
	}

	// Un espécimen y su observación en el video de esa tanda: el cruce de las dos ramas del modelo.
	private int observacion(int tanda, int cilindro) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, ?)", tanda, cilindro);
		int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = ?",
				Integer.class, tanda, cilindro);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
		return jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idespecimen = ?", Integer.class, especimen);
	}

	// Los segundos de una observación, numerados desde 1. Los dos arreglos van en paralelo: conducta y
	// origen del mismo segundo. Un null en conductas es el «sin ver».
	private void segundos(int observacion, String[] conductas, String[] origenes) {
		for (int i = 0; i < conductas.length; i++) {
			jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, ?, ?::tipo_conducta, ?::origen_segundo)",
					observacion, i + 1, conductas[i], origenes[i]);
		}
	}

	// Inicia sesión de verdad por la API y devuelve el token. No se firma a mano: así la prueba comprueba
	// de paso que el login funciona y que el token que emite sirve para las demás rutas.
	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}
}
