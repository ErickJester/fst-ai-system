package com.ipn.mx.fst.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// Manejo de errores global: va a reemplazar los mapas {mensaje, error} que cada controller arma a mano
// (sección 2 del checklist). Por ahora solo cubre el 413 de la subida de archivos.
// Todas las respuestas usan el envoltorio acordado con el frontend: {"data", "error", "status"}.
@RestControllerAdvice
public class ManejadorErrores {

	@Value("${MAX_UPLOAD_MB:2048}")
	private long maxUploadMb;

	// Se lanza cuando el archivo (o la petición) pasa del límite de spring.servlet.multipart.*
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> archivoMuyGrande(MaxUploadSizeExceededException e) {
		// LinkedHashMap conserva el orden data, error, status; con Map.of no se puede poner el null en "data".
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("data", null);
		cuerpo.put("error", "El archivo excede el límite de " + maxUploadMb + " MB");
		cuerpo.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(cuerpo);
	}

}
