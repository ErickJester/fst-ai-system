package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.domain.Experimento;
import com.ipn.mx.fst.service.ExperimentoService;
import com.ipn.mx.fst.utilerias.Respuesta;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// CRUD de experimentos, con la misma estructura que EventoController de la práctica, pero sin los mapas
// {"mensaje", "error"} armados a mano: toda respuesta JSON usa el envoltorio {"data", "error", "status"} de
// la guía (Respuesta). Las reglas viven en el servicio, que lanza ErrorApi, y ManejadorErrores convierte
// cualquier error en el envoltorio; por eso aquí no hay try/catch ni BindingResult.
// Las rutas son las de la guía (/experiments), que es el contrato con el frontend.
// CORS: ya no se declara aquí; con Spring Security va centralizado en SeguridadConfig. Todas las rutas piden
// el token de sesión.
@RestController
@RequestMapping("/experiments")
public class ExperimentoController {

	@Autowired
	private ExperimentoService experimentoService;

	// Lista paginada (20 por página) de todos los experimentos: todos los investigadores ven los de todos
	// (R-07). pagina vale 1 por omisión; si no es un número entero ≥ 1 responde 400.
	@GetMapping
	public ResponseEntity<Map<String, Object>> readAll(@RequestParam(defaultValue = "1") int pagina) {
		return Respuesta.ok(experimentoService.listar(pagina), HttpStatus.OK);
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

	// Detalle con el árbol de grupos, tandas y videos (guía 6.1).
	@GetMapping("/{id}")
	public ResponseEntity<Map<String, Object>> read(@PathVariable Integer id) {
		return Respuesta.ok(experimentoService.detalle(id), HttpStatus.OK);
	}

	// Grupos del experimento para el autocompletado al subir un video: q filtra por "etiqueta · tratamiento"
	// sin distinguir mayúsculas ni acentos, y cada grupo trae el ordinal de su siguiente tanda.
	@GetMapping("/{id}/groups")
	public ResponseEntity<Map<String, Object>> groups(@PathVariable Integer id,
			@RequestParam(required = false) String q) {
		return Respuesta.ok(experimentoService.buscarGrupos(id, q), HttpStatus.OK);
	}

	// Crea un experimento. @Valid revisa nombre y fecha (400 lo da ManejadorErrores); el servicio revisa el
	// nombre repetido (409). Responde 201 con el id nuevo, que es lo que pidió el frontend.
	@PostMapping
	public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Experimento experimento, Authentication authentication) {
		// El creador es el usuario del token (authentication.getName() es su idInstitucional).
		Experimento experimentoNuevo = experimentoService.crear(experimento, authentication.getName());
		return Respuesta.ok(Map.of("idExperimento", experimentoNuevo.getIdExperimento()), HttpStatus.CREATED);
	}

	// Actualiza nombre, fecha y notas. El creador no cambia aunque venga otro en el JSON.
	@PutMapping("/{id}")
	public ResponseEntity<Map<String, Object>> update(@Valid @RequestBody Experimento experimento, @PathVariable Integer id) {
		return Respuesta.ok(experimentoService.actualizar(id, experimento), HttpStatus.OK);
	}

	// Elimina una tanda con sus videos y resultados, para dejar libre su letra si se subió mal: solo el creador del
	// experimento o un Administrador (403); 404 si la tanda no es de ese experimento; 409 si su análisis está procesando o
	// en pausa. 204 sin cuerpo.
	@DeleteMapping("/{id}/tandas/{idTanda}")
	public ResponseEntity<Map<String, Object>> deleteTanda(@PathVariable Integer id, @PathVariable Integer idTanda,
			Authentication authentication) {
		experimentoService.eliminarTanda(id, idTanda, authentication);
		return ResponseEntity.noContent().build();
	}

	// Elimina un experimento: solo su creador o un Administrador (403). Por ahora solo los que no tienen
	// grupos (409 si los tiene, 404 si no existe).
	@DeleteMapping("/{id}")
	public ResponseEntity<Map<String, Object>> delete(@PathVariable Integer id, Authentication authentication) {
		experimentoService.eliminar(id, authentication);

		// 204 "sin contenido": por estándar no lleva cuerpo, así que tampoco lleva envoltorio.
		return ResponseEntity.noContent().build();
	}

}
