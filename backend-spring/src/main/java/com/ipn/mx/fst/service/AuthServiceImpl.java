// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// La entidad de la cuenta y el DTO del perfil.
import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.dto.PerfilPeticion;
// Los dos repositorios que se usan.
import com.ipn.mx.fst.repository.AdministradorRepository;
import com.ipn.mx.fst.repository.UsuarioRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
import com.ipn.mx.fst.utilerias.service.EmailService;
// @PostConstruct: un método que Spring llama una vez, al terminar de construir el objeto.
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
// GeneralSecurityException: la excepción raíz de los fallos de criptografía de Java.
import java.security.GeneralSecurityException;
// MessageDigest: aquí solo por su método isEqual, que compara en tiempo constante (ver abajo).
import java.security.MessageDigest;
// Duration e Instant: un lapso y un instante exacto, para la vigencia de los tokens.
import java.time.Duration;
import java.time.Instant;
// HexFormat: pasar bytes a su representación hexadecimal.
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
// CompletableFuture: ejecutar algo en otro hilo (aquí, el envío del correo).
import java.util.concurrent.CompletableFuture;
// Mac: el cálculo de un HMAC (una huella con llave secreta).
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
// Las clases de JWT: validadores, algoritmo, el token leído, el codificador y sus parámetros.
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

// La implementación de AuthService, donde está el contrato completo de los cinco métodos.
//
// Es el archivo con más decisiones de SEGURIDAD del backend. Las cuatro que conviene llevarse:
//   1. el mismo mensaje para todos los fallos de login, y además el correo de recuperación se manda en
//      otro hilo para que ni el tiempo de respuesta delate si la cuenta existe;
//   2. el rol NO viaja en el token: se lee de la base en cada petición;
//   3. el enlace de restablecer sirve una sola vez SIN guardar ninguna tabla de enlaces usados;
//   4. las comparaciones de secretos se hacen en tiempo constante.
@Service
public class AuthServiceImpl implements AuthService {

	// R-01: el mismo mensaje en los tres casos, para no revelar si un correo está registrado.
	// Los tres son: el correo no existe, la cuenta está dada de baja y la contraseña no coincide.
	private static final String CREDENCIALES_INCORRECTAS = "Correo o contraseña incorrectos";

	private static final int MINIMO_CONTRASENA = 8;
	// 72 bytes es un límite del propio algoritmo bcrypt, no una decisión del equipo (ver validarContrasenaNueva).
	private static final int MAXIMO_BYTES_CONTRASENA = 72;

	private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
	// El valor del claim "tipo" de los tokens de enlace. Distinto de "sesion", que es el que exige
	// SeguridadConfig para entrar a la API: así un enlace de correo no sirve como sesión.
	private static final String TIPO_ENLACE = "reset";
	// Un solo mensaje para TODOS los motivos por los que un enlace no sirve, por la misma regla de no dar
	// información: venció, ya se usó, la cuenta está de baja o el token está manipulado.
	private static final String ENLACE_NO_VALIDO = "El enlace venció o ya se usó.";

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorRepository administradorRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	// La misma llave que usa SeguridadConfig: aquí hace falta además para calcular la huella del enlace.
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
	//
	// El detalle es real: Spring inyecta por tipo, y con dos JwtDecoder no sabría cuál darle al resource
	// server. Construirlo a mano aquí, sin @Bean, lo mantiene privado de esta clase.
	private JwtDecoder decodificadorDeEnlaces;

