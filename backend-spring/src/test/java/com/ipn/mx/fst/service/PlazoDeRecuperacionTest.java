package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipn.mx.fst.utilerias.service.EmailService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

// Plazo del enlace para restablecer la contraseña, contra una PostgreSQL de verdad. Mismas condiciones que
// AdministracionYRecuperacionTest: corre solo con FST_TEST_DB_URL apuntando a una base VACÍA con el esquema de
// db/init/001_esquema.sql (cada prueba borra todos los datos). No duerme una hora: el enlace de recuperar lleva
// iat y exp; para el enlace vencido se firma otro con las mismas reglas pero con la fecha ya pasada (la misma
// llave del JWT, el mismo tipo "reset" y la misma huella del hash). El plazo se toma de app.reset.minutos.
@Tag("trabajo-terminal")
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-plazo",
		"app.reset.minutos=60"
})
@AutoConfigureMockMvc
class PlazoDeRecuperacionTest {

	private static final String CLAVE = "clave-de-prueba-1";
	private static final Pattern TOKEN_DEL_CORREO = Pattern.compile("/restablecer\\?token=([\\w.\\-]+)");

	@Value("${app.reset.minutos}")
	private long minutos;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@MockitoBean
	private EmailService emailService;

	@BeforeEach
	void base_limpia() {
		reset(emailService);
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
	}

	@Test
	void el_enlace_dura_exactamente_lo_que_marca_app_reset_minutos() throws Exception {
		cuenta("2", "inv@fst.mx");
		assertEquals(60L, minutos, "la prueba fija app.reset.minutos en 60");

		String enlace = pedirEnlace("inv@fst.mx");
		long emitido = numero(enlace, "$.iat");
		long vence = numero(enlace, "$.exp");
		assertEquals(minutos * 60, vence - emitido);
	}

	@Test
	void el_enlace_sirve_dentro_de_su_plazo_y_solo_una_vez() throws Exception {
		cuenta("2", "inv@fst.mx");
		String enlace = pedirEnlace("inv@fst.mx");

		// Recién pedido, dentro de los 60 minutos: cambia la contraseña.
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"clave-nueva-1\"}")).andExpect(status().isOk());

		// Usarlo otra vez falla: la huella ya no coincide con el hash nuevo.
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"clave-otra-1\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El enlace venció o ya se usó."));
		login("inv@fst.mx", "clave-nueva-1");
	}

	@Test
	void un_enlace_emitido_hace_59_minutos_todavia_sirve() throws Exception {
		cuenta("2", "inv@fst.mx");
		String huella = huella(pedirEnlace("inv@fst.mx"));

		// Emitido hace 59 minutos: su plazo de 60 todavía no termina (le quedan unos 60 segundos).
		String casiVencido = firmar("2", huella, Instant.now().minus(Duration.ofMinutes(59)));
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + casiVencido + "\",\"contrasenaNueva\":\"clave-nueva-1\"}")).andExpect(status().isOk());
		login("inv@fst.mx", "clave-nueva-1");
	}

	@Test
	void un_enlace_emitido_hace_62_minutos_ya_no_sirve_y_la_contrasena_no_cambia() throws Exception {
		cuenta("2", "inv@fst.mx");
		String huella = huella(pedirEnlace("inv@fst.mx"));

		// Emitido hace 62 minutos: su plazo de 60 venció hace 2 minutos, más que la tolerancia de reloj del validador (60 s).
		String vencido = firmar("2", huella, Instant.now().minus(Duration.ofMinutes(62)));
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + vencido + "\",\"contrasenaNueva\":\"clave-nueva-1\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El enlace venció o ya se usó."));
		login("inv@fst.mx", CLAVE);
	}

	// ---------------------------------------------------------------- ayudas

	private void cuenta(String id, String correo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, true, false)", id, correo, passwordEncoder.encode(CLAVE));
	}

	// Pide el enlace y devuelve el token que llegó por correo (el correo sale en otro hilo: por eso el timeout).
	private String pedirEnlace(String correo) throws Exception {
		reset(emailService);
		mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\"}")).andExpect(status().isOk());
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(emailService, timeout(5000)).enviarCorreo(eq(correo), anyString(), texto.capture());
		Matcher m = TOKEN_DEL_CORREO.matcher(texto.getValue());
		assertTrue(m.find(), texto.getValue());
		return m.group(1);
	}

	private static String carga(String token) {
		return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
	}

	private static long numero(String token, String ruta) {
		return ((Number) JsonPath.read(carga(token), ruta)).longValue();
	}

	private static String huella(String token) {
		return JsonPath.read(carga(token), "$.huella");
	}

	// Firma un enlace con la misma llave y las mismas reglas que AuthServiceImpl: tipo "reset", huella, y vigencia de
	// app.reset.minutos desde la fecha de emisión que se pide.
	private String firmar(String id, String huella, Instant emitido) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(id)
				.claim("tipo", "reset")
				.claim("huella", huella)
				.issuedAt(emitido)
				.expiresAt(emitido.plus(Duration.ofMinutes(minutos)))
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private void login(String correo, String contrasena) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
				.andExpect(status().isOk()).andReturn();
		assertTrue(r.getResponse().getContentAsString().contains("\"token\""));
	}
}
