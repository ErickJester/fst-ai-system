package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.domain.Experimento;
import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.service.ExperimentoService;
import com.ipn.mx.fst.utilerias.service.EmailService;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// CRUD de experimentos, con la misma estructura que EventoController de la práctica: cada método arma un
// mapa {"mensaje", "error"} o {"errores"} cuando algo sale mal y elige el código HTTP a mano.
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
	@ResponseStatus(HttpStatus.OK)
	public List<Experimento> readAll() {
		return experimentoService.findAll();
	}

	// Reporte de resultados en PDF (sección 8 de la guía). Como en la práctica: se genera en memoria y se
	// responde con Content-Disposition "inline" para que el navegador lo muestre.
	@GetMapping("/{id}/report/pdf")
	public ResponseEntity<?> generarPdf(@PathVariable Integer id) {
		Map<String, Object> respuesta = new HashMap<>();

		if (experimentoService.findById(id) == null) {
			respuesta.put("mensaje", "El experimento ".concat(id.toString()).concat(" no existe en la base de datos"));
			return new ResponseEntity<>(respuesta, HttpStatus.NOT_FOUND);
		}

		ByteArrayInputStream pdf;
		try {
			pdf = experimentoService.generarReporte(id);
		} catch (DataAccessException | IllegalStateException e) {
			respuesta.put("mensaje", "Error al generar el reporte");
			respuesta.put("error", e.getMessage());
			return new ResponseEntity<>(respuesta, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Disposition", "inline; filename=reporte_experimento_" + id + ".pdf");

		return ResponseEntity
				.ok()
				.headers(headers)
				.contentType(MediaType.APPLICATION_PDF)
				.body(new InputStreamResource(pdf));
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> read(@PathVariable Integer id) {
		Experimento experimento = null;
		Map<String, Object> respuesta = new HashMap<>();

		try {
			experimento = experimentoService.findById(id);
		} catch (DataAccessException e) {
			respuesta.put("mensaje", "Error al realizar la consulta");
			respuesta.put("error", e.getMessage().concat(" = ").concat(e.getMostSpecificCause().getMessage()));
			return new ResponseEntity<>(respuesta, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		if (experimento == null) {
			respuesta.put("mensaje", "El experimento ".concat(id.toString()).concat(" no existe en la base de datos"));
			return new ResponseEntity<>(respuesta, HttpStatus.NOT_FOUND);
		}

		return new ResponseEntity<>(experimento, HttpStatus.OK);
	}

	// Crea un experimento: solo nombre (único), fecha y notas opcionales, más el creador. Los grupos se
	// capturan después, al subir el primer video de cada uno (todavía no implementado).
	// Respuestas: 400 datos inválidos, 404 el creador no existe, 409 nombre repetido, 500 falla de la base.
	@PostMapping
	public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Experimento experimento, BindingResult result) {
		Experimento experimentoNuevo = null;
		Map<String, Object> respuesta = new HashMap<>();

		List<String> errores = result.getFieldErrors()
				.stream()
				.map(err -> "El campo '" + err.getField() + "' " + err.getDefaultMessage())
				.collect(Collectors.toCollection(ArrayList::new));
		// idInstitucional no lleva @NotBlank en la entidad (el PUT no lo pide), así que se revisa aquí.
		if (experimento.getIdInstitucional() == null || experimento.getIdInstitucional().isBlank()) {
			errores.add("El campo 'idInstitucional' no debe estar vacío");
		}
		if (!errores.isEmpty()) {
			respuesta.put("errores", errores);
			return new ResponseEntity<>(respuesta, HttpStatus.BAD_REQUEST);
		}

		Usuario creador = experimentoService.findUsuarioById(experimento.getIdInstitucional());
		if (creador == null) {
			respuesta.put("mensaje", "El usuario ".concat(experimento.getIdInstitucional()).concat(" no existe en la base de datos"));
			return new ResponseEntity<>(respuesta, HttpStatus.NOT_FOUND);
		}

		if (experimentoService.existeNombre(experimento.getNombre(), null)) {
			respuesta.put("mensaje", "Ya existe un experimento con el nombre '".concat(experimento.getNombre()).concat("'"));
			return new ResponseEntity<>(respuesta, HttpStatus.CONFLICT);
		}

		// El id lo asigna PostgreSQL; si el JSON trae uno, se ignora para que siempre sea un INSERT.
		experimento.setIdExperimento(null);

		try {
			experimentoNuevo = experimentoService.save(experimento);
		} catch (DataIntegrityViolationException e) {
			// Por si otro usuario guardó el mismo nombre justo entre la revisión de arriba y este INSERT.
			respuesta.put("mensaje", "Ya existe un experimento con el nombre '".concat(experimento.getNombre()).concat("'"));
			return new ResponseEntity<>(respuesta, HttpStatus.CONFLICT);
		} catch (DataAccessException e) {
			respuesta.put("mensaje", "Error al insertar el experimento");
			respuesta.put("error", e.getMessage().concat(" = ").concat(e.getMostSpecificCause().getMessage()));
			return new ResponseEntity<>(respuesta, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		// DEMOSTRACIÓN del servicio de correo: la guía no pide avisar al crear un experimento (solo define
		// seis notificaciones, que dependen del worker). Se deja para probar el envío mientras tanto.
		emailService.enviarCorreo(creador.getCorreo(), "Experimento creado",
				"Se creó el experimento " + experimentoNuevo.getNombre()
						+ " con fecha " + experimentoNuevo.getFecha() + ".");

		respuesta.put("mensaje", "El experimento se creó satisfactoriamente");
		respuesta.put("experimento", experimentoNuevo);
		return new ResponseEntity<>(respuesta, HttpStatus.CREATED);
	}

	// Actualiza nombre, fecha y notas. El creador no cambia aunque venga otro en el JSON.
	@PutMapping("/{id}")
	public ResponseEntity<Map<String, Object>> update(@Valid @RequestBody Experimento experimento, BindingResult result, @PathVariable Integer id) {
		Experimento experimentoActual = experimentoService.findById(id);
		Experimento experimentoActualizado = null;
		Map<String, Object> respuesta = new HashMap<>();

		if (result.hasErrors()) {
			List<String> errores = result.getFieldErrors()
					.stream()
					.map(err -> "El campo '" + err.getField() + "' " + err.getDefaultMessage())
					.collect(Collectors.toList());

			respuesta.put("errores", errores);
			return new ResponseEntity<>(respuesta, HttpStatus.BAD_REQUEST);
		}

		if (experimentoActual == null) {
			respuesta.put("mensaje", "Error al actualizar el experimento ".concat(id.toString()).concat(" no existe en la base de datos"));
			return new ResponseEntity<>(respuesta, HttpStatus.NOT_FOUND);
		}

		if (experimentoService.existeNombre(experimento.getNombre(), id)) {
			respuesta.put("mensaje", "Ya existe otro experimento con el nombre '".concat(experimento.getNombre()).concat("'"));
			return new ResponseEntity<>(respuesta, HttpStatus.CONFLICT);
		}

		try {
			experimentoActual.setNombre(experimento.getNombre());
			experimentoActual.setFecha(experimento.getFecha());
			experimentoActual.setNotas(experimento.getNotas());

			experimentoActualizado = experimentoService.save(experimentoActual);
		} catch (DataIntegrityViolationException e) {
			respuesta.put("mensaje", "Ya existe otro experimento con el nombre '".concat(experimento.getNombre()).concat("'"));
			return new ResponseEntity<>(respuesta, HttpStatus.CONFLICT);
		} catch (DataAccessException e) {
			respuesta.put("mensaje", "Error al actualizar el experimento");
			respuesta.put("error", e.getMessage().concat(" = ").concat(e.getMostSpecificCause().getMessage()));
			return new ResponseEntity<>(respuesta, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		respuesta.put("mensaje", "El experimento se actualizó satisfactoriamente");
		respuesta.put("experimento", experimentoActualizado);
		return new ResponseEntity<>(respuesta, HttpStatus.OK);
	}

	// Elimina un experimento. Por ahora solo los que no tienen grupos: borrar grupos, tandas, videos y
	// resultados (de las hojas hacia arriba) llega con la carga de video.
	// Respuestas: 204 eliminado, 404 no existe (la tarea pendiente de la práctica), 409 tiene grupos.
	@DeleteMapping("/{id}")
	public ResponseEntity<Map<String, Object>> delete(@PathVariable Integer id) {
		Map<String, Object> respuesta = new HashMap<>();

		if (experimentoService.findById(id) == null) {
			respuesta.put("mensaje", "Error al eliminar el experimento ".concat(id.toString()).concat(" no existe en la base de datos"));
			return new ResponseEntity<>(respuesta, HttpStatus.NOT_FOUND);
		}

		if (experimentoService.tieneGrupos(id)) {
			respuesta.put("mensaje", "El experimento ".concat(id.toString()).concat(" tiene grupos y todavía no se puede eliminar"));
			return new ResponseEntity<>(respuesta, HttpStatus.CONFLICT);
		}

		try {
			experimentoService.delete(id);
		} catch (DataIntegrityViolationException e) {
			respuesta.put("mensaje", "El experimento tiene datos relacionados y no se puede eliminar");
			respuesta.put("error", e.getMostSpecificCause().getMessage());
			return new ResponseEntity<>(respuesta, HttpStatus.CONFLICT);
		} catch (DataAccessException e) {
			respuesta.put("mensaje", "Error al eliminar el experimento");
			respuesta.put("error", e.getMessage().concat(" = ").concat(e.getMostSpecificCause().getMessage()));
			return new ResponseEntity<>(respuesta, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		// 204 "sin contenido": por estándar no lleva cuerpo, así que no se manda mensaje.
		return ResponseEntity.noContent().build();
	}

}
