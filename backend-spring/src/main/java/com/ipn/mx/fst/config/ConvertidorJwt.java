// Carpeta lógica de esta clase: com/ipn/mx/fst/config.
package com.ipn.mx.fst.config;

// Usuario: la entidad JPA de la tabla usuario. Aquí se usa para comprobar que la cuenta sigue activa.
import com.ipn.mx.fst.domain.Usuario;
// AdministradorRepository: el acceso a la tabla administrador. Sirve para saber si esta cuenta es admin.
import com.ipn.mx.fst.repository.AdministradorRepository;
// UsuarioRepository: el acceso a la tabla usuario.
import com.ipn.mx.fst.repository.UsuarioRepository;
// ArrayList: una lista que puede crecer. Se usa para ir juntando los permisos.
import java.util.ArrayList;
// List: la interfaz de "lista". Se declara con la interfaz y se construye con ArrayList, para no atarse a
// una implementación concreta.
import java.util.List;
// @Autowired: pide a Spring que inyecte aquí el objeto que ya creó (no se hace "new" a mano).
import org.springframework.beans.factory.annotation.Autowired;
// Converter<A, B>: interfaz de Spring para "algo que transforma un A en un B". Aquí, un Jwt en una autenticación.
import org.springframework.core.convert.converter.Converter;
// AbstractAuthenticationToken: la clase base de los objetos de autenticación de Spring Security.
import org.springframework.security.authentication.AbstractAuthenticationToken;
// BadCredentialsException: la excepción que significa "las credenciales no sirven". Spring Security la
// traduce a un 401 a través del authenticationEntryPoint de SeguridadConfig.
import org.springframework.security.authentication.BadCredentialsException;
// GrantedAuthority: un permiso concedido. Es la interfaz; cada permiso es un texto ("ROLE_ADMINISTRADOR").
import org.springframework.security.core.GrantedAuthority;
// SimpleGrantedAuthority: la implementación corriente de GrantedAuthority, que solo envuelve ese texto.
import org.springframework.security.core.authority.SimpleGrantedAuthority;
// Jwt: el token ya decodificado y validado por Spring (firma, vigencia y el claim tipo=sesion).
import org.springframework.security.oauth2.jwt.Jwt;
// JwtAuthenticationToken: la autenticación concreta que representa "este usuario entró con este JWT".
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
// @Component: marca la clase para que Spring la cree y la tenga disponible para inyectarla.
import org.springframework.stereotype.Component;

// Convierte un token ya validado (firma, vigencia y tipo) en el usuario autenticado de la petición.
// Es la verificación "en la base" que pide la guía (sección 6): el token solo trae el identificador, y aquí
// se comprueba en cada petición que la cuenta siga activa y se lee si es Administrador. La usará /admin/**
// en la sección 3.
//
// Por qué existe esta clase: un JWT es un papel firmado que el servidor no guarda. Si el rol viajara DENTRO
// del token, degradar a un administrador no tendría efecto hasta que su token venciera (hasta 7 días).
// Leyendo el rol de la base en cada petición, el cambio es inmediato. El precio son dos consultas por
// petición, que es barato comparado con el agujero de seguridad.
@Component
public class ConvertidorJwt implements Converter<Jwt, AbstractAuthenticationToken> {

	// Spring inyecta aquí la implementación del repositorio (la genera Spring Data a partir de la interfaz).
	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorRepository administradorRepository;

	// @Override: implementa el método de la interfaz Converter. Spring Security lo llama en cada petición
	// que trae un token válido, justo después de comprobar la firma y antes de llegar al controller.
	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		// getSubject() es el claim "sub" del token, el estándar para "de quién es". AuthServiceImpl guarda
		// ahí el idInstitucional al firmar el token.
		String id = jwt.getSubject();

		// Una cuenta dada de baja o borrada deja de servir aunque su token no haya vencido. La excepción llega
		// al authenticationEntryPoint y se responde 401.
		//
		// findById devuelve un Optional (una caja que puede venir vacía); .orElse(null) saca el contenido o
		// null si no había. Se usa null aquí porque la línea siguiente ya comprueba las dos cosas juntas.
		Usuario usuario = usuarioRepository.findById(id).orElse(null);
		// Dos casos distintos con la misma respuesta: la cuenta ya no existe, o existe pero está dada de baja.
		// Se responde lo mismo a propósito: decir cuál de los dos es daría información a quien prueba ids.
		if (usuario == null || !usuario.getActivo()) {
			throw new BadCredentialsException("Cuenta inactiva");
		}

		// El rol sale de la base, no del token (ver el comentario de arriba). existsById es una consulta de
		// existencia: pregunta "¿hay fila?" sin traerse la fila entera, que es más barato.
		// El prefijo "ROLE_" es una convención obligatoria de Spring Security: cuando en SeguridadConfig se
		// escribe hasRole("ADMINISTRADOR"), Spring le antepone "ROLE_" y busca "ROLE_ADMINISTRADOR".
		String rol = administradorRepository.existsById(id) ? "ROLE_ADMINISTRADOR" : "ROLE_INVESTIGADOR";
		// La lista de permisos de este usuario para esta petición. Se arma de cero cada vez.
		List<GrantedAuthority> permisos = new ArrayList<>();
		permisos.add(new SimpleGrantedAuthority(rol));
		// Contraseña temporal: FiltroCambioPendiente bloquea todo menos cambiarla (guía sección 7).
		// Fíjate en que esto es un permiso más, no un rol: no lleva el prefijo ROLE_. Es una marca que otro
		// filtro leerá más adelante en la cadena.
		if (usuario.getCambioRequerido()) {
			permisos.add(new SimpleGrantedAuthority(FiltroCambioPendiente.AUTHORITY));
		}

		// El tercer argumento es el nombre: así authentication.getName() es el idInstitucional.
		// Esto importa mucho en la práctica: en los controllers se escribe authentication.getName() para
		// saber quién hace la petición, y gracias a esta línea eso devuelve el idInstitucional y no, por
		// ejemplo, el token entero.
		return new JwtAuthenticationToken(jwt, permisos, id);
	}

}
