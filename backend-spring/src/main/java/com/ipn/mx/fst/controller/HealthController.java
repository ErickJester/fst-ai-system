package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoint de salud: responde si el backend está arriba. Sirve para probar que docker compose levantó todo.
// Es público (no pide token) y usa el mismo envoltorio {data, error, status} que el resto de la API.
@RestController
public class HealthController {

	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		return Respuesta.ok(Map.of("estado", "ok"), HttpStatus.OK);
	}

}
