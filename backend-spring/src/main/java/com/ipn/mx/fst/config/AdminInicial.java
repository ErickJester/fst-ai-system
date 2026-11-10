// Carpeta lógica de esta clase: com/ipn/mx/fst/config.
package com.ipn.mx.fst.config;

// Administrador: la entidad de la tabla administrador (la que marca qué usuarios son admins).
import com.ipn.mx.fst.domain.Administrador;
// Usuario: la entidad de la tabla usuario (los datos de la cuenta y el hash de la contraseña).
import com.ipn.mx.fst.domain.Usuario;
// Los repositorios: el acceso a esas dos tablas.
import com.ipn.mx.fst.repository.AdministradorRepository;
import com.ipn.mx.fst.repository.UsuarioRepository;
// ArrayList y List: la lista donde se van juntando los nombres de las variables que faltan.
import java.util.ArrayList;
import java.util.List;
// Logger y LoggerFactory: el registro (log). Escribir con el log y no con System.out permite filtrar por
// nivel (info, warn, error) y que Docker recoja la salida con su formato.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// @Autowired: Spring inyecta aquí el objeto ya construido.
import org.springframework.beans.factory.annotation.Autowired;
// @Value: inyecta el valor de una propiedad o variable de entorno en un campo.
import org.springframework.beans.factory.annotation.Value;
// ApplicationArguments: los argumentos con los que se lanzó la aplicación. Aquí no se usan, pero el método
// de la interfaz los exige.
import org.springframework.boot.ApplicationArguments;
// ApplicationRunner: interfaz de Spring Boot para "ejecuta esto UNA vez, al terminar de arrancar".
import org.springframework.boot.ApplicationRunner;
// PasswordEncoder: el cifrador de contraseñas. SeguridadConfig lo crea como BCryptPasswordEncoder.
import org.springframework.security.crypto.password.PasswordEncoder;
// @Component: Spring crea esta clase al arrancar y la administra.
import org.springframework.stereotype.Component;
// @Transactional: envuelve el método en una transacción de base de datos (todo se guarda o nada).
import org.springframework.transaction.annotation.Transactional;

// Crea el primer Administrador al arrancar con los valores ADMIN_* del .env (el "crear-admin" de la guía,
// sección 11.7). Sin él nadie podría entrar, porque las cuentas las crea un Administrador.
// Es idempotente: si el usuario ya existe no hace nada y nunca cambia la contraseña de una cuenta existente.
// Si faltan datos solo avisa en el log y el backend arranca igual.
//
// El problema que resuelve se llama "arranque en frío": una instalación nueva tiene la base vacía, las
// cuentas solo las puede crear un administrador, y no hay ninguno. Alguien tiene que entrar primero.
// Idempotente quiere decir que correrlo una vez o diez veces da el mismo resultado: importa porque este
// código se ejecuta en CADA arranque del contenedor, no solo en el primero.
@Component
public class AdminInicial implements ApplicationRunner {

	// El registro de esta clase. static final porque es uno solo para todas las instancias y no cambia.
	// getClass() no se usa: se pasa AdminInicial.class para que cada línea del log diga de dónde salió.
	private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

	// @Value("${ADMIN_ID:}") lee la variable de entorno ADMIN_ID. Lo que va DESPUÉS de los dos puntos es el
	// valor por omisión; aquí está vacío a propósito: si nadie la define, el campo queda "" y el método de
	// abajo se da cuenta y no crea nada. Sin ese ":" el backend no arrancaría al faltar la variable.
	// Estas cinco no están en application.properties: se leen directas del entorno, como dice el comentario
	// de application.properties en la sección de autenticación.
	@Value("${ADMIN_ID:}")
	private String id;

	@Value("${ADMIN_NOMBRE:}")
	private String nombre;

	@Value("${ADMIN_APELLIDOS:}")
	private String apellidos;

	@Value("${ADMIN_CORREO:}")
	private String correo;

	// La contraseña EN CLARO que viene del .env. Nunca se guarda así: abajo pasa por passwordEncoder.
	@Value("${ADMIN_PASSWORD:}")
	private String password;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorRepository administradorRepository;

