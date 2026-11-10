package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipn.mx.fst.utilerias.Ffprobe;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
// MockMultipartFile: un archivo falso para simular la subida de un video sin tener un video real.
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
// MockitoBean: reemplaza un bean real del contexto por un doble de Mockito (aquí, Ffprobe).
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

// Crear, eliminar y subir video contra una PostgreSQL de verdad. Mismas condiciones que AdministracionYRecuperacionTest:
// corre solo con FST_TEST_DB_URL apuntando a una base VACÍA con el esquema de db/init/001_esquema.sql (cada prueba borra
// todos los datos). ffprobe está simulado (Ffprobe es un bean): no hace falta un video real. Los archivos van a un
// directorio temporal propio (fst-pruebas-crear-subir), que se vacía antes de cada prueba.
//
// Es una prueba de integración de punta a punta: entra por HTTP (MockMvc), pasa por seguridad, controlador, servicio y
// repositorio, y llega a la base y al disco. Por eso se prueba el RESULTADO (código HTTP, mensaje, filas en la base,
// archivos en disco) y no los detalles internos. Lo único falso es ffprobe, porque depende de un programa externo
// instalado en la máquina y de un video real; todo lo demás es el sistema auténtico.
@Tag("trabajo-terminal")
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		// Raíz de archivos PROPIA de esta clase: así no choca con otras pruebas que corran en el mismo equipo.
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-crear-subir",
		"app.reset.minutos=60"
})
@AutoConfigureMockMvc
class CrearEliminarSubirTest {

	// Contraseña común de todas las cuentas de prueba. Se guarda cifrada (bcrypt) para que el login real funcione.
	private static final String CLAVE = "clave-de-prueba-1";
	// Nombre de la carpeta temporal; debe coincidir con app.archivos.raiz de arriba.
	private static final String NOMBRE_RAIZ = "fst-pruebas-crear-subir";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// El Ffprobe real ejecuta el programa externo ffprobe; aquí se sustituye por un doble para decidir qué «mide».
	@MockitoBean
	private Ffprobe ffprobe;

	// Antes de CADA prueba: deja la base y el disco vacíos para que ninguna dependa de lo que dejó otra.
	@BeforeEach
	void base_limpia() throws IOException {
		// Borra lo programado en el doble de la prueba anterior (qué devolvía sondear).
		reset(ffprobe);
		// Orden de hojas hacia arriba: las llaves foráneas impiden borrar un padre con hijos.
		jdbc.update("DELETE FROM segundo");
		jdbc.update("DELETE FROM observacion");
		jdbc.update("DELETE FROM especimen");
		jdbc.update("DELETE FROM reporte");
		jdbc.update("DELETE FROM analisis");
		jdbc.update("DELETE FROM video");
		jdbc.update("DELETE FROM tanda");
		jdbc.update("DELETE FROM grupo");
		jdbc.update("DELETE FROM notificacion");
		jdbc.update("DELETE FROM experimento");
		jdbc.update("DELETE FROM administrador");
		jdbc.update("DELETE FROM usuario");
		vaciarRaiz();
	}

	// ---------------------------------------------------------------- 1. crear experimento

