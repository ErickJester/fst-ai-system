package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipn.mx.fst.dto.ModificarCuentaPeticion;
import com.ipn.mx.fst.utilerias.ErrorApi;
import com.ipn.mx.fst.utilerias.service.EmailService;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Encargo 35: Administración (R-07, R-16) y recuperar contraseña, contra una PostgreSQL de verdad (las reglas dependen de los
// candados y de las llaves foráneas, que un simulacro no reproduce). Corre solo si FST_TEST_DB_URL apunta a una base VACÍA con el
// esquema de db/init/001_esquema.sql y NO a la de desarrollo: cada prueba borra todos los usuarios.
//   docker run -d --name fst-test-db -e POSTGRES_USER=fst -e POSTGRES_PASSWORD=fst -e POSTGRES_DB=fst_test -p 5544:5432 \
//     -v "<ruta>/backend-spring/db/init:/docker-entrypoint-initdb.d:ro" postgres:16-alpine
//   FST_TEST_DB_URL=jdbc:postgresql://localhost:5544/fst_test ./mvnw test
// Sin la variable se omite. El correo es un simulacro (EmailService): no sale nada.
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-admin",
		"app.reset.minutos=60"
})
@AutoConfigureMockMvc
class AdministracionYRecuperacionTest {

	private static final String CLAVE = "clave-de-prueba-1";
	private static final Pattern TOKEN_DEL_CORREO = Pattern.compile("/restablecer\\?token=([\\w.\\-]+)");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private AdminService adminService;

	@MockitoBean
	private EmailService emailService;

	@BeforeEach
	void base_limpia() {
		reset(emailService);
		jdbc.update("DELETE FROM reporte");
		jdbc.update("DELETE FROM analisis");
		jdbc.update("DELETE FROM video");
		jdbc.update("DELETE FROM tanda");
		jdbc.update("DELETE FROM grupo");
		jdbc.update("DELETE FROM notificacion");
		jdbc.update("DELETE FROM experimento");
		jdbc.update("DELETE FROM administrador");
		jdbc.update("DELETE FROM usuario");
	}

	// ---------------------------------------------------------------- R-07: solo Administradores

	@Test
	void un_investigador_recibe_403_en_todo_admin() throws Exception {
		cuenta("100", "inv@fst.mx", false, true);
		String token = login("inv@fst.mx", CLAVE);

		mvc.perform(conToken(get("/admin/users"), token)).andExpect(status().isForbidden());
		mvc.perform(conToken(get("/admin/system"), token)).andExpect(status().isForbidden());
		mvc.perform(conToken(post("/admin/users"), token).contentType(MediaType.APPLICATION_JSON)
				.content(cuentaNueva("200", "otra@fst.mx"))).andExpect(status().isForbidden());
		mvc.perform(conToken(patch("/admin/users/100"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"activo\":false}")).andExpect(status().isForbidden());
		mvc.perform(conToken(post("/admin/users/100/reset-temporal"), token)).andExpect(status().isForbidden());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE idinstitucional = '200'", Integer.class));
	}

	@Test
	void sin_sesion_admin_responde_401_y_el_rol_se_lee_de_la_base_en_cada_peticion() throws Exception {
		mvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());

		cuenta("1", "admin@fst.mx", true, true);
		String token = login("admin@fst.mx", CLAVE);
		mvc.perform(conToken(get("/admin/users"), token)).andExpect(status().isOk());

		// Le quitan el rol con el token todavía vigente: la siguiente petición ya es 403.
		jdbc.update("DELETE FROM administrador WHERE idinstitucional = '1'");
		mvc.perform(conToken(get("/admin/users"), token)).andExpect(status().isForbidden());
	}

	// ---------------------------------------------------------------- crear cuenta y contraseña temporal

	@Test
	void crear_una_cuenta_da_una_temporal_una_sola_vez_la_manda_por_correo_y_obliga_a_cambiarla() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		String admin = login("admin@fst.mx", CLAVE);

