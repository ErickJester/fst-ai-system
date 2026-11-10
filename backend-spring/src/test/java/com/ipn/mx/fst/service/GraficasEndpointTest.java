// Mismo paquete que las clases que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
// Los constructores de peticiones simuladas y los comprobadores de la respuesta.
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
//
// Esa última frase marca el reparto: GraficasDeResultadosTest comprueba las CUENTAS (sin base, en
// milisegundos) y esta comprueba que lleguen bien hasta el JSON, pasando por la consulta SQL de verdad,
// la seguridad y la serialización. Las dos juntas cubren el camino entero sin duplicarse.
// La estructura de prueba de integración (las anotaciones, el @BeforeEach, los métodos de apoyo) está
// explicada a fondo en SegundosYExcelTest.
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		// Una carpeta propia, distinta de la de las otras pruebas, para que no se estorben.
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
		// De las hojas hacia arriba, por las llaves foráneas.
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		cuenta("1", "creador@fst.mx");
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Exp', current_date)");
		experimento = jdbc.queryForObject("SELECT idexperimento FROM experimento", Integer.class);

		// Se insertan fuera de orden: la respuesta debe salir control, referencia y luego el experimental.
		// Es la comprobación del orden de las gráficas hecha desde los datos, no desde una lista en memoria.
		grupo("Dosis 1", "tratamiento experimental");
		int control = grupo("Control", "control");
		int referencia = grupo("Fluoxetina", "referencia");

		// Control, tanda A completada con dos especímenes: el 1 nada 3 s y se inmoviliza 2; el 2 no se ve en 5 s.
		// Dos especímenes muy distintos a propósito: así la media y la desviación salen con valores que se
		// pueden comprobar a mano, y el segundo cubre el caso de un espécimen entero sin ver.
		int tandaA = tanda(control, 1, "completado");
		int obs1 = observacion(tandaA, 1);
		segundos(obs1, N, N, N, I, I);
		segundos(observacion(tandaA, 2), null, null, null, null, null);

		// Referencia: tanda A todavía procesando, así que entra como faltante. Experimental: sin videos.
		// Los dos son casos negativos: comprueban que un grupo sin datos sale igualmente, con su aviso.
		tanda(referencia, 1, "procesando");
	}

	@Test
	@Tag("trabajo-terminal")
	void da_los_grupos_en_orden_con_sus_faltantes_y_sus_estadisticos() throws Exception {
		MvcResult r = mvc.perform(get(ruta(experimento)).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.dia").value(2))
				// Los TRES grupos salen, incluidos los que no tienen datos.
				.andExpect(jsonPath("$.data.grupos.length()").value(3))
				// Y en el orden de las gráficas, aunque se insertaron al revés.
				.andExpect(jsonPath("$.data.grupos[0].etiqueta").value("Control"))
				.andExpect(jsonPath("$.data.grupos[1].etiqueta").value("Fluoxetina"))
				.andExpect(jsonPath("$.data.grupos[2].etiqueta").value("Dosis 1"))
				.andReturn();
		String json = r.getResponse().getContentAsString();

		// Control: dos especímenes, sus valores de nado en orden de cilindro, y la media, DE y varianza muestrales.
		assertEquals(2, (int) JsonPath.read(json, "$.data.grupos[0].n"));
		// 3 del primer espécimen y 0 del segundo: el 0 SÍ cuenta, porque "no nadó" es un dato real.
		assertEquals(List.of(3, 0), JsonPath.read(json, "$.data.grupos[0].porConducta['nado activo'].valoresS"));
		// Media de 3 y 0 = 1,5; varianza muestral (n − 1) = 4,5; su raíz redondeada = 2,1.
		assertEquals(1.5, (Double) JsonPath.read(json, "$.data.grupos[0].porConducta['nado activo'].media"), 1e-9);
		assertEquals(4.5, (Double) JsonPath.read(json, "$.data.grupos[0].porConducta['nado activo'].varianza"), 1e-9);
		assertEquals(2.1, (Double) JsonPath.read(json, "$.data.grupos[0].porConducta['nado activo'].de"), 1e-9);
		// El CV con tres decimales: 2,1213 ÷ 1,5 = 1,414.
		assertEquals(1.414, (Double) JsonPath.read(json, "$.data.grupos[0].porConducta['nado activo'].cv"), 1e-9);
		// Y el espécimen que no se vio aporta sus 5 segundos a la categoría «sin ver».
		assertEquals(List.of(0, 5), JsonPath.read(json, "$.data.grupos[0].porConducta['sin ver'].valoresS"));

		// Las cinco categorías, «sin ver» incluido, suman la duración real de cada espécimen: G4 depende de ello.
		//
		// Es una comprobación de COHERENCIA, no de valores concretos: recorre las cinco categorías de cada
		// espécimen y exige que sumen su duración. Si alguna se perdiera por el camino, la barra apilada
		// de G4 saldría corta sin que nada más lo delatara.
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
		// Los videos de prueba duran 5 segundos, así que su único minuto está incompleto. Comprueba la
		// regla de no completar hasta 60: el máximo es 5, no 60.
		assertEquals(true, JsonPath.read(json, "$.data.grupos[0].porMinuto[0].incompleto"));
		assertEquals(5, (int) JsonPath.read(json, "$.data.grupos[0].porMinuto[0].segundosMax"));

		// Referencia: sin especímenes con datos; su tanda A aparece como faltante y no se dibuja ningún cero.
		// n = 0 pero nTotal = 2: la gráfica puede decir "faltan 2 especímenes" en vez de omitir el grupo.
		assertEquals(0, (int) JsonPath.read(json, "$.data.grupos[1].n"));
		assertEquals(2, (int) JsonPath.read(json, "$.data.grupos[1].nTotal"));
		assertEquals("A", JsonPath.read(json, "$.data.grupos[1].faltantes[0].letra"));
		// Y la media va en NULL, no en 0: un cero afirmaría que el grupo no nadó, cuando en realidad
		// todavía no se ha medido.
		assertNull(JsonPath.read(json, "$.data.grupos[1].porConducta['nado activo'].media"));
	}

	@Test
	void sin_sesion_401_experimento_inexistente_404() throws Exception {
		// Sin la cabecera Authorization: 401, puesto por la cadena de seguridad antes de llegar al
		// controller. Es la comprobación de que la ruta no quedó abierta por descuido.
		mvc.perform(get(ruta(experimento))).andExpect(status().isUnauthorized());
		// Y con sesión pero un id que no existe: 404 del servicio.
		mvc.perform(get(ruta(9999)).header("Authorization", "Bearer " + login("creador@fst.mx"))).andExpect(status().isNotFound());
	}

	// ----------------------------------------------------------------- apoyo
	//
	// Arman los datos directamente en la base. Son casi idénticos a los de SegundosYExcelTest, donde están
	// comentados uno a uno; se repiten en cada prueba para que cada una sea independiente y se pueda leer
	// entera sin saltar a otro archivo.

	private static String ruta(int id) {
		return "/experiments/" + id + "/results/charts";
	}

	// Una cuenta con su contraseña cifrada con el mismo bcrypt de la aplicación.
	private void cuenta(String id, String correo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, true, false)", id, correo, passwordEncoder.encode(CLAVE));
	}

	// Un grupo, devolviendo su id recién asignado.
	private int grupo(String etiqueta, String tipo) {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, ?, 'agua')", experimento, etiqueta, tipo);
		return jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE etiqueta = ?", Integer.class, etiqueta);
	}

	// Una tanda con su video del Día 2 y su análisis en el estado que se pida. La duración del video (12)
	// no coincide con los segundos insertados: aquí no importa, porque las gráficas cuentan los segundos
	// de la tabla, no la duración declarada.
	private int tanda(int grupo, int ordinal, String estado) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, 2)", grupo, ordinal);
		int tanda = jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, grupo, ordinal);
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/x.mp4', 12)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, ?::estado_analisis)", video, estado);
		return tanda;
	}

	// Un espécimen y su observación en el video de esa tanda.
	private int observacion(int tanda, int cilindro) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, ?)", tanda, cilindro);
		int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = ?",
				Integer.class, tanda, cilindro);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
		return jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idespecimen = ?", Integer.class, especimen);
	}

	// Los segundos de una observación, numerados desde 1 y todos con origen 'maquina'.
	// String... admite número variable de argumentos: segundos(obs, N, N, I) se lee de un vistazo.
	// Un null es el «sin ver».
	private void segundos(int observacion, String... conductas) {
		for (int i = 0; i < conductas.length; i++) {
			jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, ?, ?::tipo_conducta, 'maquina')",
					observacion, i + 1, conductas[i]);
		}
	}

	// Inicia sesión de verdad por la API y devuelve el token.
	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

}
