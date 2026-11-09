package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.service.ModeloActualDeDesarrollo;
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// El modelo que usa el worker, para las herramientas de desarrollo. Pide sesión como el resto; sin modo desarrollador, 404.
@RestController
@RequestMapping("/dev")
public class DevModeloController {

	@Autowired
	private ModeloActualDeDesarrollo modelo;

	@GetMapping("/modelo-actual")
	public ResponseEntity<Map<String, Object>> modeloActual() {
		return Respuesta.ok(modelo.modeloActual(), HttpStatus.OK);
	}
}
