package com.ipn.mx.fst.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// Mientras la cuenta tenga cambioRequerido (contraseña temporal, guía sección 7), la sesión solo sirve para
// cambiar la contraseña o cerrar sesión; todo lo demás responde 403. ConvertidorJwt marca esa situación con
// la authority CAMBIO_PENDIENTE, que se lee de la base en cada petición: en cuanto la contraseña cambia, el
// mismo token deja de estar bloqueado, sin pedir uno nuevo.
// No es @Component a propósito: Spring Boot registraría además un segundo filtro fuera de la cadena de
// seguridad. SeguridadConfig lo crea y lo coloca justo después de la autenticación del token.
public class FiltroCambioPendiente extends OncePerRequestFilter {

	public static final String AUTHORITY = "CAMBIO_PENDIENTE";

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		boolean pendiente = autenticacion != null && autenticacion.getAuthorities().stream()
				.anyMatch(permiso -> permiso.getAuthority().equals(AUTHORITY));

		if (pendiente && !permitida(peticion)) {
			SeguridadConfig.escribir(respuesta, 403, "Debes cambiar tu contraseña antes de continuar");
			return;
		}
		cadena.doFilter(peticion, respuesta);
	}

	// Lo único que se deja hacer con una contraseña temporal.
	private boolean permitida(HttpServletRequest peticion) {
		String metodo = peticion.getMethod();
		String ruta = peticion.getRequestURI();
		return ("PATCH".equals(metodo) && "/auth/password".equals(ruta))
				|| ("POST".equals(metodo) && "/auth/logout".equals(ruta));
	}

}
