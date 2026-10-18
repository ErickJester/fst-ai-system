package com.ipn.mx.fst.config;

import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.repository.AdministradorRepository;
import com.ipn.mx.fst.repository.UsuarioRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

// Convierte un token ya validado (firma, vigencia y tipo) en el usuario autenticado de la petición.
// Es la verificación "en la base" que pide la guía (sección 6): el token solo trae el identificador, y aquí
// se comprueba en cada petición que la cuenta siga activa y se lee si es Administrador. La usará /admin/**
// en la sección 3.
@Component
public class ConvertidorJwt implements Converter<Jwt, AbstractAuthenticationToken> {

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorRepository administradorRepository;

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		String id = jwt.getSubject();

		// Una cuenta dada de baja o borrada deja de servir aunque su token no haya vencido. La excepción llega
		// al authenticationEntryPoint y se responde 401.
		Usuario usuario = usuarioRepository.findById(id).orElse(null);
		if (usuario == null || !usuario.getActivo()) {
			throw new BadCredentialsException("Cuenta inactiva");
		}

		String rol = administradorRepository.existsById(id) ? "ROLE_ADMINISTRADOR" : "ROLE_INVESTIGADOR";
		List<GrantedAuthority> permisos = List.of(new SimpleGrantedAuthority(rol));

		// El tercer argumento es el nombre: así authentication.getName() es el idInstitucional.
		return new JwtAuthenticationToken(jwt, permisos, id);
	}

}
