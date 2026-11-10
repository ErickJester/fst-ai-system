// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.domain.valores.TipoConducta;
import com.ipn.mx.fst.dto.CrearCuentaPeticion;
import com.ipn.mx.fst.dto.ModificarCuentaPeticion;
import com.ipn.mx.fst.repository.UsuarioRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
import com.ipn.mx.fst.utilerias.service.EmailService;
// SecureRandom: generador de números aleatorios APTO PARA CRIPTOGRAFÍA (ver abajo).
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
// Las dos clases que permiten ejecutar algo DESPUÉS de que la transacción confirme.
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// La implementación de AdminService, donde está el contrato completo de los cinco métodos.
//
// Recuerda que aquí NO se comprueba el rol de quien llama: eso lo hace SeguridadConfig, que reserva
// /admin/** a ROLE_ADMINISTRADOR. Los idSesion que reciben dos métodos son para otra cosa: impedir que un
// administrador se aplique a sí mismo acciones que lo dejarían fuera.
// Es también el archivo donde más se ve el orden de candados de CandadosDeCuenta, porque modificar() toca
// los tres niveles: administradores, la cuenta y sus experimentos.
@Service
public class AdminServiceImpl implements AdminService {

	private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

	// Los dos roles, como textos que ve el usuario. En constantes para no escribirlos sueltos.
	private static final String INVESTIGADOR = "Investigador";
	private static final String ADMINISTRADOR = "Administrador";

	// Los dos mensajes de conflicto, también en constantes: se usan desde varios sitios y tienen que ser
	// idénticos (las pruebas los comprueban).
	private static final String ID_REPETIDO = "Ya existe una cuenta con ese identificador.";
	private static final String CORREO_REPETIDO = "Ya existe una cuenta con ese correo.";

	// Sin 0/O ni 1/l/I, que se confunden al copiarlas del correo. 12 caracteres de 54: unos 69 bits.
	//
	// Las dos decisiones son buenas y vale la pena verlas:
	//   quitar los caracteres ambiguos evita la llamada de "no me funciona la contraseña" porque alguien
	//   leyó un 1 donde había una l minúscula;
	//   69 bits de entropía es el tamaño del espacio de búsqueda: 54 elevado a 12. Adivinarla por fuerza
	//   bruta es inviable, y además la temporal se cambia en el primer acceso.
	private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
	private static final int LARGO_TEMPORAL = 12;
	// SecureRandom y no Random: el Random normal es predecible (quien vea unos cuantos valores puede
	// calcular los siguientes), y aquí se están generando contraseñas.
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
		// El SQL se arma por partes según los filtros. Fíjate en que SOLO se concatena texto fijo escrito
		// aquí: ningún valor del usuario entra en la consulta, así que no hay inyección posible.
		StringBuilder sql = new StringBuilder(
				"SELECT u.idinstitucional, u.nombre, u.apellidos, u.correo, u.activo, u.cambiorequerido, "
						// El rol se calcula: tener fila en administrador (ver la entidad Administrador).
						+ "(a.idinstitucional IS NOT NULL) AS administrador, "
						// Una subconsulta para contar los experimentos de cada cuenta, en la misma pasada.
						+ "(SELECT COUNT(*) FROM experimento e WHERE e.idinstitucional = u.idinstitucional) AS nexperimentos "
						// LEFT JOIN: los investigadores no tienen fila en administrador y aun así salen.
						// "WHERE 1 = 1" es un truco para poder ir pegando " AND ..." sin comprobar si es
						// la primera condición.
						+ "FROM usuario u LEFT JOIN administrador a ON a.idinstitucional = u.idinstitucional WHERE 1 = 1");
		if (rol != null && !rol.isBlank()) {
			if (ADMINISTRADOR.equals(rol)) {
				sql.append(" AND a.idinstitucional IS NOT NULL");
			} else if (INVESTIGADOR.equals(rol)) {
				sql.append(" AND a.idinstitucional IS NULL");
			} else {
				// Un valor desconocido se rechaza en vez de ignorarse: un filtro mal escrito que
				// devolviera la lista entera en silencio sería peor.
				throw new ErrorApi(HttpStatus.BAD_REQUEST, "El rol debe ser Investigador o Administrador.");
			}
		}
		if (estado != null && !estado.isBlank()) {
			if ("activa".equals(estado)) {
				// La columna es booleana, así que no hace falta "= true".
				sql.append(" AND u.activo");
			} else if ("inactiva".equals(estado)) {
				sql.append(" AND NOT u.activo");
			} else {
				throw new ErrorApi(HttpStatus.BAD_REQUEST, "El estado debe ser activa o inactiva.");
			}
		}
		// Por apellidos y nombre, que es como se busca a una persona en una lista. El id al final
		// desempata a dos homónimos y hace que el orden sea siempre el mismo.
		sql.append(" ORDER BY u.apellidos, u.nombre, u.idinstitucional");