	// El cifrador bcrypt que define SeguridadConfig. Se inyecta en vez de construirlo aquí para que haya uno
	// solo en todo el sistema: si mañana se cambia el algoritmo, se cambia en un único lugar.
	@Autowired
	private PasswordEncoder passwordEncoder;

	// @Override: implementa run(...) de ApplicationRunner. Spring Boot lo llama solo, una vez, cuando la
	// aplicación ya terminó de levantar (el contexto está listo y la base conectada).
	@Override
	// @Transactional: las dos escrituras de abajo (usuario y administrador) van en la misma transacción.
	// Si la segunda fallara, la primera se deshace y no queda un usuario suelto sin su fila de administrador.
	@Transactional
	public void run(ApplicationArguments args) {
		// isBlank() es true si el texto está vacío o solo tiene espacios (isEmpty() solo mira si está vacío).
		// Sin ADMIN_ID no hay nada que hacer, y no es un error: así arranca una instalación que ya tiene
		// su administrador creado desde hace meses.
		if (id.isBlank()) {
			log.info("ADMIN_ID vacío: no se crea el administrador inicial");
			return;
		}

		// En vez de rendirse en el primer dato que falte, se juntan TODOS los que faltan y se avisan juntos.
		// Así quien configura el .env corrige de una vez y no arranca cuatro veces seguidas.
		List<String> faltantes = new ArrayList<>();
		if (nombre.isBlank()) faltantes.add("ADMIN_NOMBRE");
		if (apellidos.isBlank()) faltantes.add("ADMIN_APELLIDOS");
		if (correo.isBlank()) faltantes.add("ADMIN_CORREO");
		if (password.isBlank()) faltantes.add("ADMIN_PASSWORD");
		if (!faltantes.isEmpty()) {
			// warn y no error: es una advertencia, el backend sigue arrancando. El {} es un hueco que slf4j
			// rellena con el argumento siguiente; se usa así, y no concatenando con +, para no armar el
			// texto cuando ese nivel de log está apagado.
			log.warn("No se crea el administrador inicial: falta {}", String.join(", ", faltantes));
			return;
		}

		// La columna idinstitucional mide 10 caracteres.
		// Se comprueba ANTES de insertar para dar un mensaje claro en el log, en vez de dejar que PostgreSQL
		// tire un error de longitud que nadie entiende al arrancar.
		if (id.length() > 10) {
			log.warn("No se crea el administrador inicial: ADMIN_ID debe tener 10 caracteres o menos");
			return;
		}

		// Aquí está la idempotencia. Se comprueban las DOS formas de que ya exista: por id y por correo.
		// Por correo también porque la columna es única: si ya hay otra cuenta con ese correo, el insert
		// fallaría. IgnoreCase porque "Ana@IPN.MX" y "ana@ipn.mx" son el mismo correo para una persona.
		if (usuarioRepository.existsById(id) || usuarioRepository.existsByCorreoIgnoreCase(correo)) {
			log.info("El administrador inicial ya existe");
			return;
		}

		// cambioRequerido = true: la contraseña del .env es temporal y se cambia en el primer ingreso.
		// Los dos booleanos del final del constructor son, en orden: activo = true y cambioRequerido = true.
		// passwordEncoder.encode(password) aplica bcrypt: en la base queda el hash, nunca la contraseña.
		// Bcrypt añade además una "sal" distinta cada vez, así que dos cuentas con la misma contraseña
		// guardan hashes diferentes.
		Usuario usuario = usuarioRepository.save(
				new Usuario(id, nombre, apellidos, correo, passwordEncoder.encode(password), true, true));

		// La fila de administrador comparte la llave con el usuario (@MapsId), no se le pone id aparte.
		// Por eso solo se le asigna el usuario: JPA saca de ahí la llave primaria. Ser administrador no es
		// una columna de usuario, es tener fila en esta tabla; de ahí el existsById de ConvertidorJwt.
		Administrador administrador = new Administrador();
		administrador.setUsuario(usuario);
		administradorRepository.save(administrador);

		log.info("Administrador inicial {} creado", id);
	}

}
