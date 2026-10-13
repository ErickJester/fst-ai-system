package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

// La llave de usuario es el identificador institucional (String), no un número.
public interface UsuarioRepository extends JpaRepository<Usuario, String> {
}
