// Carpeta lógica de esta clase: com/ipn/mx/fst/config.
package com.ipn.mx.fst.config;

// FilterChain: la "fila" de filtros por los que pasa una petición. Llamar a cadena.doFilter(...) es decir
// "yo ya terminé, que siga el siguiente". Si NO lo llamas, la petición se corta aquí.
import jakarta.servlet.FilterChain;
// ServletException: el error que puede lanzar cualquier pieza del motor de servlets (Tomcat). Va en throws.
import jakarta.servlet.ServletException;
// HttpServletRequest: la petición que llega (método, ruta, cabeceras, cuerpo).
import jakarta.servlet.http.HttpServletRequest;
// HttpServletResponse: la respuesta que se va (código, cabeceras, cuerpo).
import jakarta.servlet.http.HttpServletResponse;
// IOException: error al leer o escribir. Escribir en la respuesta puede fallar, por eso aparece en throws.
import java.io.IOException;
// Authentication: el objeto de Spring Security que representa "quién es el que está pidiendo" y qué permisos trae.
import org.springframework.security.core.Authentication;
// SecurityContextHolder: el almacén donde Spring Security deja al usuario autenticado de LA PETICIÓN ACTUAL.
// Es un almacén por hilo: cada petición ve el suyo, no se mezclan entre usuarios.
import org.springframework.security.core.context.SecurityContextHolder;
// OncePerRequestFilter: clase base de Spring para filtros. Garantiza que el filtro corra UNA sola vez por
// petición, aunque internamente haya reenvíos (forward) que volverían a pasar por la cadena.
import org.springframework.web.filter.OncePerRequestFilter;

// Mientras la cuenta tenga cambioRequerido (contraseña temporal, guía sección 7), la sesión solo sirve para
// cambiar la contraseña o cerrar sesión; todo lo demás responde 401 (ver el porqué abajo). ConvertidorJwt marca esa situación con
// la authority CAMBIO_PENDIENTE, que se lee de la base en cada petición: en cuanto la contraseña cambia, el
// mismo token deja de estar bloqueado, sin pedir uno nuevo.
// No es @Component a propósito: Spring Boot registraría además un segundo filtro fuera de la cadena de
// seguridad. SeguridadConfig lo crea y lo coloca justo después de la autenticación del token.
//
// Qué es un filtro: una pieza que ve TODAS las peticiones antes de que lleguen al controller, y que puede
// dejarlas pasar o cortarlas. Es el lugar correcto para una regla transversal como esta, que aplica a toda
// la API y no a un endpoint concreto.
public class FiltroCambioPendiente extends OncePerRequestFilter {

	// El nombre del permiso (authority) que marca "esta cuenta tiene contraseña temporal".
	// public static final = constante compartida: ConvertidorJwt la pone y este filtro la lee, los dos
	// usando este mismo nombre. Así no se escribe el texto "CAMBIO_PENDIENTE" suelto en dos lugares, donde
	// un error de dedo en uno de ellos rompería la regla en silencio.
	public static final String AUTHORITY = "CAMBIO_PENDIENTE";

	// @Override: este método reemplaza al de OncePerRequestFilter. Es el que Spring llama por cada petición.
	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		// Saca de la petición actual quién está autenticado. Puede ser null en las rutas públicas (/health,
		// /auth/login), donde nadie se identificó: por eso abajo se comprueba != null antes de usarlo.
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		// ¿Tiene la marca de contraseña temporal?
		//   autenticacion != null            hay alguien identificado;
		//   .getAuthorities()                la lista de sus permisos (rol + marcas como esta);
		//   .stream().anyMatch(...)          recorre la lista y devuelve true en cuanto uno cumple;
		//   permiso.getAuthority().equals(AUTHORITY)  ese permiso es exactamente CAMBIO_PENDIENTE.
		// El && corta por la izquierda: si autenticacion es null, no se evalúa lo de la derecha y no truena.
		boolean pendiente = autenticacion != null && autenticacion.getAuthorities().stream()
				.anyMatch(permiso -> permiso.getAuthority().equals(AUTHORITY));

		// Si la contraseña está pendiente de cambio Y la petición no es de las permitidas, se corta aquí.
		if (pendiente && !permitida(peticion)) {
			// 401 y NO 403, a propósito. El interceptor del frontend (services/api.js) manda cada 403 a
			// /experimentos con window.location.href: si el navegador guardó un token de una cuenta que
			// volvió a tener contraseña temporal, GET /experiments y /notifications dan 403, la página
			// recarga /experimentos, vuelve a dar 403 y así en un ciclo sin fin. Con 401 el interceptor
			// borra el token y lleva al login; al entrar de nuevo, la respuesta del login trae
			// cambioRequerido: true y el frontend lleva a /primer-acceso. Este 401 obliga a volver a entrar.
			SeguridadConfig.escribir(respuesta, 401, "Debes cambiar tu contraseña antes de continuar");
			// return sin llamar a cadena.doFilter: la petición NO sigue, nunca llega al controller.
			// Es la única forma de cortar; si se olvidara este return, se escribiría el 401 y además se
			// ejecutaría la petición normal.
			return;
		}
		// Caso normal: deja pasar la petición al siguiente filtro y, al final, al controller.
		cadena.doFilter(peticion, respuesta);
	}

	// Lo único que se deja hacer con una contraseña temporal. GET /health es público (lo pide la web al cargar cualquier
	// pantalla, también /primer-acceso, para saber la versión y el modo): si con el token de la temporal diera 401, recargar la
	// pantalla de primer acceso cerraría la sesión y habría que volver a entrar (visto en el navegador el 28-oct, encargo 46).
	//
	// private: solo se usa dentro de esta clase, nadie más tiene por qué llamarla.
	private boolean permitida(HttpServletRequest peticion) {
		// El verbo HTTP: GET, POST, PATCH, DELETE...
		String metodo = peticion.getMethod();
		// La ruta pedida, sin el dominio ni los parámetros: "/auth/password", por ejemplo.
		String ruta = peticion.getRequestURI();
		// Se comparan método Y ruta juntos, no solo la ruta: PATCH /auth/password se permite, pero un
		// hipotético DELETE /auth/password no. "/auth/password".equals(ruta) y no ruta.equals("/auth/password")
		// porque así, si ruta llegara a ser null, no truena.
		return ("PATCH".equals(metodo) && "/auth/password".equals(ruta))
				|| ("POST".equals(metodo) && "/auth/logout".equals(ruta))
				|| ("GET".equals(metodo) && "/health".equals(ruta));
	}

}
