package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.domain.Experimento;
import com.ipn.mx.fst.service.ExperimentoService;
import com.ipn.mx.fst.utilerias.Respuesta;
import com.ipn.mx.fst.utilerias.service.EmailService;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// CRUD de experimentos, con la misma estructura que EventoController de la práctica, pero sin los mapas
// {"mensaje", "error"} armados a mano: toda respuesta JSON usa el envoltorio {"data", "error", "status"} de
// la guía (Respuesta). Las reglas viven en el servicio, que lanza ErrorApi, y ManejadorErrores convierte
// cualquier error en el envoltorio; por eso aquí no hay try/catch ni BindingResult.
// Las rutas son las de la guía (/experiments), que es el contrato con el frontend.
// CORS: el frontend (React + Vite) corre en localhost:5173 en desarrollo.
@CrossOrigin(origins = {"http://localhost:5173"})
@RestController
@RequestMapping("/experiments")
public class ExperimentoController {

	@Autowired
	private ExperimentoService experimentoService;

	@Autowired
	private EmailService emailService;

	// Lista todos los experimentos. Todos los investigadores ven los de todos (R-07).
	@GetMapping
	public ResponseEntity<Map<String, Object>> readAll() {
		return Respuesta.ok(experimentoService.findAll(), HttpStatus.OK);
	}

	// Reporte de resultados en PDF (sección 8 de la guía). Es de las pocas respuestas que no llevan el
	// envoltorio, porque devuelve un archivo: se genera en memoria y se responde con Content-Disposition
	// "inline" para que el navegador lo muestre. Si el experimento no existe, buscar da el 404 con envoltorio;
	// si generarReporte falla, la excepción sube a ManejadorErrores (500).
	@GetMapping("/{id}/report/pdf")
	public ResponseEntity<InputStreamResource> generarPdf(@PathVariable Integer id) {
		experimentoService.buscar(id);
		ByteArrayInputStream pdf = experimentoService.generarReporte(id);

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Disposition", "inline; filename=reporte_experimento_" + id + ".pdf");

		return ResponseEntity
				.ok()
				.headers(headers)
				.contentType(MediaType.APPLICATION_PDF)
				.body(new InputStreamResource(pdf));
	}

	@GetMapping("/{id}")
	public ResponseEntity<Map<String, Object>> read(@PathVariable Integer id) {
		return Respuesta.ok(experimentoService.buscar(id), HttpStatus.OK);
	}

	// Crea un experimento. @Valid revisa nombre y fecha (400 lo da ManejadorErrores); el servicio revisa el
	// creador (404) y el nombre repetido (409). Responde 201 con el id nuevo, que es lo que pidió el frontend.
	@PostMapping
	public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Experimento experimento) {
		Experimento experimentoNuevo = experimentoService.crear(experimento);

		// DEMOSTRACIÓN del servicio de correo: la guía no pide avisar al crear un experimento (solo define
		// seis notificaciones, que dependen del worker). Se deja para probar el envío mientras tanto.
		emailService.enviarCorreo(experimentoNuevo.getUsuario().getCorreo(), "Experimento creado",
				"Se creó el experimento " + experimentoNuevo.getNombre()
						+ " con fecha " + experimentoNuevo.getFecha() + ".");

		return Respuesta.ok(Map.of("idExperimento", experimentoNuevo.getIdExperimento()), HttpStatus.CREATED);
	}

	// Actualiza nombre, fecha y notas. El creador no cambia aunque venga otro en el JSON.
	@PutMapping("/{id}")
	public ResponseEntity<Map<String, Object>> update(@Valid @RequestBody Experimento experimento, @PathVariable Integer id) {
		return Respuesta.ok(experimentoService.actualizar(id, experimento), HttpStatus.OK);
	}

	// Elimina un experimento. Por ahora solo los que no tienen grupos (409 si los tiene, 404 si no existe).
	@DeleteMapping("/{id}")
	public ResponseEntity<Map<String, Object>> delete(@PathVariable Integer id) {
		experimentoService.eliminar(id);

		// 204 "sin contenido": por estándar no lleva cuerpo, así que tampoco lleva envoltorio.
		return ResponseEntity.noContent().build();
	}

}
