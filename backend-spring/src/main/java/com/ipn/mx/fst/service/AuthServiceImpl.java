package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.dto.PerfilPeticion;
import com.ipn.mx.fst.repository.AdministradorRepository;
import com.ipn.mx.fst.repository.UsuarioRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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

	private static final int MINIMO_CONTRASENA = 8;
	private static final int MAXIMO_BYTES_CONTRASENA = 72;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorRepository administradorRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ReporteAlmacen reportes;

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

		// El token va primero y después los mismos datos de cuenta que devuelven los otros dos métodos.
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("token", token);
		datos.putAll(cuenta(usuario));
		return datos;
	}

	@Override
	@Transactional
	public Map<String, Object> cambiarContrasena(String id, String actual, String nueva) {
		Usuario usuario = usuarioRepository.findById(id)
				.orElseThrow(() -> new ErrorApi(HttpStatus.UNAUTHORIZED, "Sesión no válida o vencida"));

		if (!coincide(actual, usuario.getContrasenaHash())) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
		}
		// El mínimo de 8 caracteres es una decisión del equipo; la guía no fija un largo.
		if (nueva.length() < MINIMO_CONTRASENA) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST,
					"La contraseña nueva debe tener al menos " + MINIMO_CONTRASENA + " caracteres.");
		}
		// bcrypt solo toma en cuenta los primeros 72 bytes: más allá se rechaza para no aceptar una
		// contraseña que en realidad se guardaría recortada.
		if (nueva.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_CONTRASENA) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST,
					"La contraseña nueva no puede pasar de " + MAXIMO_BYTES_CONTRASENA + " bytes.");
		}
		// La actual ya se comprobó contra el hash, así que comparar los textos basta.
		if (nueva.equals(actual)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "La contraseña nueva debe ser distinta de la actual.");
		}

		usuario.setContrasenaHash(passwordEncoder.encode(nueva));
		// Ya no es una contraseña temporal: FiltroCambioPendiente deja de bloquear la sesión desde la
		// siguiente petición, porque ConvertidorJwt lee cambioRequerido de la base cada vez.
		usuario.setCambioRequerido(false);
		usuarioRepository.save(usuario);
		return cuenta(usuario);
	}

	@Override
	@Transactional
	public Map<String, Object> actualizarPerfil(String id, PerfilPeticion peticion) {
		Usuario usuario = usuarioRepository.findById(id)
				.orElseThrow(() -> new ErrorApi(HttpStatus.UNAUTHORIZED, "Sesión no válida o vencida"));

		// El correo se guarda en minúsculas y sin espacios, como se busca en el login.
		String correo = peticion.getCorreo().trim().toLowerCase();
		if (usuarioRepository.existsByCorreoIgnoreCaseAndIdInstitucionalNot(correo, id)) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
		}

		// El PDF de cada experimento de la cuenta muestra el nombre y los apellidos del creador (no el correo; ver
		// generarReporte). Si cambian, sus reportes guardados ya no los reflejan: se invalidan.
		String nombre = peticion.getNombre().trim();
		String apellidos = peticion.getApellidos().trim();
		if (!nombre.equals(usuario.getNombre()) || !apellidos.equals(usuario.getApellidos())) {
			invalidarReportesDeLaCuenta(id);
		}
		usuario.setNombre(nombre);
		usuario.setApellidos(apellidos);
		usuario.setCorreo(correo);
		try {
			usuarioRepository.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			// Otra cuenta tomó el correo justo entre la revisión de arriba y este UPDATE.
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
		}
		return cuenta(usuario);
	}

	// Bloquea TODOS los experimentos de la cuenta en orden de idexperimento (un solo SELECT ... FOR UPDATE con ORDER BY:
	// PostgreSQL bloquea en el orden del ORDER BY) y luego invalida cada reporte. Va después de los candados de cada
	// experimento y antes del UPDATE de usuario, igual que en el resto de las transacciones que invalidan.
	// Cuando exista PATCH /admin/users/{id} (§3), que cambie el nombre de una cuenta debe hacer lo mismo.
	private void invalidarReportesDeLaCuenta(String idInstitucional) {
		List<Integer> experimentos = jdbc.queryForList(
				"SELECT idexperimento FROM experimento WHERE idinstitucional = ? ORDER BY idexperimento FOR UPDATE",
				Integer.class, idInstitucional);
		for (Integer idExperimento : experimentos) {
			reportes.invalidar(idExperimento);
		}
	}

	// Los datos de la cuenta que ven el login, el cambio de contraseña y el de perfil. LinkedHashMap para
	// conservar el orden. Nunca se incluye la contraseña ni su hash.
	private Map<String, Object> cuenta(Usuario usuario) {
		boolean esAdministrador = administradorRepository.existsById(usuario.getIdInstitucional());

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("idInstitucional", usuario.getIdInstitucional());
		datos.put("nombre", usuario.getNombre());
		datos.put("apellidos", usuario.getApellidos());
		datos.put("correo", usuario.getCorreo());
		datos.put("activo", usuario.getActivo());
		datos.put("rol", esAdministrador ? "Administrador" : "Investigador");
		datos.put("cambioRequerido", usuario.getCambioRequerido());
		return datos;
	}

	// matches de bcrypt, sin fallar con una contraseña de más de 72 bytes: esa no puede ser la guardada.
	private boolean coincide(String contrasena, String hash) {
		return contrasena.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES_CONTRASENA
				&& passwordEncoder.matches(contrasena, hash);
	}

}
