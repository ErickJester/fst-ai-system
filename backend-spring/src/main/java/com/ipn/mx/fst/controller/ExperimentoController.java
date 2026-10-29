package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.domain.Experimento;
import com.ipn.mx.fst.domain.valores.FormatoReporte;
import com.ipn.mx.fst.service.ExperimentoService;
import com.ipn.mx.fst.service.ReporteAlmacen;
import com.ipn.mx.fst.service.ReporteCsv;
import com.ipn.mx.fst.service.ReporteXlsx;
import com.ipn.mx.fst.service.ResultadosService;
import com.ipn.mx.fst.service.ResultadosService.AlcanceDeReporte;
import com.ipn.mx.fst.utilerias.Respuesta;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	private ReporteAlmacen reportes;

	@Autowired
	private ResultadosService resultadosService;

	private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");
	private static final MediaType XLSX = MediaType
			.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	// Lista paginada (20 por página) de todos los experimentos: todos los investigadores ven los de todos
	// (R-07). pagina vale 1 por omisión; si no es un número entero ≥ 1 responde 400. Filtros opcionales: estado (el que trae
	// cada item), fecha (AAAA o AAAA-MM-DD), tratamiento (de algún grupo, completo) y q (un texto que contiene el nombre o el
	// tratamiento de algún grupo); los textos no distinguen mayúsculas ni acentos y un valor que no es de esos formatos
	// responde 400. Además de pagina, total e items trae resumen, de todos los experimentos.
	@Operation(summary = "Lista de experimentos, filtrada y paginada (20 por página)", description = """
			Los filtros son opcionales y se combinan: **estado** (el que trae cada item), **fecha** (AAAA o AAAA-MM-DD),
			**tratamiento** (el de algún grupo, completo) y **q** (texto que contiene el nombre del experimento o el
			tratamiento de algún grupo). Los textos no distinguen mayúsculas ni acentos; un valor inválido responde 400.
			`total` son los experimentos que cumplen los filtros. `resumen` es de TODOS los experimentos, con o sin filtros:
			`total`, `porEstado` (cuántos hay en cada estado), `anios` (el más reciente primero), `tratamientos` (sin repetir,
			ordenados) y `avisos` (los videos que se borran en 7 días o menos: `idExperimento`, `nombre`, `fechaBorrado`).""")
	@GetMapping
	public ResponseEntity<Map<String, Object>> readAll(@RequestParam(defaultValue = "1") int pagina,
			@RequestParam(required = false) String estado, @RequestParam(required = false) String fecha,
			@RequestParam(required = false) String tratamiento, @RequestParam(required = false) String q) {
		return Respuesta.ok(experimentoService.listar(pagina, estado, fecha, tratamiento, q), HttpStatus.OK);
	}

	// Reporte de resultados en PDF (sección 8 de la guía). Es de las pocas respuestas que no llevan el
	// envoltorio, porque devuelve un archivo, con Content-Disposition "inline" para que el navegador lo muestre.
	// Pasa por ReporteAlmacen: si el PDF guardado sigue vigente, sale del archivo; si no, generarReporte lo arma y se
	// guarda. 404 con envoltorio si el experimento no existe; si la generación falla, sube a ManejadorErrores (500).
	@GetMapping("/{id}/report/pdf")
	public ResponseEntity<byte[]> generarPdf(@PathVariable Integer id) throws IOException {
		// Sin análisis completados el PDF dice «Sin resultados» y no se guarda: el siguiente análisis completado no lo invalidaría.
		byte[] pdf = reportes.obtenerSiCorresponde(id, FormatoReporte.PDF, "experimento", () -> {
			ExperimentoService.ReporteDeResultados resultado = experimentoService.generarReporteDeResultados(id);
			return new ReporteAlmacen.Generado(resultado.pdf(), resultado.conResultados());
		});

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Disposition", "inline; filename=reporte_experimento_" + id + ".pdf");

		return ResponseEntity
				.ok()
				.headers(headers)
				.contentType(MediaType.APPLICATION_PDF)
				.body(pdf);
	}

	// Reporte de un grupo completo (?grupo=idGrupo) o de una tanda (?grupo=idGrupo&tanda=A) en CSV (D-99). Es un archivo, así que
	// tampoco lleva el envoltorio; se descarga (attachment). Pasa por ReporteAlmacen como el PDF: 400 sin grupo o con una letra
	// que no es A-Z, 404 si el grupo o la tanda no existen y 409 si no hay análisis completados. Esos errores salen antes de guardar
	// nada: el 409 lo lanza el generador dentro de la transacción del almacén, que se deshace sin dejar fila ni archivo.
	@GetMapping("/{id}/report/csv")
	public ResponseEntity<byte[]> generarCsv(@PathVariable Integer id, @RequestParam(required = false) Integer grupo,
			@RequestParam(required = false) String tanda) throws IOException {
		AlcanceDeReporte alcance = resultadosService.alcanceDeReporte(id, grupo, tanda);
		byte[] csv = reportes.obtener(id, FormatoReporte.CSV, alcance.alcance(),
				() -> ReporteCsv.armar(resultadosService.tablaDeReporte(id, grupo, tanda)));
		return descarga(csv, CSV, nombreDeArchivo(id, alcance, "csv"));
	}

	// Lo mismo en XLSX (Apache POI, una hoja «Resultados»).
	@GetMapping("/{id}/report/xlsx")
	public ResponseEntity<byte[]> generarXlsx(@PathVariable Integer id, @RequestParam(required = false) Integer grupo,
			@RequestParam(required = false) String tanda) throws IOException {
		AlcanceDeReporte alcance = resultadosService.alcanceDeReporte(id, grupo, tanda);
		byte[] xlsx = reportes.obtener(id, FormatoReporte.XLSX, alcance.alcance(),
				() -> ReporteXlsx.armar(resultadosService.tablaDeReporte(id, grupo, tanda)));
		return descarga(xlsx, XLSX, nombreDeArchivo(id, alcance, "xlsx"));
	}

	// reporte_experimento_{id}_grupo-{idGrupo}.ext o, de una tanda, reporte_experimento_{id}_grupo-{idGrupo}_tanda-{letra}.ext
	// (la letra sola no distingue entre los grupos). Todo sale de ids y de una letra A-Z ya validada.
	private static String nombreDeArchivo(Integer id, AlcanceDeReporte alcance, String extension) {
		String tanda = alcance.letra() == null ? "" : "_tanda-" + alcance.letra();
		return "reporte_experimento_" + id + "_grupo-" + alcance.idGrupo() + tanda + "." + extension;
	}

	private static ResponseEntity<byte[]> descarga(byte[] archivo, MediaType tipo, String nombre) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Disposition", "attachment; filename=" + nombre);
		return ResponseEntity.ok().headers(headers).contentType(tipo).body(archivo);
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
	public ResponseEntity<Map<String, Object>> update(@Valid @RequestBody Experimento experimento, @PathVariable Integer id,
			Authentication authentication) {
		return Respuesta.ok(experimentoService.actualizar(id, experimento, authentication), HttpStatus.OK);
	}

	// Elimina una tanda con sus videos y resultados, para dejar libre su letra si se subió mal: solo el creador del
	// experimento o un Administrador (403); 404 si la tanda no es de ese experimento; 409 si su análisis está procesando o
	// en pausa. Si era la última tanda de su grupo, el grupo también se borra. 204 sin cuerpo.
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