		MvcResult r = mvc.perform(conToken(post("/admin/users"), admin).contentType(MediaType.APPLICATION_JSON)
				.content(cuentaNueva("2021630154", "Nueva@Fst.mx")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.rol").value("Investigador"))
				.andExpect(jsonPath("$.data.correo").value("nueva@fst.mx"))
				.andExpect(jsonPath("$.data.cambioRequerido").value(true))
				.andReturn();
		String temporal = JsonPath.read(r.getResponse().getContentAsString(), "$.data.passwordTemporal");
		assertEquals(12, temporal.length());

		// En la base solo queda el hash, y la cuenta debe cambiarla.
		assertTrue(passwordEncoder.matches(temporal, jdbc.queryForObject(
				"SELECT contrasenahash FROM usuario WHERE idinstitucional = '2021630154'", String.class)));
		assertEquals(Boolean.TRUE, jdbc.queryForObject(
				"SELECT cambiorequerido FROM usuario WHERE idinstitucional = '2021630154'", Boolean.class));

		// Va por correo, con la contraseña dentro, al correo de la cuenta.
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(emailService).enviarCorreo(eq("nueva@fst.mx"), anyString(), texto.capture());
		assertTrue(texto.getValue().contains(temporal));

		// No se vuelve a mostrar: ni la lista ni nada más la trae.
		String lista = mvc.perform(conToken(get("/admin/users"), admin)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertFalse(lista.contains(temporal));
		assertFalse(lista.toLowerCase().contains("contrasena"));

		// Con la temporal entra, pero la sesión solo sirve para cambiar la contraseña.
		MvcResult entrada = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"nueva@fst.mx\",\"contrasena\":\"" + temporal + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.cambioRequerido").value(true)).andReturn();
		String tokenNueva = JsonPath.read(entrada.getResponse().getContentAsString(), "$.data.token");
		mvc.perform(conToken(get("/experiments"), tokenNueva)).andExpect(status().isUnauthorized());
		mvc.perform(conToken(patch("/auth/password"), tokenNueva).contentType(MediaType.APPLICATION_JSON)
				.content("{\"contrasenaActual\":\"" + temporal + "\",\"contrasenaNueva\":\"otra-clave-123\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.cambioRequerido").value(false));
	}

	@Test
	void un_correo_que_falla_no_deshace_la_cuenta() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		String admin = login("admin@fst.mx", CLAVE);
		org.mockito.Mockito.doThrow(new RuntimeException("SMTP caído")).when(emailService)
				.enviarCorreo(anyString(), anyString(), anyString());

		// El envío ocurre después del commit: aunque falle, la cuenta existe y la respuesta lleva la contraseña.
		mvc.perform(conToken(post("/admin/users"), admin).contentType(MediaType.APPLICATION_JSON)
				.content(cuentaNueva("300", "c@fst.mx")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.passwordTemporal").isString());
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE idinstitucional = '300'", Integer.class));
	}

	@Test
	void identificador_o_correo_repetidos_responden_409() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "dos@fst.mx", false, true);
		String admin = login("admin@fst.mx", CLAVE);

		mvc.perform(conToken(post("/admin/users"), admin).contentType(MediaType.APPLICATION_JSON)
				.content(cuentaNueva("2", "distinto@fst.mx")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Ya existe una cuenta con ese identificador."));
		mvc.perform(conToken(post("/admin/users"), admin).contentType(MediaType.APPLICATION_JSON)
				.content(cuentaNueva("3", "DOS@fst.mx")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Ya existe una cuenta con ese correo."));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM usuario", Integer.class));
		verify(emailService, never()).enviarCorreo(anyString(), anyString(), anyString());
	}

	@Test
	void la_misma_cuenta_pedida_dos_veces_a_la_vez_se_crea_una_sola_vez() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		String admin = login("admin@fst.mx", CLAVE);
		Callable<Integer> crear = () -> mvc.perform(conToken(post("/admin/users"), admin)
				.contentType(MediaType.APPLICATION_JSON).content(cuentaNueva("500", "uno@fst.mx")))
				.andReturn().getResponse().getStatus();

		List<Integer> codigos = enParalelo(crear, crear);
		assertTrue(codigos.contains(201), codigos.toString());
		assertTrue(codigos.contains(409), codigos.toString());
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE idinstitucional = '500'", Integer.class));
	}

	@Test
	void la_lista_filtra_por_rol_y_por_estado() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "uno@fst.mx", false, true);
		cuenta("3", "dos@fst.mx", false, false);
		String admin = login("admin@fst.mx", CLAVE);

		assertEquals(3, cuentasDe(admin, "/admin/users").size());
		assertEquals(List.of("1"), cuentasDe(admin, "/admin/users?rol=Administrador"));
		assertEquals(2, cuentasDe(admin, "/admin/users?rol=Investigador").size());
		assertEquals(List.of("3"), cuentasDe(admin, "/admin/users?estado=inactiva"));
		assertEquals(List.of("2"), cuentasDe(admin, "/admin/users?rol=Investigador&estado=activa"));
		mvc.perform(conToken(get("/admin/users?rol=Dios"), admin)).andExpect(status().isBadRequest());
	}

