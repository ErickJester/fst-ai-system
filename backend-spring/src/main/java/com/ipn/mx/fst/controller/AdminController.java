package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.dto.CrearCuentaPeticion;
import com.ipn.mx.fst.dto.ModificarCuentaPeticion;
import com.ipn.mx.fst.service.AdminService;
import com.ipn.mx.fst.utilerias.Respuesta;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

	// Ruta propuesta por el frontend (la guía no la trae). Responde {passwordTemporal}, que solo se devuelve aquí.
	@PostMapping("/users/{id}/reset-temporal")
	public ResponseEntity<Map<String, Object>> nuevaTemporal(@PathVariable String id) {
		return Respuesta.ok(adminService.nuevaTemporal(id), HttpStatus.OK);
	}

	@GetMapping("/system")
	public ResponseEntity<Map<String, Object>> sistema() {
		return Respuesta.ok(adminService.sistema(), HttpStatus.OK);
	}

}
