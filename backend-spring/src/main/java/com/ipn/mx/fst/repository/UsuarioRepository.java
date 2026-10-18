package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// La llave de usuario es el identificador institucional (String), no un número.
public interface UsuarioRepository extends JpaRepository<Usuario, String> {

	// El correo es el nombre de usuario del login; se compara sin importar mayúsculas.
	Optional<Usuario> findByCorreoIgnoreCase(String correo);

	boolean existsByCorreoIgnoreCase(String correo);

}
