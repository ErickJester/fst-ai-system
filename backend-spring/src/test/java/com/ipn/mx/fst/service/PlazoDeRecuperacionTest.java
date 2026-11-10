// Mismo paquete que las clases que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
// Comparadores y utilidades de Mockito: "cualquier texto", "este valor exacto", limpiar un simulado,
// esperar hasta un plazo y comprobar una llamada.
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
// Base64: la codificación con la que viaja el contenido de un JWT.
import java.util.Base64;
// Matcher y Pattern: sacar el token del texto del correo con una expresión regular.
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
// ArgumentCaptor: "captura" lo que se le pasó a un método simulado, para poder inspeccionarlo después.
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
// @MockitoBean: sustituye un bean de Spring por un simulado, solo dentro de esta prueba.
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Plazo del enlace para restablecer la contraseña, contra una PostgreSQL de verdad. Mismas condiciones que
// AdministracionYRecuperacionTest: corre solo con FST_TEST_DB_URL apuntando a una base VACÍA con el esquema de
// db/init/001_esquema.sql (cada prueba borra todos los datos). No duerme una hora: el enlace de recuperar lleva
// iat y exp; para el enlace vencido se firma otro con las mismas reglas pero con la fecha ya pasada (la misma
// llave del JWT, el mismo tipo "reset" y la misma huella del hash). El plazo se toma de app.reset.minutos.
//
// El truco de esta prueba es la ÚLTIMA parte del comentario: probar que un enlace vence a la hora sin
// esperar una hora. En vez de dormir, se FABRICA un enlace igual al real pero con una fecha de emisión
// antigua, usando la misma llave y las mismas reglas de AuthServiceImpl.firmarEnlace. Así se prueba el
// comportamiento sin esperar.
// Qué es iat y exp: son claims estándar de un JWT. "iat" (issued at) es cuándo se emitió y "exp" cuándo
// vence, ambos en segundos desde 1970.
// El comentario del @Tag es de nivel de clase: TODAS las pruebas de este archivo se pueden mostrar en la
// defensa del trabajo terminal, que es el único sitio donde aparece así.
@Tag("trabajo-terminal")
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-plazo",
		// Se fija el plazo en 60 minutos para que los cálculos de la prueba (59 y 62 minutos) sean
		// independientes de lo que valga en producción.
		"app.reset.minutos=60"
})
@AutoConfigureMockMvc
class PlazoDeRecuperacionTest {

	private static final String CLAVE = "clave-de-prueba-1";
	// La expresión regular que saca el token del enlace del correo: lo que va detrás de
	// "/restablecer?token=". [\w.\-]+ son letras, números, puntos y guiones, que es el alfabeto de un JWT.
	private static final Pattern TOKEN_DEL_CORREO = Pattern.compile("/restablecer\\?token=([\\w.\\-]+)");

	// @Value también funciona en una prueba: lee el 60 que se fijó arriba.
	@Value("${app.reset.minutos}")
	private long minutos;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// El MISMO codificador de la aplicación: es lo que permite firmar enlaces falsos que el servicio
	// acepta como propios (misma llave, mismo algoritmo).
	@Autowired
	private JwtEncoder jwtEncoder;

	// Se sustituye el servicio de correo por un simulado: así la prueba no manda correos, y además puede
	// LEER el correo que el sistema habría enviado, que es de donde saca el enlace.
	@MockitoBean
	private EmailService emailService;

