package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.service.VideoService;
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Subida de un video desde la página. Cuerpo multipart/form-data: archivo, sesion ('Dia 1' o 'Dia 2'),
// tipo y tratamiento (solo si el grupo es nuevo), nCilindros (solo si la tanda es nueva) y ordinal (la tanda
// confirmada; si falta, la siguiente). {grupo} es la ETIQUETA del grupo; Spring ya la decodifica de la URL.
// Todos los campos son opcionales para Spring: las reglas y sus mensajes (400, 422, 409) los pone el
// servicio, para que el frontend reciba siempre el envoltorio {data, error, status}.
@RestController
@RequestMapping("/experiments")
public class VideoController {

	@Autowired
	private VideoService videoService;

	@PostMapping(path = "/{id}/groups/{grupo}/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> subir(@PathVariable Integer id, @PathVariable String grupo,
			@RequestParam(required = false) MultipartFile archivo,
			@RequestParam(required = false) String sesion,
			@RequestParam(required = false) String tipo,
			@RequestParam(required = false) String tratamiento,
			@RequestParam(required = false) Integer nCilindros,
			@RequestParam(required = false) Integer ordinal,
			Authentication authentication) {
		return Respuesta.ok(
				videoService.subir(id, grupo, archivo, sesion, tipo, tratamiento, nCilindros, ordinal, authentication),
				HttpStatus.CREATED);
	}

}
