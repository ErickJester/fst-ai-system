package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.service.ResultadosService;
import com.ipn.mx.fst.utilerias.Respuesta;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Progreso y resultados de un experimento. Todas las rutas son de lectura, para cualquier usuario autenticado.
// Todas responden con el envoltorio {data, error, status}, menos la descarga del PDF de diagnóstico, que
// devuelve un archivo.
@RestController
@RequestMapping("/experiments")
public class ResultadosController {

	@Autowired
	private ResultadosService resultadosService;

	@GetMapping("/{id}/status")
	public ResponseEntity<Map<String, Object>> status(@PathVariable Integer id) {
		return Respuesta.ok(resultadosService.estado(id), HttpStatus.OK);
	}

	@GetMapping("/{id}/results")
	public ResponseEntity<Map<String, Object>> results(@PathVariable Integer id) {
		return Respuesta.ok(resultadosService.resultados(id), HttpStatus.OK);
	}

	@GetMapping("/{id}/results/byminute")
	public ResponseEntity<Map<String, Object>> resultsPorMinuto(@PathVariable Integer id) {
		return Respuesta.ok(resultadosService.resultadosPorMinuto(id), HttpStatus.OK);
	}

	@GetMapping("/{id}/results/groups")
	public ResponseEntity<Map<String, Object>> resultsPorGrupo(@PathVariable Integer id) {
		return Respuesta.ok(resultadosService.resultadosPorGrupo(id), HttpStatus.OK);
	}

	// El PDF de diagnóstico tal cual, sin envoltorio, como el reporte de resultados: Content-Disposition
	// "inline" para que el navegador lo muestre. Si no existe, el servicio lanza el 404 con envoltorio.
	@GetMapping("/{id}/analyses/{idAnalisis}/diagnostic")
	public ResponseEntity<Resource> diagnostico(@PathVariable Integer id, @PathVariable Integer idAnalisis) {
		Path pdf = resultadosService.diagnostico(id, idAnalisis);

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Disposition", "inline; filename=diagnostico_analisis_" + idAnalisis + ".pdf");

		return ResponseEntity
				.ok()
				.headers(headers)
				.contentType(MediaType.APPLICATION_PDF)
				.body(new FileSystemResource(pdf));
	}

}