	// Se construye al terminar de crear el objeto, cuando la llave inyectada ya está puesta.
	@PostConstruct
	void prepararDecodificador() {
		// Es gemelo del de SeguridadConfig, con una sola diferencia: exige tipo = "reset" en vez de "sesion".
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(llaveJwt).macAlgorithm(MacAlgorithm.HS256).build();
		// createDefault() (firma y vigencia) se vuelve a incluir porque setJwtValidator REEMPLAZA la
		// validación por omisión. Sin él se dejaría de comprobar que el enlace no haya vencido.
		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefault(),
				new JwtClaimValidator<String>("tipo", TIPO_ENLACE::equals)));
		decodificadorDeEnlaces = decodificador;
	}

	@Override
	// readOnly: el login no escribe nada. No se registra el último acceso ni se cuentan los intentos.
	@Transactional(readOnly = true)
	public Map<String, Object> login(String correo, String contrasena) {
		Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
				// orElseThrow desempaqueta el Optional o lanza. La lambda construye la excepción solo si
				// de verdad hace falta.
				.orElseThrow(() -> new ErrorApi(HttpStatus.UNAUTHORIZED, CREDENCIALES_INCORRECTAS));

		// Las dos comprobaciones en un solo if, con el MISMO mensaje: "cuenta dada de baja" y "contraseña
		// incorrecta" son indistinguibles desde fuera, y así tiene que ser.
		// matches de bcrypt vuelve a aplicar el algoritmo con la sal guardada dentro del hash y compara;
		// nunca descifra nada, porque bcrypt es de una sola dirección.
		if (!usuario.getActivo() || !passwordEncoder.matches(contrasena, usuario.getContrasenaHash())) {
			throw new ErrorApi(HttpStatus.UNAUTHORIZED, CREDENCIALES_INCORRECTAS);
		}

		// El token solo dice quién es (subject) y de qué tipo es. El rol NO va dentro: se lee de la base en
		// cada petición (ConvertidorJwt), así que quitar o dar el rol de Administrador surte efecto al instante.
		//
		// Conviene recordar que el contenido de un JWT va codificado pero NO cifrado: cualquiera con el
		// token puede leerlo. Lo que no puede es cambiarlo sin invalidar la firma.
		Instant ahora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				// "sub" es el claim estándar de "de quién es este token".
				.subject(usuario.getIdInstitucional())
				// El claim propio que distingue los tres tipos de token del sistema: sesión, enlace y video.
				.claim("tipo", "sesion")
				.issuedAt(ahora)
				// 168 horas por omisión, es decir, 7 días (JWT_EXPIRES_HOURS).
				.expiresAt(ahora.plus(Duration.ofHours(horasDeSesion)))
				.build();
		JwsHeader encabezado = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(encabezado, claims)).getTokenValue();

		// El token va primero y después los mismos datos de cuenta que devuelven los otros dos métodos.
		// putAll reutiliza cuenta(): los tres métodos devuelven exactamente la misma forma.
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("token", token);
		datos.putAll(cuenta(usuario));
		return datos;
	}

	@Override
	@Transactional
	public Map<String, Object> cambiarContrasena(String id, String actual, String nueva, Boolean aceptaTerminos) {
		// Con el candado de la cuenta, un cambio de contraseña y un restablecer con enlace no se cruzan.
		// Es el segundo nivel del orden de candados que documenta CandadosDeCuenta.
		candados.bloquearCuenta(id);
		Usuario usuario = usuarioRepository.findById(id)
				// 401 y no 404: la cuenta salió del token, así que si no existe es que la sesión ya no vale.
				.orElseThrow(() -> new ErrorApi(HttpStatus.UNAUTHORIZED, "Sesión no válida o vencida"));
		// En el primer acceso (contraseña temporal) hay que aceptar el aviso de privacidad y los términos antes
		// de tocar nada. La aceptación no se guarda: solo se exige aquí. Un cambio normal ignora el campo.
		//
		// Boolean.TRUE.equals(...) y no == true: el campo es Boolean y puede ser null si no vino en el
		// cuerpo (ver CambioContrasenaPeticion). Escrito así, el null se trata como "no aceptó" sin tronar.
		if (usuario.getCambioRequerido() && !Boolean.TRUE.equals(aceptaTerminos)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST,
					"Para continuar debes aceptar el aviso de privacidad y los términos y condiciones de uso.");
		}

		// Se exige la contraseña actual aunque ya haya sesión abierta: así, quien encuentre una sesión
		// abierta no puede apoderarse de la cuenta sin conocerla.
		if (!coincide(actual, usuario.getContrasenaHash())) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
		}
		validarContrasenaNueva(nueva, HttpStatus.BAD_REQUEST);
		// La actual ya se comprobó contra el hash, así que comparar los textos basta.
		// (si "actual" coincide con el hash y "nueva" es igual a "actual", entonces "nueva" es la de
		// ahora: no hace falta volver a pasar por bcrypt, que es caro a propósito)
		if (nueva.equals(actual)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "La contraseña nueva debe ser distinta de la actual.");
		}

		usuario.setContrasenaHash(passwordEncoder.encode(nueva));
		// Ya no es una contraseña temporal: FiltroCambioPendiente deja de bloquear la sesión desde la
		// siguiente petición, porque ConvertidorJwt lee cambioRequerido de la base cada vez.
		// Por eso no hace falta devolver un token nuevo: el que ya tiene el usuario queda desbloqueado.
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
		// Normalizar al guardar evita tener dos cuentas que solo se diferencian en las mayúsculas.
		String correo = peticion.getCorreo().trim().toLowerCase();
		// El "Not" del nombre del método excluye la propia cuenta: guardar el correo que uno ya tiene no
		// es un conflicto (ver UsuarioRepository).
		if (usuarioRepository.existsByCorreoIgnoreCaseAndIdInstitucionalNot(correo, id)) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
		}

		// El PDF de cada experimento de la cuenta muestra el nombre y los apellidos del creador (no el correo; ver
		// generarReporte). Si cambian, sus reportes guardados ya no los reflejan: se invalidan.
		//
		// El paréntesis explica por qué el correo no dispara la invalidación: no sale impreso en el PDF.
		// Invalidar de más costaría regenerar todos los reportes de la cuenta sin motivo.
		String nombre = peticion.getNombre().trim();
		String apellidos = peticion.getApellidos().trim();
		if (!nombre.equals(usuario.getNombre()) || !apellidos.equals(usuario.getApellidos())) {
			reportes.invalidarDeLaCuenta(id);
		}
		usuario.setNombre(nombre);
		usuario.setApellidos(apellidos);
		usuario.setCorreo(correo);
		try {
			// saveAndFlush y no save: fuerza el UPDATE AHORA, dentro del try. Con save, Hibernate podría
			// retrasarlo hasta el final de la transacción, fuera del try, y la excepción no se atraparía.
			usuarioRepository.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			// Otra cuenta tomó el correo justo entre la revisión de arriba y este UPDATE.
			// Es la carrera que ninguna comprobación previa puede cerrar: la restricción UNIQUE de la base
			// es la última defensa, y aquí se traduce al mismo 409 que habría dado la comprobación.
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
		}
		return cuenta(usuario);
	}

	@Override
	@Transactional(readOnly = true)
	public void recuperar(String correo) {
		// La respuesta es la misma exista o no la cuenta (R-01): aquí no se avisa nada de si el correo se encontró, y el correo
		// sale en otro hilo para que ni el tiempo de respuesta lo delate.
		//
		// Esto último es una defensa contra un ATAQUE DE TIEMPO: mandar un correo tarda cientos de
		// milisegundos, así que si se hiciera en línea, una cuenta existente respondería notablemente más
		// despacio que una inexistente, y con eso se podría enumerar usuarios aunque el mensaje fuera igual.
		// La cadena filter + ifPresent dice "si existe Y está activa, haz esto"; si no, no pasa nada, sin
		// un solo if.
		usuarioRepository.findByCorreoIgnoreCase(correo.trim()).filter(Usuario::getActivo).ifPresent(usuario -> {
			// La dirección sale de DireccionWeb, no de la petición: el backend está detrás de nginx y no
			// puede fiarse de las cabeceras para saber desde dónde se abre la web.
			String enlace = direccionWeb.base() + "/restablecer?token=" + firmarEnlace(usuario);
			// Se copian a variables locales porque la lambda de abajo corre en otro hilo.
			String destinatario = usuario.getCorreo();
			String texto = "Recibimos una solicitud para restablecer tu contraseña del sistema FST.\n\n"
					+ "Abre este enlace para escribir una nueva (vale " + minutosDelEnlace + " minutos y solo se puede usar una vez):\n"
					+ enlace + "\n\n"
					// La última línea es importante: si alguien pide el enlace por error (o un tercero lo
					// pide con tu correo), el mensaje aclara que no pasa nada si se ignora.
					+ "Si no lo pediste, ignora este correo: tu contraseña no cambia.";
			// runAsync lanza esto en otro hilo y sigue de inmediato.
			CompletableFuture.runAsync(() -> {
				try {
					emailService.enviarCorreo(destinatario, "Restablecer tu contraseña", texto);
				} catch (RuntimeException e) {
					// Hay que atrapar aquí: una excepción en un hilo aparte se perdería sin dejar rastro.
					// EmailServiceImpl ya atrapa los fallos de correo; esto cubre cualquier otro.
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
			// decode comprueba a la vez la firma, la vigencia y el claim tipo = "reset".
			enlace = decodificadorDeEnlaces.decode(token);
		} catch (JwtException e) {
			// Cualquier motivo, el mismo mensaje.
			throw new ErrorApi(HttpStatus.BAD_REQUEST, ENLACE_NO_VALIDO);
		}
		String id = enlace.getSubject();
		String huella = enlace.getClaimAsString("huella");

		// Con la cuenta bloqueada, dos usos del mismo enlace al mismo tiempo no pasan los dos: el segundo espera, lee el hash que
		// dejó el primero y su huella ya no coincide.
		//
		// Es lo que convierte "sirve una sola vez" en una garantía de verdad y no en una carrera: sin el
		// candado, dos peticiones simultáneas leerían el mismo hash y las dos pasarían.
		if (id == null || huella == null || !candados.bloquearCuenta(id)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, ENLACE_NO_VALIDO);
		}
		Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.BAD_REQUEST, ENLACE_NO_VALIDO));
		// MessageDigest.isEqual compara en TIEMPO CONSTANTE: tarda lo mismo coincidan o no, y aunque
		// difieran en el primer carácter. Un equals normal corta en cuanto encuentra una diferencia, y
		// medir esa diferencia de tiempo permitiría ir adivinando la huella carácter a carácter.
		boolean huellaVigente = MessageDigest.isEqual(
				huellaDe(usuario.getContrasenaHash()).getBytes(StandardCharsets.UTF_8), huella.getBytes(StandardCharsets.UTF_8));
		if (!usuario.getActivo() || !huellaVigente) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, ENLACE_NO_VALIDO);
		}

		// 422 y no 400: la pantalla de restablecer toma cualquier 400 como «enlace vencido».
		// Es un buen ejemplo de código elegido por cómo lo interpreta el cliente: con 400, el usuario
		// vería "el enlace venció" cuando su enlace está bien y lo que falla es la contraseña escrita.
		validarContrasenaNueva(nueva, HttpStatus.UNPROCESSABLE_ENTITY);
		// Aquí sí hay que pasar por bcrypt: a diferencia de cambiarContrasena, no se recibió la actual en
		// claro para poder comparar los textos.
		if (coincide(nueva, usuario.getContrasenaHash())) {
			throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "La contraseña nueva debe ser distinta de la actual.");
		}
		usuario.setContrasenaHash(passwordEncoder.encode(nueva));
		// Al cambiar el hash, la huella de ESTE enlace (y la de cualquier otro pendiente) deja de cuadrar:
		// todos quedan inservibles sin guardar nada en ninguna tabla.
		usuario.setCambioRequerido(false);
		usuarioRepository.save(usuario);
	}

	// El enlace es un JWT (tipo "reset") con la cuenta y una huella del hash de la contraseña de ESE momento. No hay tabla de
	// enlaces usados: cuando la contraseña cambia (por este mismo enlace, por el cambio normal o por una temporal nueva), la huella
	// del token deja de coincidir y el enlace no sirve más.
	//
	// Es el truco más bonito del archivo: un solo uso sin estado. La alternativa habitual (una tabla de
	// tokens emitidos, con su columna de "usado") obligaría a crear la tabla, escribir en ella y
	// limpiarla cada cierto tiempo. Aquí la propia contraseña hace de contador.
	private String firmarEnlace(Usuario usuario) {
		Instant ahora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(usuario.getIdInstitucional())
				.claim("tipo", TIPO_ENLACE)
				.claim("huella", huellaDe(usuario.getContrasenaHash()))
				.issuedAt(ahora)
				// 60 minutos por omisión (RESET_LINK_MINUTES). Mucho menos que una sesión, porque el
				// enlace viaja por correo, que no es un canal seguro.
				.expiresAt(ahora.plus(Duration.ofMinutes(minutosDelEnlace)))
				.build();
		JwsHeader encabezado = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(encabezado, claims)).getTokenValue();
	}

	// HMAC-SHA256 del hash con la llave del JWT, y no el hash a secas: el token lo puede leer quien lo tenga y no debe dejar ver nada
	// del hash guardado.
	//
	// La precisión importa: el contenido de un JWT va codificado pero no cifrado, así que meter el hash
	// de bcrypt tal cual lo regalaría a cualquiera que viera el enlace. Un HMAC es una huella que depende
	// de una llave secreta: sin esa llave no se puede ni calcular ni revertir, pero el servidor sí puede
	// recalcularla y comparar, que es lo único que necesita.
	private String huellaDe(String contrasenaHash) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			// La misma llave con la que se firman los tokens; no hace falta un segundo secreto.
			mac.init(llaveJwt);
			// doFinal calcula la huella y formatHex la pasa a texto hexadecimal para que quepa en el claim.
			return HexFormat.of().formatHex(mac.doFinal(contrasenaHash.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			// No puede ocurrir: HmacSHA256 existe siempre en Java. Se convierte en una excepción no
			// comprobada para no contaminar la firma de todos los métodos de arriba con un throws.
			throw new IllegalStateException("No se pudo calcular la huella del enlace", e);
		}
	}

	// El mínimo de 8 caracteres es una decisión del equipo; la guía no fija un largo. bcrypt solo toma en cuenta los primeros 72
	// bytes: más allá se rechaza para no aceptar una contraseña que en realidad se guardaría recortada.
	//
	// El límite de 72 es un detalle poco conocido y real: bcrypt ignora en silencio lo que pase de ahí.
	// Sin esta comprobación, una contraseña de 100 caracteres se guardaría de hecho con los primeros 72, y
	// cualquiera que supiera esos 72 podría entrar. Rechazarla es más honesto que recortarla sin avisar.
	// BYTES y no caracteres: en UTF-8, una letra con acento ocupa dos bytes y un emoji hasta cuatro.
	// El estado se recibe como parámetro porque el mismo método sirve a dos rutas que necesitan 400 y 422.
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
	//
	// Que los tres métodos devuelvan exactamente esta forma permite al frontend refrescar su estado con
	// cualquiera de las tres respuestas, sin casos especiales.
	private Map<String, Object> cuenta(Usuario usuario) {
		// El rol se consulta aquí, no sale de ninguna columna de usuario: ser administrador es tener fila
		// en la tabla administrador (ver la entidad Administrador).
		boolean esAdministrador = administradorRepository.existsById(usuario.getIdInstitucional());

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("idInstitucional", usuario.getIdInstitucional());
		datos.put("nombre", usuario.getNombre());
		datos.put("apellidos", usuario.getApellidos());
		datos.put("correo", usuario.getCorreo());
		datos.put("activo", usuario.getActivo());
		// El texto que ve el usuario, no el "ROLE_ADMINISTRADOR" interno de Spring Security.
		datos.put("rol", esAdministrador ? "Administrador" : "Investigador");
		// Este campo es el que hace que el frontend lleve a la pantalla de primer acceso.
		datos.put("cambioRequerido", usuario.getCambioRequerido());
		return datos;
	}

	// matches de bcrypt, sin fallar con una contraseña de más de 72 bytes: esa no puede ser la guardada.
	//
	// El && corta por la izquierda: si pasa de 72 bytes, ni se llama a bcrypt. Además de evitar el
	// problema, ahorra el cálculo, que es lento a propósito.
	// La lógica es sólida: si ninguna contraseña de más de 72 bytes pudo guardarse (lo impide
	// validarContrasenaNueva), entonces una así nunca puede ser la correcta.
	private boolean coincide(String contrasena, String hash) {
		return contrasena.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES_CONTRASENA
				&& passwordEncoder.matches(contrasena, hash);
	}

}
