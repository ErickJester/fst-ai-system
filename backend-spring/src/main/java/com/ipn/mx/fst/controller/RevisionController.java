package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.service.RevisionService;
import com.ipn.mx.fst.utilerias.Respuesta;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Revisión segundo a segundo de un análisis completado. Todas responden con el envoltorio {data, error, status},
// menos el video, que devuelve el archivo (con soporte de Range para adelantar y retroceder).
@RestController
@RequestMapping("/experiments/{id}/analyses/{idAnalisis}")
public class RevisionController {

	@Autowired
	private RevisionService revisionService;

	@GetMapping("/review")
	public ResponseEntity<Map<String, Object>> revision(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		return Respuesta.ok(revisionService.revision(id, idAnalisis, authentication), HttpStatus.OK);
	}

	@PostMapping("/video-token")
	public ResponseEntity<Map<String, Object>> tokenDeVideo(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		return Respuesta.ok(revisionService.crearTokenVideo(id, idAnalisis, authentication), HttpStatus.OK);
	}

	// Esta ruta es pública en SeguridadConfig (la etiqueta <video> no manda Authorization): quien la protege es
	// el token de video de ?vt=, que revisa el servicio. Spring MVC atiende la cabecera Range (206 Partial
	// Content) por sí solo cuando el cuerpo es un Resource.
	@GetMapping("/video")
	public ResponseEntity<Resource> video(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			@RequestParam(name = "vt", required = false) String vt) {
		Path video = revisionService.videoPorToken(id, idAnalisis, vt);
		return ResponseEntity
				.ok()
				.contentType(MediaType.parseMediaType("video/mp4"))
				.body(new FileSystemResource(video));
	}

	@PutMapping("/segundos")
	public ResponseEntity<Map<String, Object>> corregir(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			@RequestBody(required = false) Map<String, Object> cuerpo, Authentication authentication) {
		return Respuesta.ok(revisionService.corregir(id, idAnalisis, cuerpo, authentication), HttpStatus.OK);
	}

}
