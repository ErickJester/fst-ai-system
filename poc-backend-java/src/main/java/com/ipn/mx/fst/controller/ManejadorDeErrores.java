package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.service.ReglaDeNegocioException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce los errores de negocio al mismo {"error": "..."} que devuelve Flask. */
@RestControllerAdvice
public class ManejadorDeErrores {

	@ExceptionHandler(ReglaDeNegocioException.class)
	public ResponseEntity<Map<String, String>> negocio(ReglaDeNegocioException e) {
		return ResponseEntity.status(e.getEstado()).body(Map.of("error", e.getMessage()));
	}
}
