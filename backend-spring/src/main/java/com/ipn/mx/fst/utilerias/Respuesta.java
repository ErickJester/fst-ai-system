package com.ipn.mx.fst.utilerias;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// Envoltorio de toda respuesta JSON de la API (sección 6 de la guía): {"data", "error", "status"}.
// Con éxito "error" es null; con error "data" es null y el mensaje va en "error". Solo se salta esta regla
// una respuesta que devuelve un archivo (el PDF). En la práctica cada método armaba su propio mapa; aquí
// todos usan estos dos métodos para que el frontend lea siempre la misma forma.
public final class Respuesta {

	private Respuesta() {
	}

	public static ResponseEntity<Map<String, Object>> ok(Object data, HttpStatus status) {
		// LinkedHashMap conserva el orden data, error, status; Map.of no acepta el null.
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("data", data);
		cuerpo.put("error", null);
		cuerpo.put("status", status.value());
		return ResponseEntity.status(status).body(cuerpo);
	}

	public static ResponseEntity<Map<String, Object>> error(String mensaje, HttpStatus status) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("data", null);
		cuerpo.put("error", mensaje);
		cuerpo.put("status", status.value());
		return ResponseEntity.status(status).body(cuerpo);
	}

}
