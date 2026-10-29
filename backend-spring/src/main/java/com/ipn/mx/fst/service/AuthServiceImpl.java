package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.dto.PerfilPeticion;
import com.ipn.mx.fst.repository.AdministradorRepository;
import com.ipn.mx.fst.repository.UsuarioRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
import com.ipn.mx.fst.utilerias.service.EmailService;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

	// R-01: el mismo mensaje en los tres casos, para no revelar si un correo está registrado.
	private static final String CREDENCIALES_INCORRECTAS = "Correo o contraseña incorrectos";

	private static final int MINIMO_CONTRASENA = 8;
	private static final int MAXIMO_BYTES_CONTRASENA = 72;

	private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
	private static final String TIPO_ENLACE = "reset";
	private static final String ENLACE_NO_VALIDO = "El enlace venció o ya se usó.";

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorRepository administradorRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private SecretKey llaveJwt;

	@Autowired
	private ReporteAlmacen reportes;

	@Autowired
	private CandadosDeCuenta candados;

	@Autowired
	private EmailService emailService;

	@Value("${app.jwt.horas}")
	private long horasDeSesion;

	@Value("${app.reset.minutos}")
	private long minutosDelEnlace;

	@Autowired
	private DireccionWeb direccionWeb;

	// Lee los enlaces de restablecer: firma y vigencia, y tipo = "reset". No es un bean: SeguridadConfig ya tiene el
	// JwtDecoder de la sesión y un segundo bean del mismo tipo rompería la configuración del resource server.
	private JwtDecoder decodificadorDeEnlaces;

	@PostConstruct
	void prepararDecodificador() {
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(llaveJwt).macAlgorithm(MacAlgorithm.HS256).build();
		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefault(),
				new JwtClaimValidator<String>("tipo", TIPO_ENLACE::equals)));
		decodificadorDeEnlaces = decodificador;
	}

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
		// Con el candado de la cuenta, un cambio de contraseña y un restablecer con enlace no se cruzan.
		candados.bloquearCuenta(id);
		Usuario usuario = usuarioRepository.findById(id)
				.orElseThrow(() -> new ErrorApi(HttpStatus.UNAUTHORIZED, "Sesión no válida o vencida"));

		if (!coincide(actual, usuario.getContrasenaHash())) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
		}
		validarContrasenaNueva(nueva, HttpStatus.BAD_REQUEST);
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
		// Primero el candado de la cuenta (FOR NO KEY UPDATE, ver CandadosDeCuenta) y después leerla: así «¿cambió el nombre?» se
		// decide con lo último que confirmó otro cambio de la misma cuenta, y no con una lectura vieja.
		if (!candados.bloquearCuenta(id)) {
			throw new ErrorApi(HttpStatus.UNAUTHORIZED, "Sesión no válida o vencida");
		}
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
			reportes.invalidarDeLaCuenta(id);
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

	@Override
	@Transactional(readOnly = true)
	public void recuperar(String correo) {
		// La respuesta es la misma exista o no la cuenta (R-01): aquí no se avisa nada de si el correo se encontró, y el correo
		// sale en otro hilo para que ni el tiempo de respuesta lo delate.
		usuarioRepository.findByCorreoIgnoreCase(correo.trim()).filter(Usuario::getActivo).ifPresent(usuario -> {
			String enlace = direccionWeb.base() + "/restablecer?token=" + firmarEnlace(usuario);
			String destinatario = usuario.getCorreo();
			String texto = "Recibimos una solicitud para restablecer tu contraseña del sistema FST.\n\n"
					+ "Abre este enlace para escribir una nueva (vale " + minutosDelEnlace + " minutos y solo se puede usar una vez):\n"
					+ enlace + "\n\n"
					+ "Si no lo pediste, ignora este correo: tu contraseña no cambia.";
			CompletableFuture.runAsync(() -> {
				try {
					emailService.enviarCorreo(destinatario, "Restablecer tu contraseña", texto);
				} catch (RuntimeException e) {
					log.error("No se pudo enviar el enlace de restablecer: {}", e.getMessage());
				}
			});
		});
	}

	@Override
	@Transactional
	public void restablecer(String token, String nueva) {
		Jwt enlace;
		try {
			enlace = decodificadorDeEnlaces.decode(token);
		} catch (JwtException e) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, ENLACE_NO_VALIDO);
		}
		String id = enlace.getSubject();
		String huella = enlace.getClaimAsString("huella");

		// Con la cuenta bloqueada, dos usos del mismo enlace al mismo tiempo no pasan los dos: el segundo espera, lee el hash que
		// dejó el primero y su huella ya no coincide.
		if (id == null || huella == null || !candados.bloquearCuenta(id)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, ENLACE_NO_VALIDO);
		}
		Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.BAD_REQUEST, ENLACE_NO_VALIDO));
		boolean huellaVigente = MessageDigest.isEqual(
				huellaDe(usuario.getContrasenaHash()).getBytes(StandardCharsets.UTF_8), huella.getBytes(StandardCharsets.UTF_8));
		if (!usuario.getActivo() || !huellaVigente) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, ENLACE_NO_VALIDO);
		}

		// 422 y no 400: la pantalla de restablecer toma cualquier 400 como «enlace vencido».
		validarContrasenaNueva(nueva, HttpStatus.UNPROCESSABLE_ENTITY);
		if (coincide(nueva, usuario.getContrasenaHash())) {
			throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "La contraseña nueva debe ser distinta de la actual.");
		}
		usuario.setContrasenaHash(passwordEncoder.encode(nueva));
		usuario.setCambioRequerido(false);
		usuarioRepository.save(usuario);
	}

	// El enlace es un JWT (tipo "reset") con la cuenta y una huella del hash de la contraseña de ESE momento. No hay tabla de
	// enlaces usados: cuando la contraseña cambia (por este mismo enlace, por el cambio normal o por una temporal nueva), la huella
	// del token deja de coincidir y el enlace no sirve más.
	private String firmarEnlace(Usuario usuario) {
		Instant ahora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(usuario.getIdInstitucional())
				.claim("tipo", TIPO_ENLACE)
				.claim("huella", huellaDe(usuario.getContrasenaHash()))
				.issuedAt(ahora)
				.expiresAt(ahora.plus(Duration.ofMinutes(minutosDelEnlace)))
				.build();
		JwsHeader encabezado = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(encabezado, claims)).getTokenValue();
	}

	// HMAC-SHA256 del hash con la llave del JWT, y no el hash a secas: el token lo puede leer quien lo tenga y no debe dejar ver nada
	// del hash guardado.
	private String huellaDe(String contrasenaHash) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(llaveJwt);
			return HexFormat.of().formatHex(mac.doFinal(contrasenaHash.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo calcular la huella del enlace", e);
		}
	}

	// El mínimo de 8 caracteres es una decisión del equipo; la guía no fija un largo. bcrypt solo toma en cuenta los primeros 72
	// bytes: más allá se rechaza para no aceptar una contraseña que en realidad se guardaría recortada.
	private void validarContrasenaNueva(String nueva, HttpStatus estado) {
		if (nueva.length() < MINIMO_CONTRASENA) {
			throw new ErrorApi(estado, "La contraseña nueva debe tener al menos " + MINIMO_CONTRASENA + " caracteres.");
		}
		if (nueva.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_CONTRASENA) {
			throw new ErrorApi(estado, "La contraseña nueva no puede pasar de " + MAXIMO_BYTES_CONTRASENA + " bytes.");
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
