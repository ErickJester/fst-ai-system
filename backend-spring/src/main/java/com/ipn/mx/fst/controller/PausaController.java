package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.service.PausaService;
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// La pausa de un análisis (§6.1): ver la imagen, ajustar el recuadro o cancelar. Respuestas con el envoltorio
// {data, error, status}, salvo la imagen, que es un JPG.
@RestController
@RequestMapping("/experiments/{id}/analyses/{idAnalisis}")
public class PausaController {

	@Autowired
	private PausaService pausaService;

	@GetMapping("/pause")
	public ResponseEntity<Map<String, Object>> pausa(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		return Respuesta.ok(pausaService.pausa(id, idAnalisis, authentication), HttpStatus.OK);
	}

	@GetMapping(value = "/pause/image", produces = MediaType.IMAGE_JPEG_VALUE)
	public ResponseEntity<Resource> imagen(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
				.body(pausaService.imagen(id, idAnalisis, authentication));
	}

	@PostMapping("/pause/adjust")
	public ResponseEntity<Map<String, Object>> ajustar(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			@RequestBody Map<String, Object> recuadro, Authentication authentication) {
		pausaService.ajustar(id, idAnalisis, recuadro, authentication);
		return Respuesta.ok(null, HttpStatus.OK);
	}

	@PostMapping("/cancel")
	public ResponseEntity<Map<String, Object>> cancelar(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		pausaService.cancelar(id, idAnalisis, authentication);
		return Respuesta.ok(null, HttpStatus.OK);
	}
}