	@Test
	void una_temporal_nueva_cumple_las_mismas_reglas_y_deja_sin_efecto_la_anterior_y_los_enlaces() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "inv@fst.mx", false, true);
		String admin = login("admin@fst.mx", CLAVE);

		// Un enlace de restablecer pedido antes también deja de servir.
		mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"inv@fst.mx\"}"))
				.andExpect(status().isOk());
		String enlaceViejo = tokenDelCorreo("inv@fst.mx");
		reset(emailService);

		MvcResult r = mvc.perform(conToken(post("/admin/users/2/reset-temporal"), admin)).andExpect(status().isOk()).andReturn();
		String temporal = JsonPath.read(r.getResponse().getContentAsString(), "$.data.passwordTemporal");
		assertEquals(12, temporal.length());
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT cambiorequerido FROM usuario WHERE idinstitucional = '2'", Boolean.class));
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(emailService).enviarCorreo(eq("inv@fst.mx"), anyString(), texto.capture());
		assertTrue(texto.getValue().contains(temporal));

		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"inv@fst.mx\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isUnauthorized());
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"inv@fst.mx\",\"contrasena\":\"" + temporal + "\"}")).andExpect(status().isOk());
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlaceViejo + "\",\"contrasenaNueva\":\"otra-clave-123\"}")).andExpect(status().isBadRequest());

		mvc.perform(conToken(post("/admin/users/999/reset-temporal"), admin)).andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------- PATCH: datos, rol, desactivar, nunca DELETE

	@Test
	void patch_cambia_datos_y_rol_desactiva_y_no_hay_delete() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "inv@fst.mx", false, true);
		String admin = login("admin@fst.mx", CLAVE);

		mvc.perform(conToken(patch("/admin/users/2"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana\",\"apellidos\":\"Pérez\",\"correo\":\"NUEVO@fst.mx\",\"rol\":\"Administrador\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nombre").value("Ana"))
				.andExpect(jsonPath("$.data.correo").value("nuevo@fst.mx"))
				.andExpect(jsonPath("$.data.rol").value("Administrador"));
		assertEquals(Boolean.TRUE, jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM administrador WHERE idinstitucional = '2')", Boolean.class));

		mvc.perform(conToken(patch("/admin/users/2"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"activo\":false,\"rol\":\"Investigador\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.activo").value(false))
				.andExpect(jsonPath("$.data.rol").value("Investigador"));
		// Desactivada: ni entra ni su correo recibe enlaces.
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"nuevo@fst.mx\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isUnauthorized());

		mvc.perform(conToken(delete("/admin/users/2"), admin)).andExpect(status().is4xxClientError());
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM usuario", Integer.class));

		mvc.perform(conToken(patch("/admin/users/999"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"activo\":false}")).andExpect(status().isNotFound());
	}

	@Test
	void patch_con_un_correo_de_otra_cuenta_responde_409_y_con_otro_identificador_responde_400() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "inv@fst.mx", false, true);
		String admin = login("admin@fst.mx", CLAVE);

		mvc.perform(conToken(patch("/admin/users/2"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"ADMIN@fst.mx\"}")).andExpect(status().isConflict());
		mvc.perform(conToken(patch("/admin/users/2"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idInstitucional\":\"22\"}")).andExpect(status().isBadRequest());
		// El mismo identificador de la URL se acepta (el frontend lo mandaba).
		mvc.perform(conToken(patch("/admin/users/2"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idInstitucional\":\"2\",\"nombre\":\"Igual\"}")).andExpect(status().isOk());
	}

	@Test
	void patch_con_nombre_apellidos_o_correo_vacios_o_solo_espacios_responde_400() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "inv@fst.mx", false, true);
		String admin = login("admin@fst.mx", CLAVE);

		for (String cuerpo : List.of("{\"correo\":\"\"}", "{\"correo\":\"   \"}", "{\"nombre\":\" \"}", "{\"apellidos\":\"\"}")) {
			mvc.perform(conToken(patch("/admin/users/2"), admin).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
					.andExpect(status().isBadRequest());
		}
		assertEquals("inv@fst.mx", jdbc.queryForObject("SELECT correo FROM usuario WHERE idinstitucional = '2'", String.class));
	}

	@Test
	void r16_no_se_desactiva_ni_se_le_quita_el_rol_al_ultimo_administrador() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "inv@fst.mx", false, true);
		String admin = login("admin@fst.mx", CLAVE);

		mvc.perform(conToken(patch("/admin/users/1"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"activo\":false}")).andExpect(status().isConflict());
		mvc.perform(conToken(patch("/admin/users/1"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"rol\":\"Investigador\"}")).andExpect(status().isConflict());
		mvc.perform(conToken(patch("/admin/users/1"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"activo\":false,\"rol\":\"Investigador\"}")).andExpect(status().isConflict());
		assertEquals(1, administradoresActivos());

		// Un Administrador desactivado no cuenta como «otro»: con 2 es admin inactivo, el 1 sigue siendo el último.
		cuenta("3", "tres@fst.mx", true, false);
		mvc.perform(conToken(patch("/admin/users/1"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"activo\":false}")).andExpect(status().isConflict());

		// Con otro Administrador activo sí se puede, y entonces el que queda es el último.
		jdbc.update("UPDATE usuario SET activo = true WHERE idinstitucional = '3'");
		mvc.perform(conToken(patch("/admin/users/1"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"rol\":\"Investigador\"}")).andExpect(status().isOk());
		String otro = login("tres@fst.mx", CLAVE);
		mvc.perform(conToken(patch("/admin/users/3"), otro).contentType(MediaType.APPLICATION_JSON)
				.content("{\"activo\":false}")).andExpect(status().isConflict());
	}

	@Test
	void r16_dos_administradores_que_se_desactivan_a_la_vez_no_dejan_el_sistema_sin_ninguno() throws Exception {
		for (int vuelta = 0; vuelta < 25; vuelta++) {
			base_limpia();
			cuenta("1", "a@fst.mx", true, true);
			cuenta("2", "b@fst.mx", true, true);
			ModificarCuentaPeticion baja = new ModificarCuentaPeticion();
			baja.setActivo(false);

			List<Integer> codigos = enParalelo(
					() -> codigoDe(() -> adminService.modificar("1", baja)),
					() -> codigoDe(() -> adminService.modificar("2", baja)));

			assertEquals(1, administradoresActivos(), "vuelta " + vuelta + ": " + codigos);
			assertTrue(codigos.contains(200) && codigos.contains(409), "vuelta " + vuelta + ": " + codigos);
		}
	}

	@Test
	void r16_quitarse_el_rol_el_uno_al_otro_a_la_vez_tampoco_deja_el_sistema_sin_ninguno() throws Exception {
		for (int vuelta = 0; vuelta < 25; vuelta++) {
			base_limpia();
			cuenta("1", "a@fst.mx", true, true);
			cuenta("2", "b@fst.mx", true, true);
			ModificarCuentaPeticion quitar = new ModificarCuentaPeticion();
			quitar.setRol("Investigador");

			List<Integer> codigos = enParalelo(
					() -> codigoDe(() -> adminService.modificar("1", quitar)),
					() -> codigoDe(() -> adminService.modificar("2", quitar)));

			assertEquals(1, administradoresActivos(), "vuelta " + vuelta + ": " + codigos);
			assertTrue(codigos.contains(200) && codigos.contains(409), "vuelta " + vuelta + ": " + codigos);
		}
	}

	// ---------------------------------------------------------------- candado de la cuenta: FOR NO KEY UPDATE

	@Test
	void el_candado_de_la_cuenta_deja_pasar_las_notificaciones_del_worker_y_for_update_no() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		int experimento = experimento("1", "Exp");

		try (Connection edicion = dataSource.getConnection(); Connection worker = dataSource.getConnection()) {
			edicion.setAutoCommit(false);
			worker.setAutoCommit(false);
			// El worker ya tiene el experimento (como en cola.py) cuando se bloquea la cuenta.
			ejecutar(worker, "SELECT 1 FROM experimento WHERE idexperimento = " + experimento + " FOR UPDATE");
			ejecutar(edicion, "SELECT 1 FROM usuario WHERE idinstitucional = '1' FOR NO KEY UPDATE");

			// Su notificación (llave foránea a la cuenta) entra sin esperar.
			ejecutar(worker, "SET LOCAL lock_timeout = '1500ms'");
			ejecutar(worker, "INSERT INTO notificacion (idinstitucional, idexperimento, tipo, mensaje) "
					+ "VALUES ('1', " + experimento + ", 'analisis completado', 'listo')");
			worker.commit();
			edicion.commit();
		}

		// Con FOR UPDATE el mismo cruce se bloquea: es lo que causaría el interbloqueo y la razón del candado más débil.
		try (Connection edicion = dataSource.getConnection(); Connection worker = dataSource.getConnection()) {
			edicion.setAutoCommit(false);
			worker.setAutoCommit(false);
			ejecutar(edicion, "SELECT 1 FROM usuario WHERE idinstitucional = '1' FOR UPDATE");
			ejecutar(worker, "SET LOCAL lock_timeout = '500ms'");
			SQLException bloqueo = null;
			try {
				ejecutar(worker, "INSERT INTO notificacion (idinstitucional, idexperimento, tipo, mensaje) "
						+ "VALUES ('1', " + experimento + ", 'analisis completado', 'otra')");
			} catch (SQLException e) {
				bloqueo = e;
			}
			assertTrue(bloqueo != null && "55P03".equals(bloqueo.getSQLState()), "esperaba lock_timeout con FOR UPDATE");
			edicion.rollback();
			worker.rollback();
		}
	}

	@Test
	void editar_el_nombre_mientras_el_worker_crea_una_notificacion_no_se_traba_ni_pierde_nada() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp");
		ModificarCuentaPeticion cambio = new ModificarCuentaPeticion();

		for (int vuelta = 0; vuelta < 20; vuelta++) {
			cambio.setNombre("Nombre " + vuelta);
			cambio.setCorreo("inv" + vuelta + "@fst.mx");
			List<Integer> codigos = enParalelo(
					() -> codigoDe(() -> adminService.modificar("2", cambio)),
					() -> {
						// Lo que hace el worker: el experimento primero y la notificación después.
						try (Connection c = dataSource.getConnection()) {
							c.setAutoCommit(false);
							ejecutar(c, "SELECT 1 FROM experimento WHERE idexperimento = " + experimento + " FOR UPDATE");
							ejecutar(c, "INSERT INTO notificacion (idinstitucional, idexperimento, tipo, mensaje) "
									+ "VALUES ('2', " + experimento + ", 'analisis completado', 'listo')");
							c.commit();
							return 200;
						}
					});
			assertEquals(List.of(200, 200), codigos, "vuelta " + vuelta);
		}
		assertEquals(20, jdbc.queryForObject("SELECT COUNT(*) FROM notificacion", Integer.class));
	}

	@Test
	void cambiar_el_nombre_desde_administracion_invalida_los_reportes_y_cambiar_solo_el_rol_no() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		cuenta("2", "inv@fst.mx", false, true);
		int experimento = experimento("2", "Exp");
		jdbc.update("INSERT INTO reporte (idexperimento, formato, ruta) VALUES (?, 'PDF', 'reportes/x/experimento.pdf')", experimento);
		String admin = login("admin@fst.mx", CLAVE);

		mvc.perform(conToken(patch("/admin/users/2"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"otro@fst.mx\",\"rol\":\"Administrador\"}")).andExpect(status().isOk());
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reporte", Integer.class));

		mvc.perform(conToken(patch("/admin/users/2"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"apellidos\":\"Distinto\"}")).andExpect(status().isOk());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM reporte", Integer.class));
	}

	// ---------------------------------------------------------------- GET /admin/system

	@Test
	void system_trae_disco_conductas_y_la_cola_en_orden_de_llegada() throws Exception {
		cuenta("1", "admin@fst.mx", true, true);
		int experimento = experimento("1", "Exp de cola");
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, 'Control', 'control', 'agua')", experimento);
		int grupo = jdbc.queryForObject("SELECT idgrupo FROM grupo", Integer.class);
		int tanda1 = tanda(grupo, 1);
		int tanda2 = tanda(grupo, 2);
		int tanda3 = tanda(grupo, 3);
		analisis(tanda1, "en cola", "2026-10-25 10:00:00+00");
		analisis(tanda2, "procesando", "2026-10-25 11:00:00+00");
		analisis(tanda3, "completado", "2026-10-25 09:00:00+00");
		String admin = login("admin@fst.mx", CLAVE);

		mvc.perform(conToken(get("/admin/system"), admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.disco.total_gb").isNumber())
				.andExpect(jsonPath("$.data.disco.usado_gb").isNumber())
				.andExpect(jsonPath("$.data.disco.videos_gb").isNumber())
				.andExpect(jsonPath("$.data.disco.resultados_gb").isNumber())
				.andExpect(jsonPath("$.data.disco.por_liberar_7d_gb").isNumber())
				.andExpect(jsonPath("$.data.conductas[0]").value("nado activo"))
				.andExpect(jsonPath("$.data.conductas[3]").value("conducta activa"))
				.andExpect(jsonPath("$.data.cola.length()").value(2))
				.andExpect(jsonPath("$.data.cola[0].estado").value("procesando"))
				.andExpect(jsonPath("$.data.cola[0].letra").value("B"))
				.andExpect(jsonPath("$.data.cola[1].estado").value("en cola"))
				.andExpect(jsonPath("$.data.cola[1].experimento").value("Exp de cola"))
				.andExpect(jsonPath("$.data.cola[1].grupo").value("Control"))
				.andExpect(jsonPath("$.data.cola[1].sesion").value("Dia 2"));
	}

	// ---------------------------------------------------------------- recuperar y restablecer

	@Test
	void recover_responde_lo_mismo_exista_o_no_el_correo_y_solo_manda_correo_si_existe() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		cuenta("3", "baja@fst.mx", false, false);

		String existe = mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"INV@fst.mx\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String noExiste = mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"nadie@fst.mx\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String inactiva = mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"baja@fst.mx\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertEquals(existe, noExiste);
		assertEquals(existe, inactiva);

		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(emailService, timeout(5000)).enviarCorreo(eq("inv@fst.mx"), anyString(), texto.capture());
		assertTrue(TOKEN_DEL_CORREO.matcher(texto.getValue()).find());
		assertTrue(texto.getValue().contains("60 minutos"));
		Thread.sleep(300);
		verify(emailService, never()).enviarCorreo(eq("nadie@fst.mx"), anyString(), anyString());
		verify(emailService, never()).enviarCorreo(eq("baja@fst.mx"), anyString(), anyString());
	}

	@Test
	void el_enlace_cambia_la_contrasena_una_sola_vez() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		jdbc.update("UPDATE usuario SET cambiorequerido = true WHERE idinstitucional = '2'");
		mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"inv@fst.mx\"}"))
				.andExpect(status().isOk());
		String enlace = tokenDelCorreo("inv@fst.mx");

		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"la-nueva-clave-1\"}")).andExpect(status().isOk());
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"inv@fst.mx\",\"contrasena\":\"la-nueva-clave-1\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.cambioRequerido").value(false));
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"inv@fst.mx\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isUnauthorized());

		// El mismo enlace por segunda vez: ya no sirve.
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"otra-distinta-123\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("El enlace venció o ya se usó."));
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"inv@fst.mx\",\"contrasena\":\"la-nueva-clave-1\"}")).andExpect(status().isOk());
	}

	@Test
	void el_mismo_enlace_usado_dos_veces_a_la_vez_solo_funciona_una() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"inv@fst.mx\"}"))
				.andExpect(status().isOk());
		String enlace = tokenDelCorreo("inv@fst.mx");

		Callable<Integer> a = () -> mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"clave-de-a-123\"}")).andReturn().getResponse().getStatus();
		Callable<Integer> b = () -> mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"clave-de-b-123\"}")).andReturn().getResponse().getStatus();
		List<Integer> codigos = enParalelo(a, b);
		assertTrue(codigos.contains(200) && codigos.contains(400), codigos.toString());
	}

	@Test
	void el_enlace_deja_de_servir_si_la_contrasena_cambia_por_otro_camino_o_si_vencio_o_no_es_de_restablecer() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"inv@fst.mx\"}"))
				.andExpect(status().isOk());
		String enlace = tokenDelCorreo("inv@fst.mx");

		// Cambio normal de contraseña con la sesión: el enlace pendiente ya no sirve.
		String sesion = login("inv@fst.mx", CLAVE);
		mvc.perform(conToken(patch("/auth/password"), sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"contrasenaActual\":\"" + CLAVE + "\",\"contrasenaNueva\":\"cambiada-por-mi-1\"}")).andExpect(status().isOk());
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"desde-el-enlace-1\"}")).andExpect(status().isBadRequest());

		// Vencido (firmado con la misma llave pero con la vigencia pasada).
		String vencido = firmar("reset", "2", Instant.now().minus(Duration.ofMinutes(1)), huellaActual("2"));
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + vencido + "\",\"contrasenaNueva\":\"desde-el-enlace-1\"}")).andExpect(status().isBadRequest());

		// Un token de sesión no sirve como enlace, ni uno inventado.
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + sesion + "\",\"contrasenaNueva\":\"desde-el-enlace-1\"}")).andExpect(status().isBadRequest());
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"basura\",\"contrasenaNueva\":\"desde-el-enlace-1\"}")).andExpect(status().isBadRequest());

		// Y el enlace no sirve como sesión.
		String comoSesion = firmar("reset", "2", Instant.now().plus(Duration.ofMinutes(10)), huellaActual("2"));
		mvc.perform(conToken(get("/experiments"), comoSesion)).andExpect(status().isUnauthorized());
	}

	@Test
	void una_contrasena_que_no_cumple_las_reglas_da_422_y_no_gasta_el_enlace() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"inv@fst.mx\"}"))
				.andExpect(status().isOk());
		String enlace = tokenDelCorreo("inv@fst.mx");

		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"corta\"}")).andExpect(status().isUnprocessableEntity());
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"" + CLAVE + "\"}")).andExpect(status().isUnprocessableEntity());
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"ahora-si-vale-1\"}")).andExpect(status().isOk());
	}

	@Test
	void una_cuenta_desactivada_despues_de_pedir_el_enlace_ya_no_puede_usarlo() throws Exception {
		cuenta("2", "inv@fst.mx", false, true);
		mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"inv@fst.mx\"}"))
				.andExpect(status().isOk());
		String enlace = tokenDelCorreo("inv@fst.mx");
		jdbc.update("UPDATE usuario SET activo = false WHERE idinstitucional = '2'");

		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"desde-el-enlace-1\"}")).andExpect(status().isBadRequest());
	}

	// ---------------------------------------------------------------- ayudas

	private void cuenta(String id, String correo, boolean administrador, boolean activo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, ?, false)", id, correo, passwordEncoder.encode(CLAVE), activo);
		if (administrador) {
			jdbc.update("INSERT INTO administrador (idinstitucional) VALUES (?)", id);
		}
	}

	private int experimento(String creador, String nombre) {
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES (?, ?, current_date)", creador, nombre);
		return jdbc.queryForObject("SELECT idexperimento FROM experimento WHERE nombre = ?", Integer.class, nombre);
	}

	private int tanda(int grupo, int ordinal) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, 2)", grupo, ordinal);
		return jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, grupo, ordinal);
	}

	private void analisis(int tanda, String estado, String fecha) {
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', NULL, 300)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		jdbc.update("INSERT INTO analisis (idvideo, estado, fechaanalisis) VALUES (?, ?::estado_analisis, ?::timestamptz)",
				video, estado, fecha);
	}

	private int administradoresActivos() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM administrador a JOIN usuario u ON u.idinstitucional = a.idinstitucional "
				+ "WHERE u.activo", Integer.class);
	}

	private String login(String correo, String contrasena) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

	private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
		return peticion.header("Authorization", "Bearer " + token);
	}

	private static String cuentaNueva(String id, String correo) {
		return "{\"idInstitucional\":\"" + id + "\",\"nombre\":\"Ana\",\"apellidos\":\"López\",\"correo\":\"" + correo + "\"}";
	}

	private List<String> cuentasDe(String token, String ruta) throws Exception {
		String cuerpo = mvc.perform(conToken(get(ruta), token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(cuerpo, "$.data[*].idInstitucional");
	}

	// El token del último correo mandado a ese destinatario (el recover lo manda en otro hilo, por eso el timeout).
	private String tokenDelCorreo(String destinatario) {
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(emailService, timeout(5000)).enviarCorreo(eq(destinatario), anyString(), texto.capture());
		Matcher m = TOKEN_DEL_CORREO.matcher(texto.getValue());
		assertTrue(m.find(), texto.getValue());
		return m.group(1);
	}

	private String huellaActual(String id) throws Exception {
		// La huella se obtiene de un enlace verdadero: se lee del token que mandó recover.
		reset(emailService);
		mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + jdbc.queryForObject("SELECT correo FROM usuario WHERE idinstitucional = ?", String.class, id) + "\"}"));
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(emailService, timeout(5000)).enviarCorreo(anyString(), anyString(), texto.capture());
		Matcher m = TOKEN_DEL_CORREO.matcher(texto.getValue());
		assertTrue(m.find());
		String cargaUtil = new String(java.util.Base64.getUrlDecoder().decode(m.group(1).split("\\.")[1]));
		return JsonPath.read(cargaUtil, "$.huella");
	}

	private String firmar(String tipo, String id, Instant vence, String huella) {
		Instant emitido = vence.minus(Duration.ofMinutes(30));
		JwtClaimsSet claims = JwtClaimsSet.builder().subject(id).claim("tipo", tipo).claim("huella", huella)
				.issuedAt(emitido).expiresAt(vence).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private static void ejecutar(Connection c, String sql) throws SQLException {
		try (Statement s = c.createStatement()) {
			s.execute(sql);
		}
	}

	// 200 si la operación termina; el código de su ErrorApi si la regla la rechaza.
	private static int codigoDe(Runnable operacion) {
		try {
			operacion.run();
			return 200;
		} catch (ErrorApi e) {
			return e.getStatus().value();
		}
	}

	// Lanza las dos tareas a la vez (con una barrera) y devuelve sus resultados en el orden en que se dieron.
	@SafeVarargs
	private static List<Integer> enParalelo(Callable<Integer>... tareas) throws Exception {
		ExecutorService hilos = Executors.newFixedThreadPool(tareas.length);
		try {
			CountDownLatch salida = new CountDownLatch(tareas.length);
			List<Future<Integer>> futuros = new java.util.ArrayList<>();
			for (Callable<Integer> tarea : tareas) {
				futuros.add(hilos.submit(() -> {
					salida.countDown();
					salida.await();
					return tarea.call();
				}));
			}
			List<Integer> resultados = new java.util.ArrayList<>();
			for (Future<Integer> futuro : futuros) {
				resultados.add(futuro.get());
			}
			return resultados;
		} finally {
			hilos.shutdownNow();
		}
	}

}