	@Test
	void crear_un_experimento_lo_guarda_y_aparece_en_la_lista() throws Exception {
		// Un investigador (no administrador) activo con el id institucional 2.
		cuenta("2", "inv@fst.mx", false, true);
		// Inicia sesión de verdad y obtiene el JWT que usará en las peticiones.
		String token = login("inv@fst.mx");

		mvc.perform(conToken(post("/experiments"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Exp creado\",\"fecha\":\"2026-11-01\",\"notas\":\"prueba\"}"))
				// 201 Created: se creó un recurso nuevo.
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.idExperimento").isNumber());

		// Se verifica directo en la base que el experimento quedó a nombre de quien lo creó y con su fecha.
		assertEquals("2", jdbc.queryForObject("SELECT idinstitucional FROM experimento WHERE nombre = 'Exp creado'", String.class));
		assertEquals("2026-11-01", jdbc.queryForObject("SELECT fecha::text FROM experimento WHERE nombre = 'Exp creado'", String.class));
		// Y que la lista de experimentos del investigador ya lo incluye.
		mvc.perform(conToken(get("/experiments"), token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].nombre").value("Exp creado"));
	}

	@Test
	void un_nombre_repetido_responde_409_con_su_mensaje() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		String token = login("inv@fst.mx");
		// Ya existe un experimento con ese nombre (el nombre es único en todo el sistema, RF-11).
		experimento("2", "Exp repetido");

		mvc.perform(conToken(post("/experiments"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Exp repetido\",\"fecha\":\"2026-11-01\"}"))
				// 409 Conflict: la petición choca con el estado actual de los datos.
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Ya existe un experimento con el nombre 'Exp repetido'"));
		// No se creó un segundo.
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM experimento", Integer.class));
	}

	@Test
	void un_nombre_vacio_o_una_fecha_invalida_responden_400() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		String token = login("inv@fst.mx");

		// Tres entradas malas distintas: nombre vacío, fecha ausente y fecha que no es fecha. Las tres son 400
		// (Bad Request): el error es del cliente y la validación del DTO la corta antes de llegar a la base.
		mvc.perform(conToken(post("/experiments"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"\",\"fecha\":\"2026-11-01\"}")).andExpect(status().isBadRequest());
		mvc.perform(conToken(post("/experiments"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Exp sin fecha\"}")).andExpect(status().isBadRequest());
		mvc.perform(conToken(post("/experiments"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Exp fecha mala\",\"fecha\":\"no-es-fecha\"}")).andExpect(status().isBadRequest());
		// Ninguna de las tres dejó nada.
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM experimento", Integer.class));
	}

	@Test
	void crear_sin_sesion_responde_401() throws Exception {
		// Sin cabecera Authorization: Spring Security responde 401 (no autenticado) antes de llegar al controlador.
		mvc.perform(post("/experiments").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Exp anonimo\",\"fecha\":\"2026-11-01\"}")).andExpect(status().isUnauthorized());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM experimento", Integer.class));
	}

	// ---------------------------------------------------------------- 2. eliminar experimento

	@Test
	void el_creador_puede_eliminar_su_experimento() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp propio");

		// 204 No Content: se borró y no hay nada que devolver.
		mvc.perform(conToken(delete("/experiments/" + experimento), login("inv@fst.mx"))).andExpect(status().isNoContent());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class, experimento));
	}

