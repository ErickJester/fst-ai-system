// Carpeta lógica de esta clase: com/ipn/mx/fst/config.
package com.ipn.mx.fst.config;

// HttpServletRequest y HttpServletResponse: la petición que entra y la respuesta que sale, al nivel más
// bajo (el del motor de servlets). Aquí se usan porque los manejadores de error de Spring Security trabajan
// a ese nivel, antes de que exista cualquier controller.
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// IOException: error al escribir en la respuesta.
import java.io.IOException;
// StandardCharsets: las codificaciones de texto que siempre existen en Java. Se usa UTF_8 para convertir el
// secreto a bytes de forma predecible en cualquier sistema operativo.
import java.nio.charset.StandardCharsets;
// List: lista de elementos (los orígenes de CORS, los métodos permitidos...).
import java.util.List;
// SecretKey: una llave secreta de criptografía simétrica, es decir, la MISMA llave firma y verifica.
import javax.crypto.SecretKey;
// SecretKeySpec: la forma de construir una SecretKey a partir de unos bytes que ya tienes.
import javax.crypto.spec.SecretKeySpec;
// @Value: inyecta el valor de una propiedad de application.properties o del entorno.
import org.springframework.beans.factory.annotation.Value;
// @Bean y @Configuration: esta clase aporta configuración; cada método @Bean construye un objeto que Spring
// guarda y reparte.
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// HttpMethod: el enumerado con GET, POST, PUT... Se usa para que una regla aplique solo a un verbo.
import org.springframework.http.HttpMethod;
// MediaType: los tipos de contenido ("application/json"). Se usa al escribir los errores a mano.
import org.springframework.http.MediaType;
// Customizer: ayuda de Spring Security para decir "configura esto con lo que ya hay por omisión".
import org.springframework.security.config.Customizer;
// HttpSecurity: el constructor paso a paso de las reglas de seguridad de la API.
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
// @EnableWebSecurity: enciende la seguridad web de Spring en esta aplicación.
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
// AbstractHttpConfigurer: la clase base de los configuradores; se usa su método disable como referencia
// (AbstractHttpConfigurer::disable) para apagar CSRF.
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
// SessionCreationPolicy: cuándo se crea sesión en el servidor. Aquí, nunca (STATELESS).
import org.springframework.security.config.http.SessionCreationPolicy;
// BCryptPasswordEncoder: el algoritmo bcrypt para guardar contraseñas. PasswordEncoder es su interfaz.
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
// DelegatingOAuth2TokenValidator: junta varios validadores en uno; el token debe pasarlos todos.
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
// MacAlgorithm: los algoritmos de firma simétrica. Aquí HS256.
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
// JwtClaimValidator: valida un claim concreto del token con la condición que le pases.
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
// JwtDecoder y JwtEncoder: las interfaces de "verificar y abrir un token" y "crear y firmar un token".
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
// JwtValidators: fábrica con los validadores estándar (firma y vigencia).
import org.springframework.security.oauth2.jwt.JwtValidators;
// NimbusJwtDecoder y NimbusJwtEncoder: las implementaciones concretas, basadas en la biblioteca Nimbus.
// Son las que trae Spring; por eso el pom.xml explica que no se usa jjwt.
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
// BearerTokenAuthenticationFilter: el filtro de Spring que lee la cabecera Authorization. Se nombra para
// colocar FiltroCambioPendiente justo DESPUÉS de él.
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
// SecurityFilterChain: el resultado de toda esta configuración, la cadena de filtros ya armada.
import org.springframework.security.web.SecurityFilterChain;
// Las clases de CORS: la configuración y la fuente que dice qué configuración aplica a qué rutas.
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Seguridad de la API: tokens JWT firmados con HS256, sin sesión en el servidor. Nada de esto se vio en la
// práctica (allá no había inicio de sesión), así que cada pieza lleva su porqué.
//
// Qué es un JWT: un texto con tres partes separadas por puntos (cabecera.contenido.firma). El contenido va
// codificado, NO cifrado: cualquiera puede leerlo. Lo que no puede hacer nadie sin el secreto es cambiarlo,
// porque la firma dejaría de cuadrar. Por eso en el token solo viaja el identificador y el rol se lee de la
// base (ConvertidorJwt).
// Qué es HS256: firma con una llave secreta compartida (simétrica). Quien firma y quien verifica es el mismo
// servidor, así que no hace falta el esquema de llave pública/privada.
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

	// Contraseñas con bcrypt, como pide la guía (el hash de la tabla usuario).
	// Bcrypt es lento a propósito: hace caro probar contraseñas por fuerza bruta. Además mete una "sal"
	// distinta en cada hash, así que dos personas con la misma contraseña guardan hashes diferentes.
	// Este @Bean es el que inyectan AdminInicial y AuthServiceImpl: hay UNO solo en todo el sistema.
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// HS256 necesita una llave de al menos 256 bits. Si el secreto es más corto (o falta), el backend no
	// arranca: es mejor que arrancar firmando tokens con una llave que cualquiera adivina.
	//
	// 256 bits = 32 bytes = 32 caracteres si son ASCII, de ahí el número 32 de abajo.
	// Lanzar la excepción dentro de un @Bean detiene el arranque de Spring: el contenedor muere al levantar
	// y el error sale en el log, en vez de descubrirse semanas después.
	@Bean
	public SecretKey llaveJwt(@Value("${app.jwt.secreto}") String secreto) {
		if (secreto == null || secreto.length() < 32) {
			throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
		}
		// getBytes(UTF_8): se fija la codificación a propósito. Sin ella, Java usaría la del sistema y el
		// mismo secreto daría bytes distintos en Windows y en el contenedor Linux, invalidando los tokens.
		// "HmacSHA256" es el nombre con el que Java conoce al algoritmo que HS256 llama así.
		return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	// El que FIRMA tokens nuevos. Lo usa AuthServiceImpl al iniciar sesión y al generar los tokens de video
	// y de restablecer contraseña. Recibe la llave del @Bean de arriba: Spring ve el parámetro SecretKey y
	// le pasa el objeto que ya construyó.
	@Bean
	public JwtEncoder jwtEncoder(SecretKey llaveJwt) {
		return NimbusJwtEncoder.withSecretKey(llaveJwt).build();
	}

	// Además de la firma y la vigencia (createDefault), exige tipo = "sesion": los tokens de restablecer
	// contraseña y de video no deben servir como sesión.
	//
	// Esta es la pieza que convierte "tengo un token válido" en "tengo un token válido DE ESTE TIPO".
	// Sin ella, el token de un enlace de correo serviría para entrar a toda la API.
	@Bean
	public JwtDecoder jwtDecoder(SecretKey llaveJwt) {
		// macAlgorithm(HS256): se fija el algoritmo en vez de aceptar el que diga el token. Aceptar el que
		// diga el token es un fallo clásico de seguridad en JWT.
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(llaveJwt)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		// setJwtValidator reemplaza la validación por omisión; por eso hay que volver a incluir
		// createDefault() (firma y vigencia) además del validador propio. Si solo se pusiera el del claim,
		// se dejaría de comprobar que el token no haya vencido.
		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefault(),
				// "sesion"::equals es una referencia a método: equivale a valor -> "sesion".equals(valor).
				// Escrito en ese orden, no truena si el token no trae el claim y llega null.
				new JwtClaimValidator<String>("tipo", "sesion"::equals)));
		return decodificador;
	}

	// Con Spring Security el CORS va aquí y no en cada controller (@CrossOrigin): el preflight (OPTIONS)
	// llega antes que el controller y debe poder pasar sin token.
	// Los orígenes permitidos (las páginas que pueden llamar a la API desde un navegador) vienen de
	// CORS_ORIGINS, separados por comas; por omisión, el frontend de desarrollo (Vite). En la versión
	// portable la página y la API salen por el mismo nginx, pero nginx reenvía el Host SIN el puerto y
	// Spring no reconoce la petición como del mismo origen (responde 403 "Invalid CORS request" aunque el
	// navegador no esté haciendo nada raro): por eso el compose portable declara ahí su propia dirección.
	//
	// Qué es CORS: una regla del NAVEGADOR. Una página servida desde un origen no puede leer respuestas de
	// otro origen salvo que ese otro lo autorice con cabeceras. Origen = esquema + dominio + puerto, así que
	// localhost:5173 y localhost:8084 son orígenes distintos.
	// Qué es el preflight: antes de la petición real, el navegador manda un OPTIONS preguntando si se
	// permite. Ese OPTIONS no lleva token, y por eso CORS tiene que resolverse antes de pedir autenticación.
	@Bean
	// El parámetro es List<String> y la propiedad es un texto con comas: Spring hace solo esa conversión.
	public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origenes}") List<String> origenes) {
		CorsConfiguration cors = new CorsConfiguration();
		// Qué páginas pueden llamar a esta API desde un navegador.
		cors.setAllowedOrigins(origenes);
		// Qué verbos se permiten. OPTIONS va en la lista porque es el del preflight.
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		// Qué cabeceras puede MANDAR la página: el token y el tipo de contenido del cuerpo.
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// Para que la web lea el nombre de los archivos que descarga (Content-Disposition) cuando la API está en otro
		// origen (desarrollo); por nginx es el mismo origen y no hace falta.
		// "Exposed" es lo contrario de "allowed": de las cabeceras que manda la API, cuáles puede LEER el
		// JavaScript de la página. Por omisión no ve casi ninguna.
		cors.setExposedHeaders(List.of("Content-Disposition"));

		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		// "/**" = todas las rutas de la API con la misma configuración.
		fuente.registerCorsConfiguration("/**", cors);
		return fuente;
	}

	// El método central: arma la cadena de filtros por la que pasa TODA petición antes de llegar a un
	// controller. El orden de las reglas importa: Spring aplica la PRIMERA que coincida con la petición.
	@Bean
	public SecurityFilterChain filtros(HttpSecurity http, ConvertidorJwt convertidorJwt) throws Exception {
		http
				// CSRF protege las cookies de sesión; esta API no usa cookies, el token viaja en una cabecera.
				// El ataque CSRF consiste en que otra página haga que TU navegador mande una petición con tus
				// cookies adjuntas, porque el navegador las manda solo. Una cabecera Authorization no se manda
				// sola: la pone el JavaScript de la página. Sin cookies no hay CSRF que proteger.
				.csrf(AbstractHttpConfigurer::disable)
				// Aplica el @Bean de CORS de arriba.
				.cors(Customizer.withDefaults())
				// STATELESS: el servidor no guarda nada entre peticiones. Cada una se identifica sola con su
				// token. Es lo que permite correr varias copias del backend sin compartir sesiones.
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Aquí empiezan las reglas de "quién entra a dónde".
				.authorizeHttpRequests(rutas -> rutas
						// permitAll = sin token. Son las tres que se usan JUSTO ANTES de tener sesión.
						.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/recover", "/auth/reset").permitAll()
						// /health lo consulta la web al cargar cualquier pantalla para saber versión y modo.
						.requestMatchers(HttpMethod.GET, "/health").permitAll()
						// El video de la revisión: la etiqueta <video> no puede mandar Authorization, así que la
						// ruta no pide sesión y el propio endpoint exige un token de VIDEO en ?vt= (RevisionService).
						// Solo ese GET y esa ruta exacta; todo lo demás de /experiments sigue pidiendo sesión.
						// El * sustituye un tramo de la ruta (un id), no varios: por eso no abre nada más.
						.requestMatchers(HttpMethod.GET, "/experiments/*/analyses/*/video").permitAll()
						// La documentación y la página de error. Recuerda que en producción springdoc va
						// apagado por SWAGGER_HABILITADO=false y estas rutas dan 404 de todos modos.
						.requestMatchers("/v3/api-docs/**", "/documentacion/**", "/swagger-ui/**", "/error").permitAll()
						// R-07: todo /admin/** es solo de Administradores (403 a los demás). El rol sale de la base en cada petición
						// (ConvertidorJwt), no del token.
						// hasRole("ADMINISTRADOR") busca la authority "ROLE_ADMINISTRADOR": Spring pone el
						// prefijo solo. Por eso ConvertidorJwt la crea con ese prefijo exacto.
						.requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
						// La regla de cierre: cualquier otra ruta exige sesión. Va al final a propósito, para
						// que una ruta nueva nazca protegida mientras nadie la liste arriba.
						.anyRequest().authenticated())
				// El token se acepta solo en la cabecera "Authorization: Bearer" (el resolver por defecto),
				// nunca en la URL, porque las URL quedan en los registros.
				.oauth2ResourceServer(recurso -> recurso
						// Aquí se engancha ConvertidorJwt: es quien convierte el token en el usuario.
						.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorJwt))
						// 401 = "no sé quién eres" (falta token, está vencido o no es válido).
						.authenticationEntryPoint((peticion, respuesta, e) -> escribir(respuesta, 401, "Sesión no válida o vencida"))
						// 403 = "sé quién eres, pero no te toca" (un investigador pidiendo /admin/**).
						.accessDeniedHandler((peticion, respuesta, e) -> escribir(respuesta, 403, "No tienes permiso para esta acción")))
				// Los mismos dos manejadores, pero para los errores que ocurren fuera del procesamiento del
				// token. Se repiten porque son dos puntos distintos de la cadena y cada uno tiene el suyo;
				// sin esta segunda pareja, algunos casos devolverían la página de error de Tomcat en HTML.
				.exceptionHandling(errores -> errores
						.authenticationEntryPoint((peticion, respuesta, e) -> escribir(respuesta, 401, "Sesión no válida o vencida"))
						.accessDeniedHandler((peticion, respuesta, e) -> escribir(respuesta, 403, "No tienes permiso para esta acción")))
				// Debe ir después de la autenticación del token: necesita saber quién es y si le falta cambiar
				// la contraseña.
				// addFilterAfter coloca el filtro en una posición EXACTA de la cadena. Si fuera antes, el
				// SecurityContextHolder aún estaría vacío y el filtro no vería la marca CAMBIO_PENDIENTE.
				.addFilterAfter(new FiltroCambioPendiente(), BearerTokenAuthenticationFilter.class);
		// build() cierra la configuración y devuelve la cadena ya armada.
		return http.build();
	}

	// Los filtros de seguridad actúan antes que los controllers, así que ManejadorErrores no los ve. El texto
	// es fijo, por eso el envoltorio {data, error, status} se escribe a mano.
	// static y sin private: también lo usa FiltroCambioPendiente.
	//
	// Es la misma forma que arma Respuesta.error(...), escrita a mano porque aquí no hay Jackson ni
	// controller todavía. El cliente recibe siempre la misma estructura, venga el error de donde venga.
	static void escribir(HttpServletResponse respuesta, int codigo, String mensaje) throws IOException {
		respuesta.setStatus(codigo);
		// charset=UTF-8 explícito: sin él, los acentos de "Sesión" llegan rotos al navegador.
		respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
		// Se concatena a mano porque los tres mensajes son constantes del código y no llevan comillas ni
		// barras que haya que escapar. Con texto que viniera del usuario esto sería un error: habría que
		// serializarlo con Jackson.
		respuesta.getWriter().write("{\"data\":null,\"error\":\"" + mensaje + "\",\"status\":" + codigo + "}");
	}

}
