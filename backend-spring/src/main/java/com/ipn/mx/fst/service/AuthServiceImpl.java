package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.repository.AdministradorRepository;
import com.ipn.mx.fst.repository.UsuarioRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

	// R-01: el mismo mensaje en los tres casos, para no revelar si un correo está registrado.
	private static final String CREDENCIALES_INCORRECTAS = "Correo o contraseña incorrectos";

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorRepository administradorRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Value("${app.jwt.horas}")
	private long horasDeSesion;

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> login(String correo, String contrasena) {
		Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
				.orElseThrow(() -> new ErrorApi(HttpStatus.UNAUTHORIZED, CREDENCIALES_INCORRECTAS));

		if (!usuario.getActivo() || !passwordEncoder.matches(contrasena, usuario.getContrasenaHash())) {
			throw new ErrorApi(HttpStatus.UNAUTHORIZED, CREDENCIALES_INCORRECTAS);
		}

		// El token solo dice quién es (subject) y de qué tipo es. El rol NO va dentro: se lee de la base en
		// cada petición (ConvertidorJwt), así que quitar o dar el rol de Administrador surte efecto al instante.
		Instant ahora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(usuario.getIdInstitucional())
				.claim("tipo", "sesion")
				.issuedAt(ahora)
				.expiresAt(ahora.plus(Duration.ofHours(horasDeSesion)))
				.build();
		JwsHeader encabezado = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(encabezado, claims)).getTokenValue();

		boolean esAdministrador = administradorRepository.existsById(usuario.getIdInstitucional());

		// LinkedHashMap para conservar el orden. Nunca se incluye la contraseña ni su hash.
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("token", token);
		datos.put("idInstitucional", usuario.getIdInstitucional());
		datos.put("nombre", usuario.getNombre());
		datos.put("apellidos", usuario.getApellidos());
		datos.put("correo", usuario.getCorreo());
		datos.put("rol", esAdministrador ? "Administrador" : "Investigador");
		datos.put("cambioRequerido", usuario.getCambioRequerido());
		return datos;
	}

}
