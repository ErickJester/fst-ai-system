package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.service.SegundosDeResultados;
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Los segundos de una tanda, uno por uno, para la pantalla segundo a segundo. Lectura para cualquier usuario autenticado,
// como los demás resultados. Con envoltorio {data, error, status}.
@RestController
@RequestMapping("/experiments")
public class SegundosController {

	@Autowired
	private SegundosDeResultados segundos;

	@GetMapping("/{id}/results/seconds")
	// grupo y tanda son opcionales en la firma para que su falta llegue a deUnaTanda, que responde 400 con su mensaje (y no el
	// 500 del manejador general que da la ausencia de un parámetro obligatorio).
	public ResponseEntity<Map<String, Object>> segundosDeUnaTanda(@PathVariable Integer id,
			@RequestParam(required = false) Integer grupo, @RequestParam(required = false) String tanda) {
		return Respuesta.ok(segundos.deUnaTanda(id, grupo, tanda), HttpStatus.OK);
	}
}