	@BeforeEach
	void base_limpia() {
		// Se limpia también el simulado: sin esto, los correos de una prueba se verían en la siguiente.
		reset(emailService);
		// De las hojas hacia arriba, por las llaves foráneas.
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
		// El segundo argumento de assertEquals es el mensaje si falla: avisa de que la prueba ASUME ese
		// valor, para que quien cambie la propiedad de arriba entienda por qué se rompe.
		assertEquals(60L, minutos, "la prueba fija app.reset.minutos en 60");

		String enlace = pedirEnlace("inv@fst.mx");
		long emitido = numero(enlace, "$.iat");
		long vence = numero(enlace, "$.exp");
		// La diferencia entre vencimiento y emisión son 60 minutos en segundos: 3 600.
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
		// Es el truco de AuthServiceImpl: el "un solo uso" sale de que el token lleva una huella del hash
		// de la contraseña vigente al emitirlo, y al cambiarla deja de cuadrar. No hay tabla de enlaces
		// usados. Se comprueba además que el MENSAJE sea el genérico, que no distingue si venció o se usó.
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + enlace + "\",\"contrasenaNueva\":\"clave-otra-1\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El enlace venció o ya se usó."));
		// Y que la contraseña NUEVA es la que quedó, no la del segundo intento.
		login("inv@fst.mx", "clave-nueva-1");
	}

	@Test
	void un_enlace_emitido_hace_59_minutos_todavia_sirve() throws Exception {
		cuenta("2", "inv@fst.mx");
		// Se pide un enlace de verdad solo para sacar su huella: la del hash vigente de esa cuenta.
		String huella = huella(pedirEnlace("inv@fst.mx"));

		// Emitido hace 59 minutos: su plazo de 60 todavía no termina (le quedan unos 60 segundos).
		// Es el borde de ABAJO del plazo: justo antes de vencer, tiene que servir.
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
		//
		// Por qué 62 y no 61: el validador de JWT de Spring tolera 60 segundos de diferencia entre relojes
		// (para no rechazar tokens por un desajuste mínimo entre servidores). Con 61 minutos, el enlace
		// seguiría dentro de esa tolerancia y se aceptaría, que sería un falso fallo de la prueba.
		String vencido = firmar("2", huella, Instant.now().minus(Duration.ofMinutes(62)));
		mvc.perform(post("/auth/reset").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + vencido + "\",\"contrasenaNueva\":\"clave-nueva-1\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El enlace venció o ya se usó."));
		// La contraseña ORIGINAL sigue funcionando: el enlace vencido no cambió nada.
		login("inv@fst.mx", CLAVE);
	}

	// ---------------------------------------------------------------- ayudas

	private void cuenta(String id, String correo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, true, false)", id, correo, passwordEncoder.encode(CLAVE));
	}

	// Pide el enlace y devuelve el token que llegó por correo (el correo sale en otro hilo: por eso el timeout).
	//
	// AuthServiceImpl.recuperar manda el correo con CompletableFuture.runAsync, es decir, en otro hilo, y
	// puede tardar unos milisegundos tras responder la petición. verify con timeout(5000) ESPERA hasta
	// cinco segundos a que la llamada ocurra, en vez de fallar si todavía no ha pasado.
	private String pedirEnlace(String correo) throws Exception {
		reset(emailService);
		mvc.perform(post("/auth/recover").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\"}")).andExpect(status().isOk());
		// El captor se queda con el TERCER argumento de enviarCorreo (el texto), mientras eq y anyString
		// fijan los otros dos.
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(emailService, timeout(5000)).enviarCorreo(eq(correo), anyString(), texto.capture());
		Matcher m = TOKEN_DEL_CORREO.matcher(texto.getValue());
		// El segundo argumento imprime el texto del correo si no se encontró el token.
		assertTrue(m.find(), texto.getValue());
		return m.group(1);
	}

	// El contenido (el "payload") de un JWT: su segunda parte, entre los dos puntos, decodificada.
	// Un JWT es cabecera.contenido.firma, cada parte en Base64 URL. split("\\.") parte por los puntos y
	// [1] toma el del medio. Es solo lectura: el contenido de un JWT no está cifrado, cualquiera lo lee.
	private static String carga(String token) {
		return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
	}

	private static long numero(String token, String ruta) {
		return ((Number) JsonPath.read(carga(token), ruta)).longValue();
	}

	// La huella del hash que lleva el enlace, que es lo que hay que copiar al fabricar uno falso.
	private static String huella(String token) {
		return JsonPath.read(carga(token), "$.huella");
	}

	// Firma un enlace con la misma llave y las mismas reglas que AuthServiceImpl: tipo "reset", huella, y vigencia de
	// app.reset.minutos desde la fecha de emisión que se pide.
	//
	// Es la pieza que permite probar el vencimiento sin esperar: replica firmarEnlace pero dejando elegir
	// la fecha de emisión. Se puede hacer porque el codificador es el MISMO bean de la aplicación, así que
	// su firma es válida para el decodificador.
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

	// Inicia sesión con una contraseña concreta y comprueba que funcione: es la forma de saber CUÁL
	// contraseña quedó guardada tras un intento de restablecer.
	private void login(String correo, String contrasena) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
				.andExpect(status().isOk()).andReturn();
		assertTrue(r.getResponse().getContentAsString().contains("\"token\""));
	}
}