		// La lambda convierte cada fila en el mapa que espera la pantalla.
		return jdbc.query(sql.toString(), (rs, fila) -> {
			Map<String, Object> cuenta = new LinkedHashMap<>();
			cuenta.put("idInstitucional", rs.getString("idinstitucional"));
			cuenta.put("nombre", rs.getString("nombre"));
			cuenta.put("apellidos", rs.getString("apellidos"));
			cuenta.put("correo", rs.getString("correo"));
			cuenta.put("rol", rs.getBoolean("administrador") ? ADMINISTRADOR : INVESTIGADOR);
			cuenta.put("activo", rs.getBoolean("activo"));
			cuenta.put("cambioRequerido", rs.getBoolean("cambiorequerido"));
			// El conteo sirve para avisar antes de desactivar a alguien con trabajo a su nombre.
			cuenta.put("nExperimentos", rs.getLong("nexperimentos"));
			return cuenta;
		});
	}

	@Override
	@Transactional
	public Map<String, Object> crear(CrearCuentaPeticion peticion) {
		// El DTO ya recortó los espacios del identificador en su setter (ver CrearCuentaPeticion).
		String id = peticion.getIdInstitucional();
		// El correo se normaliza a minúsculas, como en actualizarPerfil y como se busca en el login.
		String correo = peticion.getCorreo().trim().toLowerCase();
		// Toda alta nace como Investigador: el ascenso a Administrador es solo por PATCH /admin/users/{id}.
		// Es la regla de mínimo privilegio. La variable existe (en vez de escribir false suelto) para que
		// el código de abajo se lea igual si algún día se permitiera elegir el rol al crear.
		boolean administrador = false;

		// Las dos comprobaciones previas dan mensajes claros; la carrera la cubre el catch de abajo.
		if (usuarioRepository.existsById(id)) {
			throw new ErrorApi(HttpStatus.CONFLICT, ID_REPETIDO);
		}
		if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
			throw new ErrorApi(HttpStatus.CONFLICT, CORREO_REPETIDO);
		}

		String temporal = generarTemporal();
		// El objeto se arma para poder reutilizar cuenta() al final; la escritura va por SQL (ver abajo).
		// Los dos true finales son activo y cambioRequerido.
		Usuario usuario = new Usuario(id, peticion.getNombre().trim(), peticion.getApellidos().trim(), correo,
				passwordEncoder.encode(temporal), true, true);
		try {
			// INSERT de verdad y no save(): con el identificador ya puesto, save() hace un merge (SELECT y luego INSERT o UPDATE), y
			// si otra petición con el mismo identificador acababa de confirmar, el segundo merge actualizaba esa cuenta (cambiaba su
			// contraseña) y respondía 201 en lugar de 409. Con INSERT la llave primaria decide: el segundo falla y cae en el catch.
			//
			// Es un fallo real y sutil de JPA que conviene entender: save() no significa "inserta",
			// significa "guarda". Con una llave que pone la aplicación (aquí la boleta, no un IDENTITY),
			// JPA no puede saber si la entidad es nueva, así que consulta primero. Y entre esa consulta y
			// la escritura cabe otra petición. El resultado era peor que un error: se le cambiaba la
			// contraseña a una cuenta ajena y se respondía como si todo hubiera ido bien.
			jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
					+ "VALUES (?, ?, ?, ?, ?, true, true)", id, usuario.getNombre(), usuario.getApellidos(), correo,
					usuario.getContrasenaHash());
			if (administrador) {
				jdbc.update("INSERT INTO administrador (idinstitucional) VALUES (?)", id);
			}
		} catch (DataIntegrityViolationException e) {
			// Otra petición tomó el identificador o el correo justo entre las revisiones de arriba y el INSERT. No se consulta
			// nada más: tras el error la transacción de PostgreSQL ya está abortada; la restricción rota dice cuál fue.
			//
			// La aclaración importa: después de un error, PostgreSQL no acepta ninguna consulta más en esa
			// transacción. Por eso no se puede volver a preguntar cuál de los dos chocó, y hay que
			// deducirlo del nombre de la restricción que viene en el mensaje.
			boolean correoRepetido = String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_usuario_correo");
			throw new ErrorApi(HttpStatus.CONFLICT, correoRepetido ? CORREO_REPETIDO : ID_REPETIDO);
		}

		enviarTemporal(usuario, temporal, true);
		Map<String, Object> datos = cuenta(usuario, administrador);
		// La temporal viaja UNA vez, aquí. Después solo queda su hash de bcrypt, irreversible.
		datos.put("passwordTemporal", temporal);
		return datos;
	}

	@Override
	@Transactional
	public Map<String, Object> modificar(String id, ModificarCuentaPeticion peticion, String idSesion) {
		// El identificador se admite en el cuerpo por comodidad, pero solo si coincide con el de la URL:
		// cambiarlo sería cambiar la llave primaria, de la que cuelgan los experimentos.
		if (peticion.getIdInstitucional() != null && !peticion.getIdInstitucional().equals(id)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El identificador institucional no se puede cambiar.");
		}
		Boolean activoNuevo = peticion.getActivo();
		String rolNuevo = peticion.getRol();
		// esAdministrador lanza 400 si el texto no es ninguno de los dos válidos.
		boolean nuevoAdministrador = rolNuevo != null && esAdministrador(rolNuevo);

		// Orden de candados (ver CandadosDeCuenta): administradores, la cuenta y sus experimentos. Los administradores solo
		// se bloquean si este cambio puede quitar uno.
		//
		// La condición ahorra el candado más caro (bloquea TODA la tabla administrador) en el caso
		// corriente de solo corregir un apellido.
		if (rolNuevo != null || activoNuevo != null) {
			reglaAdministradores.bloquearAdministradores();
		}
		// Segundo nivel: la fila de la cuenta. false significa que no existe.
		if (!candados.bloquearCuenta(id)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "La cuenta " + id + " no existe.");
		}
		// La lectura va DESPUÉS del candado: así ve lo último que confirmó otro cambio de esta cuenta.
		Usuario usuario = usuarioRepository.findById(id)
				.orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "La cuenta " + id + " no existe."));

		// Los cuatro valores que describen el ANTES y el DESPUÉS. Se calculan juntos porque las dos reglas
		// siguientes comparan uno con otro.
		boolean eraAdministrador = jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM administrador WHERE idinstitucional = ?)", Boolean.class, id);
		// Si el campo no vino en el PATCH, el valor no cambia: ese es el sentido del null en un PATCH.
		boolean seraAdministrador = rolNuevo == null ? eraAdministrador : nuevoAdministrador;
		boolean eraActivo = usuario.getActivo();
		boolean seraActivo = activoNuevo == null ? eraActivo : activoNuevo;

		// Como reset-temporal: un Administrador no se desactiva ni se quita el rol a sí mismo (su sesión moriría en la siguiente
		// petición). Cambiar su propio nombre, apellidos o correo sí se permite: la sesión sigue igual.
		//
		// "Su sesión moriría en la siguiente petición" es literal: ConvertidorJwt lee el rol y el estado
		// de la base en CADA petición, así que el efecto sería inmediato y se quedaría fuera sin querer.
		boolean quitaSuPropioAcceso = id.equals(idSesion) && eraAdministrador && eraActivo && (!seraAdministrador || !seraActivo);
		if (quitaSuPropioAcceso) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Para cambiar tu propia cuenta usa Mi perfil.");
		}

		// R-16
		// La otra protección: que no se quede el sistema sin ningún administrador activo. Se le pasa el
		// antes y el después ya combinados (ser administrador Y estar activo), que es lo que cuenta.
		reglaAdministradores.exigirAlMenosUno(id, eraAdministrador && eraActivo, seraAdministrador && seraActivo);

		// El patrón del PATCH: lo que no vino se queda como estaba.
		String nombre = peticion.getNombre() == null ? usuario.getNombre() : peticion.getNombre().trim();
		String apellidos = peticion.getApellidos() == null ? usuario.getApellidos() : peticion.getApellidos().trim();
		String correo = peticion.getCorreo() == null ? usuario.getCorreo() : peticion.getCorreo().trim().toLowerCase();
		// Solo se comprueba si el correo DE VERDAD cambió: así, un PATCH que lo reenvía igual no gasta
		// una consulta ni puede dar un falso conflicto.
		if (!correo.equalsIgnoreCase(usuario.getCorreo())
				&& usuarioRepository.existsByCorreoIgnoreCaseAndIdInstitucionalNot(correo, id)) {
			throw new ErrorApi(HttpStatus.CONFLICT, CORREO_REPETIDO);
		}

		// Igual que PATCH /users/me: el PDF de cada experimento de la cuenta muestra su nombre y apellidos, así que si cambian
		// se invalidan sus reportes guardados.
		// Es el tercer nivel del orden de candados, y va antes del UPDATE de usuario.
		if (!nombre.equals(usuario.getNombre()) || !apellidos.equals(usuario.getApellidos())) {
			reportes.invalidarDeLaCuenta(id);
		}

		usuario.setNombre(nombre);
		usuario.setApellidos(apellidos);
		usuario.setCorreo(correo);
		usuario.setActivo(seraActivo);
		try {
			// saveAndFlush fuerza el UPDATE aquí, dentro del try, para poder atrapar el choque.
			usuarioRepository.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			// La carrera que ninguna comprobación previa puede cerrar: la restricción UNIQUE es la última
			// defensa y aquí se traduce al mismo 409.
			throw new ErrorApi(HttpStatus.CONFLICT, CORREO_REPETIDO);
		}

		// El cambio de rol es crear o borrar la fila de administrador. Solo se toca si de verdad cambió:
		// insertar una que ya está daría error y borrar una que no está no haría nada.
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
		//
		// El orden de las comprobaciones es observable desde fuera y por eso está documentado y probado.
		// La aclaración del final explica por qué aquí no importa: el administrador conoce su propio id,
		// así que saber que existe no le dice nada nuevo.
		// La regla de fondo es la misma que en AdminService: por esta vía no se exige la contraseña
		// actual, y PATCH /auth/password sí.
		if (id.equals(idSesion)) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Para cambiar tu propia contraseña usa Mi perfil.");
		}
		if (!usuario.getActivo()) {
			throw new ErrorApi(HttpStatus.CONFLICT, "La cuenta está desactivada: reactívala antes de darle una contraseña temporal.");
		}

		// Cambiar el hash también deja sin efecto cualquier enlace de restablecer pendiente (llevan la huella del hash anterior).
		// Es el mecanismo de AuthServiceImpl.firmarEnlace: la huella deja de cuadrar y el enlace muere solo.
		String temporal = generarTemporal();
		usuario.setContrasenaHash(passwordEncoder.encode(temporal));
		// Vuelve a dejar la cuenta en "primer acceso": ConvertidorJwt pondrá la marca CAMBIO_PENDIENTE y
		// FiltroCambioPendiente bloqueará todo salvo cambiar la contraseña.
		usuario.setCambioRequerido(true);
		usuarioRepository.saveAndFlush(usuario);
		// Auditoría: quién se la dio a quién. NUNCA se registra la temporal ni su hash.
		// Un secreto escrito en el log queda en los archivos del servidor y en cualquier recolector
		// de registros, mucho después de haber dejado de servir.
		log.info("El Administrador {} le dio una contraseña temporal nueva a la cuenta {}", idSesion, id);

		enviarTemporal(usuario, temporal, false);
		Map<String, Object> datos = new LinkedHashMap<>();
		// Solo la temporal: no se devuelven los datos de la cuenta, que el administrador ya tiene.
		datos.put("passwordTemporal", temporal);
		return datos;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> sistema() {
		// La lista de conductas sale del enum: la pantalla la usa para armar sus menús sin escribirlas.
		List<String> conductas = new ArrayList<>();
		Arrays.stream(TipoConducta.values()).forEach(c -> conductas.add(c.valor()));

		// En orden de llegada: primero lo que se procesa y luego la cola por fecha de encolado.
		List<Map<String, Object>> cola = jdbc.query(
				"SELECT a.idanalisis, e.idexperimento, e.nombre AS experimento, g.etiqueta, t.ordinal, "
						// ::text convierte los dominios de PostgreSQL a texto para poder leerlos con getString.
						+ "v.sesion::text AS sesion, a.estado::text AS estado "
						+ "FROM analisis a JOIN video v ON v.idvideo = a.idvideo JOIN tanda t ON t.idtanda = v.idtanda "
						+ "JOIN grupo g ON g.idgrupo = t.idgrupo JOIN experimento e ON e.idexperimento = g.idexperimento "
						// Solo lo pendiente: lo completado, con error o cancelado no está en la cola.
						+ "WHERE a.estado IN ('procesando', 'en cola') "
						// El primer criterio sube lo que se está procesando: en PostgreSQL un booleano
						// ordena false antes que true, así que DESC pone los true (procesando) arriba.
						// Después, por fecha de encolado, que es el orden en que el worker los tomará.
						// El id desempata dos encolados en el mismo instante.
						+ "ORDER BY (a.estado = 'procesando') DESC, a.fechaanalisis, a.idanalisis",
				(rs, fila) -> {
					Map<String, Object> trabajo = new LinkedHashMap<>();
					trabajo.put("idAnalisis", rs.getInt("idanalisis"));
					trabajo.put("idExperimento", rs.getInt("idexperimento"));
					trabajo.put("experimento", rs.getString("experimento"));
					trabajo.put("grupo", rs.getString("etiqueta"));
					// El ordinal se traduce a letra, como en todas las pantallas.
					trabajo.put("letra", String.valueOf((char) ('A' + rs.getInt("ordinal") - 1)));
					trabajo.put("sesion", rs.getString("sesion"));
					trabajo.put("estado", rs.getString("estado"));
					return trabajo;
				});

		Map<String, Object> datos = new LinkedHashMap<>();
		// El disco viene de la caché de un minuto de UsoDeDisco: esta pantalla se refresca cada pocos
		// segundos y recorrer el volumen cada vez sería carísimo.
		datos.put("disco", usoDeDisco.consultar());
		datos.put("conductas", conductas);
		datos.put("cola", cola);
		return datos;
	}

	// null o «Investigador» → false; «Administrador» → true; otro texto, 400.
	// Un texto desconocido se rechaza en vez de tratarse como investigador: "Admin" o "administrador" no
	// deben degradar a alguien en silencio.
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
	//
	// Los dos límites son los de AuthServiceImpl: 12 caracteres pasan el mínimo de 8, y al ser todos
	// ASCII ocupan 12 bytes, muy por debajo del tope de 72 de bcrypt.
	// static y sin private para que la prueba pueda llamarlo sin levantar el contexto de Spring.
	static String generarTemporal() {
		StringBuilder temporal = new StringBuilder(LARGO_TEMPORAL);
		for (int i = 0; i < LARGO_TEMPORAL; i++) {
			// nextInt(n) da un número de 0 a n-1, que es justo un índice válido del alfabeto.
			temporal.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
		}
		return temporal.toString();
	}

	// El correo sale cuando la transacción confirma: si se deshace, nadie recibe una contraseña que no existe. Un fallo del
	// correo no deshace nada (EmailServiceImpl lo registra y sigue; regla de la guía, sección 11).
	//
	// Es el mismo mecanismo que usa ReporteAlmacen para borrar carpetas, y por la misma razón: un correo
	// enviado no se puede retirar. Mandarlo antes del commit podría dejar al usuario con una contraseña
	// de una cuenta que al final no se creó.
	private void enviarTemporal(Usuario usuario, String temporal, boolean cuentaNueva) {
		String destinatario = usuario.getCorreo();
		// El mismo método sirve para los dos casos; el booleano cambia el asunto y la primera frase.
		String asunto = cuentaNueva ? "Tu cuenta del sistema FST" : "Nueva contraseña temporal del sistema FST";
		String texto = (cuentaNueva ? "Se creó tu cuenta del sistema FST.\n\n" : "Un Administrador restableció tu contraseña.\n\n")
				+ "Identificador: " + usuario.getIdInstitucional() + "\n"
				+ "Contraseña temporal: " + temporal + "\n\n"
				// La última frase explica qué va a pasar: el sistema le pedirá cambiarla, y no es un error.
				+ "Entra en " + direccionWeb.base() + " y el sistema te pedirá escribir una contraseña nueva. La temporal deja de servir en cuanto la cambies.";
		// Se guarda el envío en un Runnable para no repetirlo en las dos ramas de abajo.
		Runnable envio = () -> {
			try {
				emailService.enviarCorreo(destinatario, asunto, texto);
			} catch (RuntimeException e) {
				// Después del commit una excepción llegaría al cliente como 500 con la cuenta ya creada y su contraseña perdida.
				// Es el peor resultado posible: el administrador creería que falló y la temporal, que ya
				// viajaba en la respuesta, se habría perdido.
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
			// Sin transacción (por ejemplo, desde una prueba) no hay commit que esperar.
			envio.run();
		}
	}

	// Los datos de la cuenta, con la forma del login (sin token) y sin la contraseña ni su hash.
	// Es gemelo del cuenta() de AuthServiceImpl: la misma forma desde los dos servicios, para que el
	// frontend lea igual cualquier respuesta de cuenta.
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
