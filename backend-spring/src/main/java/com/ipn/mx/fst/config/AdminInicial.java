package com.ipn.mx.fst.config;

import com.ipn.mx.fst.domain.Administrador;
import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.repository.AdministradorRepository;
import com.ipn.mx.fst.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Crea el primer Administrador al arrancar con los valores ADMIN_* del .env (el "crear-admin" de la guía,
// sección 11.7). Sin él nadie podría entrar, porque las cuentas las crea un Administrador.
// Es idempotente: si el usuario ya existe no hace nada y nunca cambia la contraseña de una cuenta existente.
// Si faltan datos solo avisa en el log y el backend arranca igual.
@Component
public class AdminInicial implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

	@Value("${ADMIN_ID:}")
	private String id;

	@Value("${ADMIN_NOMBRE:}")
	private String nombre;

	@Value("${ADMIN_APELLIDOS:}")
	private String apellidos;

	@Value("${ADMIN_CORREO:}")
	private String correo;

	@Value("${ADMIN_PASSWORD:}")
	private String password;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorRepository administradorRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (id.isBlank()) {
			log.info("ADMIN_ID vacío: no se crea el administrador inicial");
			return;
		}

		List<String> faltantes = new ArrayList<>();
		if (nombre.isBlank()) faltantes.add("ADMIN_NOMBRE");
		if (apellidos.isBlank()) faltantes.add("ADMIN_APELLIDOS");
		if (correo.isBlank()) faltantes.add("ADMIN_CORREO");
		if (password.isBlank()) faltantes.add("ADMIN_PASSWORD");
		if (!faltantes.isEmpty()) {
			log.warn("No se crea el administrador inicial: falta {}", String.join(", ", faltantes));
			return;
		}

		// La columna idinstitucional mide 10 caracteres.
		if (id.length() > 10) {
			log.warn("No se crea el administrador inicial: ADMIN_ID debe tener 10 caracteres o menos");
			return;
		}

		if (usuarioRepository.existsById(id) || usuarioRepository.existsByCorreoIgnoreCase(correo)) {
			log.info("El administrador inicial ya existe");
			return;
		}

		// cambioRequerido = true: la contraseña del .env es temporal y se cambia en el primer ingreso.
		Usuario usuario = usuarioRepository.save(
				new Usuario(id, nombre, apellidos, correo, passwordEncoder.encode(password), true, true));

		// La fila de administrador comparte la llave con el usuario (@MapsId), no se le pone id aparte.
		Administrador administrador = new Administrador();
		administrador.setUsuario(usuario);
		administradorRepository.save(administrador);

		log.info("Administrador inicial {} creado", id);
	}

}
