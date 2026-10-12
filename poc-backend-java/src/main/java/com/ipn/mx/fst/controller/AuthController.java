package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.dto.LoginRequest;
import com.ipn.mx.fst.dto.LoginResponse;
import com.ipn.mx.fst.service.AuthService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest peticion) {
		String email = peticion.email() == null ? "" : peticion.email().trim().toLowerCase();
		String password = peticion.password() == null ? "" : peticion.password();
		if (email.isEmpty() || password.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "Credenciales inválidas"));
		}

		LoginResponse respuesta = authService.login(email, password);
		if (respuesta == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "Credenciales inválidas"));
		}
		return ResponseEntity.ok(respuesta);
	}

	@PostMapping("/logout")
	public Map<String, Object> logout() {
		// TODO: invalidar el token cuando exista JWT de verdad
		return Map.of("ok", true);
	}
}
