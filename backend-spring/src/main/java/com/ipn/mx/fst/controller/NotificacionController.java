package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.service.NotificacionService;
import com.ipn.mx.fst.utilerias.ErrorApi;
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// La campanita. Todas las rutas son de la cuenta de la sesión: el usuario sale del token
// (authentication.getName() es su idInstitucional), nunca de la URL.
@RestController
@RequestMapping("/notifications")
public class NotificacionController {

	@Autowired
	private NotificacionService notificacionService;

	@GetMapping
	public ResponseEntity<Map<String, Object>> listar(Authentication authentication) {
		return Respuesta.ok(notificacionService.listar(authentication.getName()), HttpStatus.OK);
	}

	// Cuerpo {"leido": true|false}. No hay "marcar todas": el frontend hace un PATCH por cada notificación.
	// Se recibe como mapa y no como una clase con Boolean, para que {"leido": "si"} sea un 400 claro y no
	// dependa de cómo convierta Jackson un texto a booleano.
	@PatchMapping("/{id}")
	public ResponseEntity<Map<String, Object>> marcar(@PathVariable Integer id,
			@RequestBody Map<String, Object> cuerpo, Authentication authentication) {
		if (!(cuerpo.get("leido") instanceof Boolean leido)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El campo 'leido' es obligatorio y debe ser true o false.");
		}
		return Respuesta.ok(notificacionService.marcar(authentication.getName(), id, leido), HttpStatus.OK);
	}

}
