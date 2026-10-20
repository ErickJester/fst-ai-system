package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.service.ResultadosService;
import com.ipn.mx.fst.utilerias.Respuesta;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

	// "Segundos" (solo modo desarrollador): la salida por segundo del modelo TAL CUAL la deja fst_auto, byte por byte, sin
	// reescribirla. Con grupo y tanda, el CSV de esa tanda; con solo grupo, un ZIP con el de cada tanda completada y su
	// resumen. OJO: es la salida CRUDA del modelo (el video completo, con probabilidades) y NO trae las correcciones de
	// la revisión. Sin MODO_DESARROLLADOR el servicio responde 404; sin sesión, 401 (lo pone la seguridad).
	@GetMapping("/{id}/report/segundos")
	public ResponseEntity<Resource> segundos(@PathVariable Integer id, @RequestParam(required = false) Integer grupo,
			@RequestParam(required = false) String tanda) throws IOException {
		ResultadosService.SalidaSegundos salida = resultadosService.segundosDelModelo(id, grupo, tanda);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentDisposition(ContentDisposition.attachment().filename(salida.nombre(), StandardCharsets.UTF_8).build());
		if (!salida.zip()) {
			return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType("text/csv"))
					.body(new FileSystemResource(salida.archivos().values().iterator().next()));
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, Path> archivo : salida.archivos().entrySet()) {
				zip.putNextEntry(new ZipEntry(archivo.getKey()));
				Files.copy(archivo.getValue(), zip);
				zip.closeEntry();
			}
		}
		return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType("application/zip"))
				.body(new ByteArrayResource(bytes.toByteArray()));
	}

	// La imagen de control (los tubos que encontró el pipeline) tal cual, sin envoltorio: la pantalla de un análisis
	// con error la muestra. Pide sesión como el resto; si no existe, el servicio lanza el 404 con envoltorio.
	@GetMapping("/{id}/analyses/{idAnalisis}/control")
	public ResponseEntity<Resource> control(@PathVariable Integer id, @PathVariable Integer idAnalisis) {
		return ResponseEntity
				.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.body(new FileSystemResource(resultadosService.control(id, idAnalisis)));
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