	@Test
	void un_administrador_puede_eliminar_el_experimento_de_otro() throws Exception {
		// Id 1 es administrador (fila en la tabla administrador); el experimento es de la cuenta 2.
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp ajeno");

		mvc.perform(conToken(delete("/experiments/" + experimento), login("admin@fst.mx"))).andExpect(status().isNoContent());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class, experimento));
	}

	@Test
	void otro_investigador_recibe_403_y_no_se_borra_nada() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		cuenta("3", "otro@fst.mx", false, true);
		int experimento = experimento("2", "Exp de inv");
		int grupo = grupo(experimento, "Control");
		tanda(grupo, 1);

		// El investigador 3 no es el creador ni administrador: 403 Forbidden (sabemos quién es, pero no puede).
		mvc.perform(conToken(delete("/experiments/" + experimento), login("otro@fst.mx")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Solo quien creó el experimento o un Administrador puede eliminarlo"));
		// Ni el experimento ni sus grupos ni sus tandas se tocaron: el permiso se revisa ANTES de borrar nada.
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class, experimento));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE idexperimento = ?", Integer.class, experimento));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tanda", Integer.class));
	}

	@Test
	void con_un_analisis_procesando_responde_409_y_no_borra_nada() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp procesando");
		analisis(tanda(grupo(experimento, "Control"), 1), "procesando");

		// Mientras el worker está analizando, borrar dejaría al worker escribiendo sobre filas que ya no existen.
		mvc.perform(conToken(delete("/experiments/" + experimento), login("inv@fst.mx")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("No se puede eliminar mientras un análisis está procesando o en pausa"));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class, experimento));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM analisis WHERE estado = 'procesando'", Integer.class));
	}

	@Test
	void con_un_analisis_en_pausa_responde_409_y_no_borra_nada() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp en pausa");
		analisis(tanda(grupo(experimento, "Control"), 1), "en pausa");

		// Lo mismo con «en pausa»: el análisis sigue vivo en el worker y se puede reanudar.
		mvc.perform(conToken(delete("/experiments/" + experimento), login("inv@fst.mx")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("No se puede eliminar mientras un análisis está procesando o en pausa"));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM experimento WHERE idexperimento = ?", Integer.class, experimento));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM analisis WHERE estado = 'en pausa'", Integer.class));
	}

	@Test
	void al_eliminar_desaparecen_sus_grupos_tandas_y_notificaciones_y_los_de_otro_experimento_quedan() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		// Dos experimentos del mismo investigador, cada uno con su grupo, su tanda y su notificación.
		int borrar = experimento("2", "Exp a borrar");
		int conservar = experimento("2", "Exp a conservar");
		tanda(grupo(borrar, "Control"), 1);
		tanda(grupo(conservar, "Control"), 1);
		notificacion("2", borrar);
		notificacion("2", conservar);

		mvc.perform(conToken(delete("/experiments/" + borrar), login("inv@fst.mx"))).andExpect(status().isNoContent());

		// Del que se borró desaparece todo lo que colgaba de él (la base no tiene cascada, lo borra el servicio)...
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE idexperimento = ?", Integer.class, borrar));
		// ...y del otro no se toca nada: es la prueba de que el borrado filtra por experimento y no se pasa de largo.
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE idexperimento = ?", Integer.class, conservar));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tanda", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE idexperimento = ?", Integer.class, borrar));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE idexperimento = ?", Integer.class, conservar));
	}

	// ---------------------------------------------------------------- 3. subir video

	@Test
	void otro_investigador_recibe_403_con_el_mensaje_de_creador() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		cuenta("3", "otro@fst.mx", false, true);
		int experimento = experimento("2", "Exp de subida");

		// Aquí ni un administrador podría: subir videos es solo de quien creó el experimento.
		subir(login("otro@fst.mx"), experimento, "Dia 2", "video.mp4", null)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Solo quien creó el experimento sube sus videos."));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM video", Integer.class));
	}

	@Test
	void un_archivo_que_no_es_mp4_ni_mov_se_rechaza_con_422() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp formato");

		// 422 Unprocessable Entity: la petición está bien formada pero su contenido no se puede aceptar.
		subir(login("inv@fst.mx"), experimento, "Dia 2", "video.avi", null)
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("Solo se aceptan videos .mp4 o .mov."));
		// Se rechaza antes de sondear el archivo y antes de crear la tanda.
		// never(): comprueba que ffprobe NI SIQUIERA se llamó (no se gasta tiempo midiendo un formato no válido).
		verify(ffprobe, never()).sondear(any());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tanda", Integer.class));
	}

	@Test
	void un_video_vertical_responde_422_y_no_deja_archivos_ni_filas() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp vertical");
		// Se «programa» el doble: ffprobe dirá que el video mide 1080 de ancho por 1920 de alto (vertical), 300 s.
		when(ffprobe.sondear(any())).thenReturn(new Ffprobe.Sonda(1080, 1920, 300.0));

		subir(login("inv@fst.mx"), experimento, "Dia 2", "vertical.mp4", null)
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value(
						"El video es vertical: se necesita un video horizontal, con los cilindros de lado a lado."));
		// El rechazo no deja basura: ni video, ni tanda, ni grupo en la base, ni archivo suelto en disco.
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM video", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tanda", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM grupo", Integer.class));
		assertEquals(0, archivosEn("videos"));
	}

	@Test
	void un_video_horizontal_del_dia_2_crea_la_tanda_el_video_y_el_analisis_en_cola() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp horizontal");
		// 1920x1080 = horizontal; 300 s = los cinco minutos de la prueba.
		when(ffprobe.sondear(any())).thenReturn(new Ffprobe.Sonda(1920, 1080, 300.0));

		subir(login("inv@fst.mx"), experimento, "Dia 2", "horizontal.mp4", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.estadoAnalisis").value("en cola"));

		// Una sola subida crea toda la cadena: la tanda (la primera es la A)...
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tanda", Integer.class));
		// CHR(64 + 1) = 'A': el ordinal se guarda como número y se muestra como letra.
		assertEquals("A", jdbc.queryForObject("SELECT CHR(64 + ordinal) FROM tanda", String.class));
		// ...el video, con el archivo guardado como videos/<id>.mp4 (nombre propio, no el que mandó el usuario)...
		int idVideo = jdbc.queryForObject("SELECT idvideo FROM video", Integer.class);
		assertEquals("videos/" + idVideo + ".mp4",
				jdbc.queryForObject("SELECT archivo FROM video WHERE idvideo = ?", String.class, idVideo));
		// ...el archivo realmente escrito en disco...
		assertTrue(Files.exists(raizDePruebas().resolve("videos").resolve(idVideo + ".mp4")));
		// ...y el análisis «en cola», que es lo que el worker recogerá.
		assertEquals("en cola", jdbc.queryForObject("SELECT estado FROM analisis WHERE idvideo = ?", String.class, idVideo));
	}

	@Test
	void un_video_horizontal_del_dia_1_se_guarda_sin_analisis() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp dia 1");
		// El Día 1 es la habituación: dura más (1200 s) y no se analiza.
		when(ffprobe.sondear(any())).thenReturn(new Ffprobe.Sonda(1920, 1080, 1200.0));

		subir(login("inv@fst.mx"), experimento, "Dia 1", "habituacion.mp4", null)
				.andExpect(status().isCreated())
				// En la respuesta ni siquiera aparece el campo del análisis.
				.andExpect(jsonPath("$.data.estadoAnalisis").doesNotExist());

		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM video WHERE sesion = 'Dia 1'", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM analisis", Integer.class));
	}

	@Test
	void un_segundo_video_del_mismo_dia_en_la_misma_tanda_responde_409() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp dos videos");
		String token = login("inv@fst.mx");
		when(ffprobe.sondear(any())).thenReturn(new Ffprobe.Sonda(1920, 1080, 300.0));

		// El primero crea la Tanda A. El segundo pide expresamente el ordinal "1" (la misma A) y ya tiene Día 2.
		subir(token, experimento, "Dia 2", "primero.mp4", null).andExpect(status().isCreated());
		subir(token, experimento, "Dia 2", "segundo.mp4", "1")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("La Tanda A ya tiene su video de Día 2. Cada tanda lleva un solo video por sesión."));

		// Solo queda el primero, tanto en la base como en disco (el archivo del rechazado no se dejó).
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM video", Integer.class));
		assertEquals(1, archivosEn("videos"));
	}

	// ---------------------------------------------------------------- ayudas
	//
	// Métodos de apoyo: arman datos por SQL directo y peticiones HTTP, para que cada prueba de arriba cuente solo
	// lo que quiere probar.

	// Arma la petición multipart de subida de video. "ordinal" es opcional: null deja que el sistema cree/elija la tanda.
	private ResultActions subir(String token, int experimento, String sesion, String nombreArchivo, String ordinal)
			throws Exception {
		// El archivo viaja en la parte "archivo", con tres bytes de relleno: el contenido no importa porque ffprobe es falso.
		MockMultipartHttpServletRequestBuilder peticion = multipart("/experiments/{id}/groups/{grupo}/videos", experimento, "Control")
				.file(new MockMultipartFile("archivo", nombreArchivo, "video/mp4", new byte[] {0, 1, 2}));
		peticion.param("sesion", sesion);
		peticion.param("tipo", "control");
		peticion.param("tratamiento", "agua");
		peticion.param("nCilindros", "2");
		if (ordinal != null) {
			peticion.param("ordinal", ordinal);
		}
		return mvc.perform(peticion.header("Authorization", "Bearer " + token));
	}

	// Crea una cuenta; si es administrador, también su fila en la tabla administrador. Contraseña = CLAVE (cifrada).
	private void cuenta(String id, String correo, boolean administrador, boolean activo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, ?, false)", id, correo, passwordEncoder.encode(CLAVE), activo);
		if (administrador) {
			jdbc.update("INSERT INTO administrador (idinstitucional) VALUES (?)", id);
		}
	}

	// Inserta un experimento a nombre de "creador" y devuelve su id.
	private int experimento(String creador, String nombre) {
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES (?, ?, current_date)", creador, nombre);
		return jdbc.queryForObject("SELECT idexperimento FROM experimento WHERE nombre = ?", Integer.class, nombre);
	}

	private int grupo(int experimento, String etiqueta) {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, 'control', 'agua')",
				experimento, etiqueta);
		return jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE idexperimento = ? AND etiqueta = ?", Integer.class,
				experimento, etiqueta);
	}

	private int tanda(int grupo, int ordinal) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, 2)", grupo, ordinal);
		return jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, grupo, ordinal);
	}

	// Un video del Día 2 con su análisis en el estado dado (la subida normal crea el análisis en cola).
	private void analisis(int tanda, String estado) {
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', NULL, 300)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		// ?::estado_analisis convierte el texto al tipo de dominio de la base.
		jdbc.update("INSERT INTO analisis (idvideo, estado, fechaanalisis) VALUES (?, ?::estado_analisis, now())", video, estado);
	}

	private void notificacion(String idInstitucional, int experimento) {
		jdbc.update("INSERT INTO notificacion (idinstitucional, idexperimento, tipo, mensaje) "
				+ "VALUES (?, ?, 'analisis completado', 'prueba')", idInstitucional, experimento);
	}

	// Inicia sesión por el endpoint real y devuelve el JWT de la respuesta (JsonPath lo extrae de "data.token").
	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

	// Agrega la cabecera "Authorization: Bearer <token>" a cualquier petición.
	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header("Authorization", "Bearer " + token);
	}

	// Carpeta temporal donde el sistema guarda los archivos durante estas pruebas.
	private static Path raizDePruebas() {
		return Path.of(System.getProperty("java.io.tmpdir")).resolve(NOMBRE_RAIZ);
	}

	// Cuántos archivos hay en una subcarpeta de la raíz (0 si la carpeta no existe).
	private static int archivosEn(String subcarpeta) throws IOException {
		Path carpeta = raizDePruebas().resolve(subcarpeta);
		if (!Files.exists(carpeta)) {
			return 0;
		}
		// try-with-resources: Files.list abre un recurso del sistema que hay que cerrar.
		try (Stream<Path> archivos = Files.list(carpeta)) {
			return (int) archivos.count();
		}
	}

	// Deja la raíz de pruebas vacía, con sus subcarpetas, para que cada prueba cuente solo lo suyo.
	private static void vaciarRaiz() throws IOException {
		Path raiz = raizDePruebas();
		if (Files.exists(raiz)) {
			try (Stream<Path> todo = Files.walk(raiz)) {
				// Orden inverso: primero los archivos y subcarpetas más profundos, y al final las carpetas padre,
				// porque no se puede borrar una carpeta con contenido. La raíz misma se conserva.
				for (Path p : todo.sorted(Comparator.reverseOrder()).toList()) {
					if (!p.equals(raiz)) {
						Files.delete(p);
					}
				}
			}
		}
		// Recrea las subcarpetas que el sistema espera encontrar.
		Files.createDirectories(raiz.resolve("videos"));
		Files.createDirectories(raiz.resolve("analisis"));
		Files.createDirectories(raiz.resolve("reportes"));
	}
}
