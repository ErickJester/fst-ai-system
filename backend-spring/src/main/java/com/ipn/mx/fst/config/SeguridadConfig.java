package com.ipn.mx.fst.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Seguridad de la API: tokens JWT firmados con HS256, sin sesión en el servidor. Nada de esto se vio en la
// práctica (allá no había inicio de sesión), así que cada pieza lleva su porqué.
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

	// Contraseñas con bcrypt, como pide la guía (el hash de la tabla usuario).
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// HS256 necesita una llave de al menos 256 bits. Si el secreto es más corto (o falta), el backend no
	// arranca: es mejor que arrancar firmando tokens con una llave que cualquiera adivina.
	@Bean
	public SecretKey llaveJwt(@Value("${app.jwt.secreto}") String secreto) {
		if (secreto == null || secreto.length() < 32) {
			throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
		}
		return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey llaveJwt) {
		return NimbusJwtEncoder.withSecretKey(llaveJwt).build();
	}

	// Además de la firma y la vigencia (createDefault), exige tipo = "sesion": los tokens de restablecer
	// contraseña y de video no deben servir como sesión.
	@Bean
	public JwtDecoder jwtDecoder(SecretKey llaveJwt) {
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(llaveJwt)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefault(),
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
	@Bean
	public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origenes}") List<String> origenes) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(origenes);
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// Para que la web lea el nombre de los archivos que descarga (Content-Disposition) cuando la API está en otro
		// origen (desarrollo); por nginx es el mismo origen y no hace falta.
		cors.setExposedHeaders(List.of("Content-Disposition"));

		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/**", cors);
		return fuente;
	}

	@Bean
	public SecurityFilterChain filtros(HttpSecurity http, ConvertidorJwt convertidorJwt) throws Exception {
		http
				// CSRF protege las cookies de sesión; esta API no usa cookies, el token viaja en una cabecera.
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rutas -> rutas
						.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/recover", "/auth/reset").permitAll()
						.requestMatchers(HttpMethod.GET, "/health").permitAll()
						// El video de la revisión: la etiqueta <video> no puede mandar Authorization, así que la
						// ruta no pide sesión y el propio endpoint exige un token de VIDEO en ?vt= (RevisionService).
						// Solo ese GET y esa ruta exacta; todo lo demás de /experiments sigue pidiendo sesión.
						.requestMatchers(HttpMethod.GET, "/experiments/*/analyses/*/video").permitAll()
						.requestMatchers("/v3/api-docs/**", "/documentacion/**", "/swagger-ui/**", "/error").permitAll()
						// R-07: todo /admin/** es solo de Administradores (403 a los demás). El rol sale de la base en cada petición
						// (ConvertidorJwt), no del token.
						.requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
						.anyRequest().authenticated())
				// El token se acepta solo en la cabecera "Authorization: Bearer" (el resolver por defecto),
				// nunca en la URL, porque las URL quedan en los registros.
				.oauth2ResourceServer(recurso -> recurso
						.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorJwt))
						.authenticationEntryPoint((peticion, respuesta, e) -> escribir(respuesta, 401, "Sesión no válida o vencida"))
						.accessDeniedHandler((peticion, respuesta, e) -> escribir(respuesta, 403, "No tienes permiso para esta acción")))
				.exceptionHandling(errores -> errores
						.authenticationEntryPoint((peticion, respuesta, e) -> escribir(respuesta, 401, "Sesión no válida o vencida"))
						.accessDeniedHandler((peticion, respuesta, e) -> escribir(respuesta, 403, "No tienes permiso para esta acción")))
				// Debe ir después de la autenticación del token: necesita saber quién es y si le falta cambiar
				// la contraseña.
				.addFilterAfter(new FiltroCambioPendiente(), BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	// Los filtros de seguridad actúan antes que los controllers, así que ManejadorErrores no los ve. El texto
	// es fijo, por eso el envoltorio {data, error, status} se escribe a mano.
	// static y sin private: también lo usa FiltroCambioPendiente.
	static void escribir(HttpServletResponse respuesta, int codigo, String mensaje) throws IOException {
		respuesta.setStatus(codigo);
		respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
		respuesta.getWriter().write("{\"data\":null,\"error\":\"" + mensaje + "\",\"status\":" + codigo + "}");
	}

}
