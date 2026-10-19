package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.dto.CambioContrasenaPeticion;
import com.ipn.mx.fst.dto.LoginPeticion;
import com.ipn.mx.fst.service.AuthService;
import com.ipn.mx.fst.utilerias.Respuesta;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Inicio y cierre de sesión (sección 11 de la guía). Las dos rutas usan el envoltorio {data, error, status};
// los errores (401, 400) los arma ManejadorErrores, igual que en el resto de la API.
@RestController
@RequestMapping("/auth")
public class AuthController {

	@Autowired
	private AuthService authService;

	@PostMapping("/login")
	public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginPeticion peticion) {
		return Respuesta.ok(authService.login(peticion.getCorreo(), peticion.getContrasena()), HttpStatus.OK);
	}

	// Cambia la contraseña de quien tiene la sesión abierta (el usuario sale del token). Es lo único, junto
	// con cerrar sesión, que se permite mientras la cuenta tiene una contraseña temporal. No devuelve token:
	// el mismo sigue sirviendo.
	@PatchMapping("/password")
	public ResponseEntity<Map<String, Object>> cambiarContrasena(
			@Valid @RequestBody CambioContrasenaPeticion peticion, Authentication authentication) {
		return Respuesta.ok(authService.cambiarContrasena(authentication.getName(),
				peticion.getContrasenaActual(), peticion.getContrasenaNueva()), HttpStatus.OK);
	}

	// No hay lista de bloqueo de tokens ni sesión en el servidor: el cliente descarta su token y deja de
	// mandarlo (guía 11.1). Por eso aquí no hay nada que invalidar y solo se confirma con 200.
	@PostMapping("/logout")
	public ResponseEntity<Map<String, Object>> logout() {
		return Respuesta.ok(null, HttpStatus.OK);
	}

}
