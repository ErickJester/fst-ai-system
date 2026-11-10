// Mismo paquete que las clases que prueba, dentro de src/test.
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
// @AfterEach: un método que corre DESPUÉS de cada prueba (lo contrario de @BeforeEach).
import org.junit.jupiter.api.AfterEach;
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

// Encargo 46: GET /experiments/{id}/status con un análisis «en pausa» y su analisis/{id}/pausa.json (campo extra
// pausa.desdeSegundo). Mismas condiciones que BloquesDeResultadosTest: solo corre con FST_TEST_DB_URL apuntando a una base de
// pruebas VACÍA con el esquema de db/init (cada prueba borra todos los datos). Los archivos van a una carpeta temporal propia.
//
// Es una prueba de integración (la estructura general está explicada en SegundosYExcelTest). Lo particular
// de esta es que combina DOS fuentes de verdad: la base (el estado 'en pausa') y un archivo del volumen
// (pausa.json, que escribe el worker). Por eso prepara las dos y limpia la carpeta al terminar.
// Lo que comprueba es sobre todo la TOLERANCIA a fallos: archivo ausente, roto o incompleto no deben
// tumbar la pantalla de progreso (ver ResultadosServiceImpl.pausa).
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		// Una carpeta propia, para no pisar los archivos de otras pruebas.
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

	// La carpeta del volumen de la prueba, y los ids que arma el @BeforeEach.
	private Path raiz;
	private int experimento;
	private int analisis;

	@BeforeEach
	void base_limpia() throws Exception {
		// La MISMA ruta que la propiedad app.archivos.raiz de arriba: así lo que la prueba escribe es lo
		// que el servicio lee.
		raiz = Path.of(System.getProperty("java.io.tmpdir"), "fst-pruebas-estado-pausa");
		// Se borra y se vuelve a crear: una carpeta limpia por prueba, sin restos de la anterior.
		borrarCarpeta(raiz);
		Files.createDirectories(raiz);

		// De las hojas hacia arriba, por las llaves foráneas.
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		// Un usuario, un experimento, un grupo, una tanda, un video y un análisis: la cadena mínima.
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES ('1', 'Nombre', 'Apellidos', 'creador@fst.mx', ?, true, false)", passwordEncoder.encode(CLAVE));
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Exp', current_date)");
		experimento = jdbc.queryForObject("SELECT idexperimento FROM experimento", Integer.class);
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, 'Control', 'control', 'agua')",
				experimento);
		// Como la base está vacía antes de cada prueba, cada SELECT sin WHERE devuelve exactamente la
		// única fila que se acaba de insertar.
		int grupo = jdbc.queryForObject("SELECT idgrupo FROM grupo", Integer.class);
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 1, 2)", grupo);
		int tanda = jdbc.queryForObject("SELECT idtanda FROM tanda", Integer.class);
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/x.mp4', 300)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video", Integer.class);
		// El análisis nace 'en pausa' y en la etapa 'cilindros', que es donde el worker se atasca al no
		// ubicar un tubo. Los ::tipo son las conversiones a los dominios de PostgreSQL.
		jdbc.update("INSERT INTO analisis (idvideo, estado, etapa) VALUES (?, 'en pausa'::estado_analisis, 'cilindros'::etapa_analisis)",
				video);
		analisis = jdbc.queryForObject("SELECT idanalisis FROM analisis", Integer.class);
	}

	// Corre al terminar CADA prueba, pase o falle: deja la carpeta temporal limpia.
	@AfterEach
	void limpia_archivos() throws Exception {
		borrarCarpeta(raiz);
	}

	@Test
	@Tag("trabajo-terminal")
	void con_pausa_json_devuelve_cilindro_desde_segundo_y_el_plazo_que_queda() throws Exception {
		// El archivo tal como lo escribiría el worker, con la pausa empezada hace dos horas.
		escribirPausa("{\"cilindro\": 2, \"desde_segundo\": 137, \"hora_pausa\": \"" + OffsetDateTime.now().minusHours(2) + "\"}");

		MvcResult r = mvc.perform(get(ruta()).header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analisis[0].estado").value("en pausa"))
				// El cambio de nombres de la API: desde_segundo (archivo) pasa a desdeSegundo (JSON).
				.andExpect(jsonPath("$.data.analisis[0].pausa.cilindro").value(2))
				.andExpect(jsonPath("$.data.analisis[0].pausa.desdeSegundo").value(137))
				.andReturn();

		// Plazo de 72 h (PAUSE_TIMEOUT_HOURS por omisión) menos las 2 h que lleva en pausa: 70 h, con un margen de un minuto.
		//
		// El margen es necesario porque entre escribir el archivo y consultarlo pasan unos milisegundos:
		// comparar con == fallaría casi siempre. Un minuto es holgado para el retraso de la prueba y
		// estrecho para detectar un error de verdad (como confundir horas con minutos).
		long restantes = ((Number) JsonPath.read(r.getResponse().getContentAsString(),
				"$.data.analisis[0].pausa.segundosRestantes")).longValue();
		long esperado = 70L * 3600;
		// El tercer argumento es el mensaje que se imprime si falla: enseña el valor real.
		assertEquals(true, Math.abs(restantes - esperado) < 60, "segundosRestantes = " + restantes);
	}

	@Test
	void sin_pausa_json_la_pausa_es_null_y_el_estado_sigue_respondiendo_200() throws Exception {
		// No se escribe ningún archivo. Es lo que pasa si el volumen se vació o el worker no llegó a
		// escribirlo: la pantalla de progreso debe seguir funcionando, solo sin el detalle de la pausa.
		// value((Object) null) comprueba que el campo es null; el cast aclara a Java que se quiere el
		// método que recibe un Object.
		mvc.perform(get(ruta()).header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analisis[0].estado").value("en pausa"))
				.andExpect(jsonPath("$.data.analisis[0].pausa").value((Object) null));
	}

	@Test
	void con_pausa_json_danado_la_pausa_es_null_y_no_se_cae_el_estado() throws Exception {
		escribirPausa("{\"cilindro\": 2, \"desde_segundo\": 13");     // cortado a la mitad
		// Es el caso de leer el archivo justo mientras el worker lo escribe: JSON incompleto. El servicio
		// lo atrapa y sigue, en vez de responder 500 por un archivo auxiliar.

		mvc.perform(get(ruta()).header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analisis[0].pausa").value((Object) null));
	}

	// JSON válido pero sin hora_pausa: no se puede calcular el plazo, así que también queda en null (hoy el código lo trata
	// como archivo roto). Documenta el comportamiento actual.
	//
	// "Documenta el comportamiento actual" es una distinción honesta sobre el propósito de la prueba: no
	// afirma que esto sea lo ideal, sino que es lo que ocurre hoy. Si algún día se decidiera mostrar la
	// pausa sin plazo, esta prueba se cambiaría a propósito.
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
		// Se cambia el estado DESPUÉS de escribir el archivo, para reproducir un análisis que se pausó,
		// se reanudó y volvió a la cola dejando el archivo atrás.
		jdbc.update("UPDATE analisis SET estado = 'en cola'::estado_analisis");

		mvc.perform(get(ruta()).header("Authorization", "Bearer " + login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analisis[0].estado").value("en cola"))
				// Sin esto, la pantalla enseñaría una cuenta atrás de una pausa que ya terminó.
				.andExpect(jsonPath("$.data.analisis[0].pausa").value((Object) null));
	}

	// ----------------------------------------------------------------- apoyo

	private String ruta() {
		return "/experiments/" + experimento + "/status";
	}

	// Escribe pausa.json donde el servicio lo busca: analisis/{idAnalisis}/pausa.json dentro de la raíz.
	// createDirectories crea la carpeta del análisis, que no existe hasta que algo la necesita.
	private void escribirPausa(String contenido) throws Exception {
		Path carpeta = raiz.resolve("analisis").resolve(String.valueOf(analisis));
		Files.createDirectories(carpeta);
		Files.writeString(carpeta.resolve("pausa.json"), contenido);
	}

	// Inicia sesión por la API y devuelve el token.
	private String login() throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"creador@fst.mx\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

	// Borra una carpeta con todo lo que tiene dentro. Es el mismo truco de RevisionServiceImpl.limpiarCarpeta:
	// ordenar las rutas AL REVÉS hace que las más profundas se borren primero, así cada carpeta ya está
	// vacía cuando le toca.
	private static void borrarCarpeta(Path carpeta) throws Exception {
		if (!Files.exists(carpeta)) {
			return;
		}
		// try-con-recursos: Files.walk abre descriptores del sistema y hay que cerrarlos.
		try (Stream<Path> rutas = Files.walk(carpeta)) {
			for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(ruta);
			}
		}
	}

}
