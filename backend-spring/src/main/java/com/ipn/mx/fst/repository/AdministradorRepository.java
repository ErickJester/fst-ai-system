package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Administrador;
import org.springframework.data.jpa.repository.JpaRepository;

// La llave de administrador es la misma de usuario (identificador institucional). existsById(id) responde si
// un usuario es Administrador.
public interface AdministradorRepository extends JpaRepository<Administrador, String> {
}
