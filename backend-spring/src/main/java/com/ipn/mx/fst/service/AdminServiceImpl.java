package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.domain.valores.TipoConducta;
import com.ipn.mx.fst.dto.CrearCuentaPeticion;
import com.ipn.mx.fst.dto.ModificarCuentaPeticion;
import com.ipn.mx.fst.repository.UsuarioRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
import com.ipn.mx.fst.utilerias.service.EmailService;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AdminServiceImpl implements AdminService {

	private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

	private static final String INVESTIGADOR = "Investigador";
	private static final String ADMINISTRADOR = "Administrador";

	private static final String ID_REPETIDO = "Ya existe una cuenta con ese identificador.";
	private static final String CORREO_REPETIDO = "Ya existe una cuenta con ese correo.";

	// Sin 0/O ni 1/l/I, que se confunden al copiarlas del correo. 12 caracteres de 54: unos 69 bits.
	private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
	private static final int LARGO_TEMPORAL = 12;
	private static final SecureRandom ALEATORIO = new SecureRandom();

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CandadosDeCuenta candados;

	@Autowired
	private ReglaAdministradores reglaAdministradores;

	@Autowired
	private ReporteAlmacen reportes;

	@Autowired
	private EmailService emailService;

	@Autowired
	private UsoDeDisco usoDeDisco;

	@Autowired
	private DireccionWeb direccionWeb;

	@Override
	@Transactional(readOnly = true)
	public List<Map<String, Object>> listar(String rol, String estado) {
		StringBuilder sql = new StringBuilder(
				"SELECT u.idinstitucional, u.nombre, u.apellidos, u.correo, u.activo, u.cambiorequerido, "
						+ "(a.idinstitucional IS NOT NULL) AS administrador, "
						+ "(SELECT COUNT(*) FROM experimento e WHERE e.idinstitucional = u.idinstitucional) AS nexperimentos "
						+ "FROM usuario u LEFT JOIN administrador a ON a.idinstitucional = u.idinstitucional WHERE 1 = 1");
		if (rol != null && !rol.isBlank()) {
			if (ADMINISTRADOR.equals(rol)) {
				sql.append(" AND a.idinstitucional IS NOT NULL");
			} else if (INVESTIGADOR.equals(rol)) {
				sql.append(" AND a.idinstitucional IS NULL");
			} else {
				throw new ErrorApi(HttpStatus.BAD_REQUEST, "El rol debe ser Investigador o Administrador.");
			}
		}
		if (estado != null && !estado.isBlank()) {
			if ("activa".equals(estado)) {
				sql.append(" AND u.activo");
			} else if ("inactiva".equals(estado)) {
				sql.append(" AND NOT u.activo");
			} else {
				throw new ErrorApi(HttpStatus.BAD_REQUEST, "El estado debe ser activa o inactiva.");
			}
		}
		sql.append(" ORDER BY u.apellidos, u.nombre, u.idinstitucional");

		return jdbc.query(sql.toString(), (rs, fila) -> {
			Map<String, Object> cuenta = new LinkedHashMap<>();
			cuenta.put("idInstitucional", rs.getString("idinstitucional"));
			cuenta.put("nombre", rs.getString("nombre"));
			cuenta.put("apellidos", rs.getString("apellidos"));
			cuenta.put("correo", rs.getString("correo"));
			cuenta.put("rol", rs.getBoolean("administrador") ? ADMINISTRADOR : INVESTIGADOR);
			cuenta.put("activo", rs.getBoolean("activo"));
			cuenta.put("cambioRequerido", rs.getBoolean("cambiorequerido"));
			cuenta.put("nExperimentos", rs.getLong("nexperimentos"));
			return cuenta;
		});
	}

	@Override
	@Transactional
	public Map<String, Object> crear(CrearCuentaPeticion peticion) {
		String id = peticion.getIdInstitucional().trim();
		String correo = peticion.getCorreo().trim().toLowerCase();
		boolean administrador = esAdministrador(peticion.getRol());

		if (usuarioRepository.existsById(id)) {
			throw new ErrorApi(HttpStatus.CONFLICT, ID_REPETIDO);
		}
		if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
			throw new ErrorApi(HttpStatus.CONFLICT, CORREO_REPETIDO);
		}

		String temporal = generarTemporal();
		Usuario usuario = new Usuario(id, peticion.getNombre().trim(), peticion.getApellidos().trim(), correo,
				passwordEncoder.encode(temporal), true, true);
		try {
			usuarioRepository.saveAndFlush(usuario);
			if (administrador) {
				jdbc.update("INSERT INTO administrador (idinstitucional) VALUES (?)", id);
			}
		} catch (DataIntegrityViolationException e) {
			// Otra petición tomó el identificador o el correo justo entre las revisiones de arriba y el INSERT. No se consulta
			// nada más: tras el error la transacción de PostgreSQL ya está abortada; la restricción rota dice cuál fue.
			boolean correoRepetido = String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_usuario_correo");
			throw new ErrorApi(HttpStatus.CONFLICT, correoRepetido ? CORREO_REPETIDO : ID_REPETIDO);
		}

		enviarTemporal(usuario, temporal, true);
		Map<String, Object> datos = cuenta(usuario, administrador);
		datos.put("passwordTemporal", temporal);
		return datos;
	}

	@Override
	@Transactional
	public Map<String, Object> modificar(String id, ModificarCuentaPeticion peticion) {
		if (peticion.getIdInstitucional() != null && !peticion.getIdInstitucional().equals(id)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El identificador institucional no se puede cambiar.");
		}
		Boolean activoNuevo = peticion.getActivo();
		String rolNuevo = peticion.getRol();
		boolean nuevoAdministrador = rolNuevo != null && esAdministrador(rolNuevo);

		// Orden de candados (ver CandadosDeCuenta): administradores, la cuenta y sus experimentos. Los administradores solo
		// se bloquean si este cambio puede quitar uno.
		if (rolNuevo != null || activoNuevo != null) {
			reglaAdministradores.bloquearAdministradores();
		}
		if (!candados.bloquearCuenta(id)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "La cuenta " + id + " no existe.");
		}
		Usuario usuario = usuarioRepository.findById(id)
				.orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "La cuenta " + id + " no existe."));

		boolean eraAdministrador = jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM administrador WHERE idinstitucional = ?)", Boolean.class, id);
		boolean seraAdministrador = rolNuevo == null ? eraAdministrador : nuevoAdministrador;
		boolean eraActivo = usuario.getActivo();
		boolean seraActivo = activoNuevo == null ? eraActivo : activoNuevo;

		// R-16
		reglaAdministradores.exigirAlMenosUno(id, eraAdministrador && eraActivo, seraAdministrador && seraActivo);

		String nombre = peticion.getNombre() == null ? usuario.getNombre() : peticion.getNombre().trim();
		String apellidos = peticion.getApellidos() == null ? usuario.getApellidos() : peticion.getApellidos().trim();
		String correo = peticion.getCorreo() == null ? usuario.getCorreo() : peticion.getCorreo().trim().toLowerCase();
		if (!correo.equalsIgnoreCase(usuario.getCorreo())
				&& usuarioRepository.existsByCorreoIgnoreCaseAndIdInstitucionalNot(correo, id)) {
			throw new ErrorApi(HttpStatus.CONFLICT, CORREO_REPETIDO);
		}

		// Igual que PATCH /users/me: el PDF de cada experimento de la cuenta muestra su nombre y apellidos, así que si cambian
		// se invalidan sus reportes guardados.
		if (!nombre.equals(usuario.getNombre()) || !apellidos.equals(usuario.getApellidos())) {
			reportes.invalidarDeLaCuenta(id);
		}

		usuario.setNombre(nombre);
		usuario.setApellidos(apellidos);
		usuario.setCorreo(correo);
		usuario.setActivo(seraActivo);
		try {
			usuarioRepository.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			throw new ErrorApi(HttpStatus.CONFLICT, CORREO_REPETIDO);
		}

		if (seraAdministrador && !eraAdministrador) {
			jdbc.update("INSERT INTO administrador (idinstitucional) VALUES (?)", id);
		} else if (!seraAdministrador && eraAdministrador) {
			jdbc.update("DELETE FROM administrador WHERE idinstitucional = ?", id);
		}
		return cuenta(usuario, seraAdministrador);
	}

	@Override
	@Transactional
	public Map<String, Object> nuevaTemporal(String id, String idSesion) {
		if (!candados.bloquearCuenta(id)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "La cuenta " + id + " no existe.");
		}
		Usuario usuario = usuarioRepository.findById(id)
				.orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "La cuenta " + id + " no existe."));
		// Con su propia cuenta su sesión quedaría en 401 y la temporal le llegaría por correo: para cambiar su contraseña tiene
		// PATCH /auth/password. Va después del 404 y antes del 409 de desactivada: no revela nada, el Administrador ya sabe su id.
		if (id.equals(idSesion)) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Para cambiar tu propia contraseña usa Mi perfil.");
		}
		if (!usuario.getActivo()) {
			throw new ErrorApi(HttpStatus.CONFLICT, "La cuenta está desactivada: reactívala antes de darle una contraseña temporal.");
		}

		// Cambiar el hash también deja sin efecto cualquier enlace de restablecer pendiente (llevan la huella del hash anterior).
		String temporal = generarTemporal();
		usuario.setContrasenaHash(passwordEncoder.encode(temporal));
		usuario.setCambioRequerido(true);
		usuarioRepository.saveAndFlush(usuario);
		// Auditoría: quién se la dio a quién. NUNCA se registra la temporal ni su hash.
		log.info("El Administrador {} le dio una contraseña temporal nueva a la cuenta {}", idSesion, id);

		enviarTemporal(usuario, temporal, false);
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("passwordTemporal", temporal);
		return datos;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> sistema() {
		List<String> conductas = new ArrayList<>();
		Arrays.stream(TipoConducta.values()).forEach(c -> conductas.add(c.valor()));

		// En orden de llegada: primero lo que se procesa y luego la cola por fecha de encolado.
		List<Map<String, Object>> cola = jdbc.query(
				"SELECT a.idanalisis, e.idexperimento, e.nombre AS experimento, g.etiqueta, t.ordinal, "
						+ "v.sesion::text AS sesion, a.estado::text AS estado "
						+ "FROM analisis a JOIN video v ON v.idvideo = a.idvideo JOIN tanda t ON t.idtanda = v.idtanda "
						+ "JOIN grupo g ON g.idgrupo = t.idgrupo JOIN experimento e ON e.idexperimento = g.idexperimento "
						+ "WHERE a.estado IN ('procesando', 'en cola') "
						+ "ORDER BY (a.estado = 'procesando') DESC, a.fechaanalisis, a.idanalisis",
				(rs, fila) -> {
					Map<String, Object> trabajo = new LinkedHashMap<>();
					trabajo.put("idAnalisis", rs.getInt("idanalisis"));
					trabajo.put("idExperimento", rs.getInt("idexperimento"));
					trabajo.put("experimento", rs.getString("experimento"));
					trabajo.put("grupo", rs.getString("etiqueta"));
					trabajo.put("letra", String.valueOf((char) ('A' + rs.getInt("ordinal") - 1)));
					trabajo.put("sesion", rs.getString("sesion"));
					trabajo.put("estado", rs.getString("estado"));
					return trabajo;
				});

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("disco", usoDeDisco.consultar());
		datos.put("conductas", conductas);
		datos.put("cola", cola);
		return datos;
	}

	// null o «Investigador» → false; «Administrador» → true; otro texto, 400.
	private boolean esAdministrador(String rol) {
		if (rol == null || INVESTIGADOR.equals(rol)) {
			return false;
		}
		if (ADMINISTRADOR.equals(rol)) {
			return true;
		}
		throw new ErrorApi(HttpStatus.BAD_REQUEST, "El rol debe ser Investigador o Administrador.");
	}

	// Contraseña temporal aleatoria (SecureRandom). Cumple las reglas de la contraseña: más de 8 caracteres y menos de 72 bytes.
	static String generarTemporal() {
		StringBuilder temporal = new StringBuilder(LARGO_TEMPORAL);
		for (int i = 0; i < LARGO_TEMPORAL; i++) {
			temporal.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
		}
		return temporal.toString();
	}

	// El correo sale cuando la transacción confirma: si se deshace, nadie recibe una contraseña que no existe. Un fallo del
	// correo no deshace nada (EmailServiceImpl lo registra y sigue; regla de la guía, sección 11).
	private void enviarTemporal(Usuario usuario, String temporal, boolean cuentaNueva) {
		String destinatario = usuario.getCorreo();
		String asunto = cuentaNueva ? "Tu cuenta del sistema FST" : "Nueva contraseña temporal del sistema FST";
		String texto = (cuentaNueva ? "Se creó tu cuenta del sistema FST.\n\n" : "Un Administrador restableció tu contraseña.\n\n")
				+ "Identificador: " + usuario.getIdInstitucional() + "\n"
				+ "Contraseña temporal: " + temporal + "\n\n"
				+ "Entra en " + direccionWeb.base() + " y el sistema te pedirá escribir una contraseña nueva. La temporal deja de servir en cuanto la cambies.";
		Runnable envio = () -> {
			try {
				emailService.enviarCorreo(destinatario, asunto, texto);
			} catch (RuntimeException e) {
				// Después del commit una excepción llegaría al cliente como 500 con la cuenta ya creada y su contraseña perdida.
				log.error("No se pudo enviar la contraseña temporal a {}: {}", destinatario, e.getMessage());
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					envio.run();
				}
			});
		} else {
			envio.run();
		}
	}

	// Los datos de la cuenta, con la forma del login (sin token) y sin la contraseña ni su hash.
	private Map<String, Object> cuenta(Usuario usuario, boolean administrador) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("idInstitucional", usuario.getIdInstitucional());
		datos.put("nombre", usuario.getNombre());
		datos.put("apellidos", usuario.getApellidos());
		datos.put("correo", usuario.getCorreo());
		datos.put("activo", usuario.getActivo());
		datos.put("rol", administrador ? ADMINISTRADOR : INVESTIGADOR);
		datos.put("cambioRequerido", usuario.getCambioRequerido());
		return datos;
	}

}
