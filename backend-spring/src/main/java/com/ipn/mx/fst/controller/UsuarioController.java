package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.dto.PerfilPeticion;
import com.ipn.mx.fst.service.AuthService;
import com.ipn.mx.fst.utilerias.Respuesta;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Datos de la cuenta de quien tiene la sesión abierta. El usuario sale del token (authentication.getName() es
// su idInstitucional), nunca de la URL ni del cuerpo: nadie puede editar la cuenta de otro por aquí.
@RestController
@RequestMapping("/users")
public class UsuarioController {

	@Autowired
	private AuthService authService;

	// Si cambia el nombre o los apellidos, se invalidan los reportes guardados de todos los experimentos de la cuenta
	// (AuthServiceImpl.actualizarPerfil). PATCH /admin/users/{id} (AdminServiceImpl.modificar) hace lo mismo, con los mismos
	// candados en el mismo orden (CandadosDeCuenta).
	@PatchMapping("/me")
	public ResponseEntity<Map<String, Object>> actualizarPerfil(@Valid @RequestBody PerfilPeticion peticion,
			Authentication authentication) {
		return Respuesta.ok(authService.actualizarPerfil(authentication.getName(), peticion), HttpStatus.OK);
	}

}
