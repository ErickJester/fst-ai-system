package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.dto.CrearCuentaPeticion;
import com.ipn.mx.fst.dto.ModificarCuentaPeticion;
import com.ipn.mx.fst.service.AdminService;
import com.ipn.mx.fst.utilerias.Respuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Administración (R-07). La revisión de rol está en SeguridadConfig (todo /admin/** pide ROLE_ADMINISTRADOR, que ConvertidorJwt lee
// de la base en cada petición): un Investigador recibe 403 antes de llegar aquí. No hay DELETE: las cuentas se desactivan.
@RestController
@RequestMapping("/admin")
public class AdminController {

	@Autowired
	private AdminService adminService;

	@GetMapping("/users")
	public ResponseEntity<Map<String, Object>> listar(@RequestParam(required = false) String rol,
			@RequestParam(required = false) String estado) {
		return Respuesta.ok(adminService.listar(rol, estado), HttpStatus.OK);
	}

	// 201 con la cuenta y passwordTemporal, que solo se devuelve aquí.
	@PostMapping("/users")
	public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody CrearCuentaPeticion peticion) {
		return Respuesta.ok(adminService.crear(peticion), HttpStatus.CREATED);
	}

	@PatchMapping("/users/{id}")
	public ResponseEntity<Map<String, Object>> modificar(@PathVariable String id,
			@Valid @RequestBody ModificarCuentaPeticion peticion) {
		return Respuesta.ok(adminService.modificar(id, peticion), HttpStatus.OK);
	}

	// Ruta oficial (la guía no la trae; Erick la aceptó el 28-oct-2026). Responde {passwordTemporal}, que solo se devuelve aquí.
	// authentication.getName() es el idInstitucional de la sesión: con él el servicio rechaza que el Administrador se la dé a
	// su propia cuenta (para eso está PATCH /auth/password).
	@Operation(summary = "Restablecer contraseña temporal", description = """
			Le da a una cuenta existente una contraseña temporal nueva (12 caracteres), la manda por correo y la devuelve UNA vez en \
			`passwordTemporal`. La cuenta queda con `cambioRequerido = true`: sus tokens anteriores dan 401 salvo en \
			`PATCH /auth/password` y `POST /auth/logout`, la contraseña vieja y los enlaces de restablecer pendientes dejan de servir. \
			Si el correo falla igual responde 200 con la temporal. Queda una línea de auditoría en el registro del servidor (sin la \
			contraseña).""")
	@ApiResponse(responseCode = "200", description = "{passwordTemporal}")
	@ApiResponse(responseCode = "403", description = "La sesión no es de un Administrador")
	@ApiResponse(responseCode = "404", description = "La cuenta no existe")
	@ApiResponse(responseCode = "409", description = "La cuenta está desactivada, o es la propia cuenta del Administrador (usa Mi perfil)")
	@PostMapping("/users/{id}/reset-temporal")
	public ResponseEntity<Map<String, Object>> nuevaTemporal(@PathVariable String id, Authentication authentication) {
		return Respuesta.ok(adminService.nuevaTemporal(id, authentication.getName()), HttpStatus.OK);
	}

	@GetMapping("/system")
	public ResponseEntity<Map<String, Object>> sistema() {
		return Respuesta.ok(adminService.sistema(), HttpStatus.OK);
	}

}
