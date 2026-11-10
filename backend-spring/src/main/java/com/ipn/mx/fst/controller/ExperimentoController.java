// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// La entidad, que aquí se usa como cuerpo de entrada del POST y del PUT (ver la nota de Experimento).
import com.ipn.mx.fst.domain.Experimento;
// El enum de los tres formatos de reporte.
import com.ipn.mx.fst.domain.valores.FormatoReporte;
// Los servicios: las reglas viven ahí, no aquí.
import com.ipn.mx.fst.service.ExperimentoService;
// ReporteAlmacen: la caché en disco de los reportes ya generados.
import com.ipn.mx.fst.service.ReporteAlmacen;
// Los dos armadores de archivos tabulares.
import com.ipn.mx.fst.service.ReporteCsv;
import com.ipn.mx.fst.service.ReporteXlsx;
import com.ipn.mx.fst.service.ResultadosService;
// AlcanceDeReporte: a qué se refiere un reporte (el experimento entero, un grupo o una tanda).
import com.ipn.mx.fst.service.ResultadosService.AlcanceDeReporte;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
// @Operation: documentación para la página de Swagger.
import io.swagger.v3.oas.annotations.Operation;
// @Valid: dispara las validaciones declaradas en el objeto que entra.
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
// HttpHeaders: las cabeceras de la respuesta (aquí, la que controla la descarga).
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
// Authentication: el usuario autenticado de la petición.
import org.springframework.security.core.Authentication;
// Las anotaciones de ruta: los cinco verbos, las variables de ruta, los parámetros y el cuerpo.
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
//
// Es el controller principal del sistema y el más grande. Atiende tres cosas distintas: el CRUD de
// experimentos, la descarga de reportes en tres formatos, y el detalle con su árbol de grupos y tandas.
// CRUD son las cuatro operaciones básicas sobre un recurso: crear (POST), leer (GET), actualizar (PUT) y
// borrar (DELETE). En HTTP cada una tiene su verbo, y por eso la misma ruta /experiments/{id} hace cosas
// distintas según con qué verbo se llame.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/experiments")
public class ExperimentoController {

	@Autowired
	private ExperimentoService experimentoService;

	// La caché en disco de los reportes: antes de generar uno, mira si el guardado sigue sirviendo.
	@Autowired
	private ReporteAlmacen reportes;

	@Autowired
	private ResultadosService resultadosService;

	// Los dos tipos de contenido de las descargas. MediaType no trae constantes para ellos, de ahí el
	// parseMediaType. El charset=UTF-8 del CSV es necesario: sin él, Excel abre los acentos rotos.
	private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");
	private static final MediaType XLSX = MediaType
			.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	// Lista paginada (20 por página) de todos los experimentos: todos los investigadores ven los de todos
	// (R-07). pagina vale 1 por omisión; si no es un número entero ≥ 1 responde 400. Filtros opcionales: estado (el que trae
	// cada item), fecha (AAAA o AAAA-MM-DD), tratamiento (de algún grupo, completo) y q (un texto que contiene el nombre o el
	// tratamiento de algún grupo); los textos no distinguen mayúsculas ni acentos y un valor que no es de esos formatos
	// responde 400. Además de pagina, total e items trae resumen, de todos los experimentos.
	//
	// Recuerda lo que explica ExperimentoRepository: el filtrado y la paginación se hacen en Java y no en
	// SQL, porque el estado no es una columna (se calcula) y el resumen es de TODOS los experimentos.
	@Operation(summary = "Lista de experimentos, filtrada y paginada (20 por página)", description = """
			Los filtros son opcionales y se combinan: **estado** (el que trae cada item), **fecha** (AAAA o AAAA-MM-DD),
			**tratamiento** (el de algún grupo, completo) y **q** (texto que contiene el nombre del experimento o el
			tratamiento de algún grupo). Los textos no distinguen mayúsculas ni acentos; un valor inválido responde 400.
			`total` son los experimentos que cumplen los filtros. `resumen` es de TODOS los experimentos, con o sin filtros:
			`total`, `porEstado` (cuántos hay en cada estado), `anios` (el más reciente primero), `tratamientos` (sin repetir,
			ordenados) y `avisos` (los videos que se borran en 7 días o menos: `idExperimento`, `nombre`, `fechaBorrado`).""")
	// @GetMapping sin ruta: responde al prefijo de la clase, es decir, GET /experiments.
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
		//
		// Es una sutileza de caché que vale la pena entender: guardar un PDF que dice "sin resultados"
		// sería peligroso, porque nada lo invalidaría cuando por fin terminara un análisis, y el
		// investigador seguiría descargando el PDF vacío. Por eso ese caso se genera siempre al vuelo.
		// El último argumento es una lambda: el código que SOLO se ejecuta si hay que generar el PDF.
		// Pasarlo así, y no el PDF ya hecho, es lo que permite que el almacén se ahorre el trabajo caro
		// cuando el archivo guardado sigue valiendo.
		byte[] pdf = reportes.obtenerSiCorresponde(id, FormatoReporte.PDF, "experimento", () -> {
			ExperimentoService.ReporteDeResultados resultado = experimentoService.generarReporteDeResultados(id);
			// El segundo campo (conResultados) es lo que le dice al almacén si merece la pena guardarlo.
			return new ReporteAlmacen.Generado(resultado.pdf(), resultado.conResultados());
		});

		HttpHeaders headers = new HttpHeaders();
		// "inline": el navegador lo MUESTRA en la pestaña. Compárese con el "attachment" del CSV y el XLSX
		// de abajo, que fuerzan la descarga porque el navegador no sabe mostrarlos.
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
	//
	// Esa última frase describe algo importante: el generador corre DENTRO de la transacción del almacén.
	// Si lanza el 409, la transacción se deshace entera y no queda ni la fila de reporte ni el archivo a
	// medio escribir. Sin eso, un error dejaría basura que después habría que limpiar a mano.
	@GetMapping("/{id}/report/csv")
	public ResponseEntity<byte[]> generarCsv(@PathVariable Integer id, @RequestParam(required = false) Integer grupo,
			@RequestParam(required = false) String tanda) throws IOException {
		// Primero se resuelve y valida el alcance (qué grupo, qué tanda). Aquí salen el 400 y el 404.
		AlcanceDeReporte alcance = resultadosService.alcanceDeReporte(id, grupo, tanda);
		// obtener (no obtenerSiCorresponde): estos reportes se guardan siempre, porque el 409 de "no hay
		// análisis completados" ya impide generar uno vacío.
		byte[] csv = reportes.obtener(id, FormatoReporte.CSV, alcance.alcance(),
				() -> ReporteCsv.armar(resultadosService.tablaDeReporte(id, grupo, tanda)));
		return descarga(csv, CSV, nombreDeArchivo(id, alcance, "csv"));
	}

	// Lo mismo en XLSX (Apache POI, una hoja «Resultados»).
	// Es el MISMO método con dos palabras cambiadas, y es a propósito: los dos formatos comparten los
	// mismos datos (tablaDeReporte) y solo cambia quién los escribe. Compárese con /report/excel de
	// ExcelExperimentoController, que es otra cosa: aquel trae el experimento entero con su hoja de
	// segundo a segundo, y no pasa por el almacén.
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
	//
	// Esa última frase es una nota de SEGURIDAD, no un detalle: el nombre se arma solo con números y con
	// una letra que alcanceDeReporte ya comprobó. Nada que haya escrito el usuario entra aquí. Si se usara
	// la etiqueta del grupo, un nombre con comillas o saltos de línea podría romper la cabecera HTTP.
	// private static: no usa nada de la instancia, solo sus parámetros.
	private static String nombreDeArchivo(Integer id, AlcanceDeReporte alcance, String extension) {
		// Si no hay letra, el trozo de la tanda queda vacío y el nombre solo lleva el grupo.
		String tanda = alcance.letra() == null ? "" : "_tanda-" + alcance.letra();
		return "reporte_experimento_" + id + "_grupo-" + alcance.idGrupo() + tanda + "." + extension;
	}

	// Lo común de las dos descargas, en un solo sitio: cabecera de descarga, tipo de contenido y cuerpo.
	private static ResponseEntity<byte[]> descarga(byte[] archivo, MediaType tipo, String nombre) {
		HttpHeaders headers = new HttpHeaders();
		// "attachment": el navegador lo guarda en vez de mostrarlo.
		headers.add("Content-Disposition", "attachment; filename=" + nombre);
		return ResponseEntity.ok().headers(headers).contentType(tipo).body(archivo);
	}

	// Detalle con el árbol de grupos, tandas y videos (guía 6.1).
	// Es la pantalla de un experimento, y la que alimentan las consultas de GrupoRepository y
	// TandaRepository: todo el árbol en dos consultas, sin el problema N+1.
	@GetMapping("/{id}")
	public ResponseEntity<Map<String, Object>> read(@PathVariable Integer id) {
		return Respuesta.ok(experimentoService.detalle(id), HttpStatus.OK);
	}

	// Grupos del experimento para el autocompletado al subir un video: q filtra por "etiqueta · tratamiento"
	// sin distinguir mayúsculas ni acentos, y cada grupo trae el ordinal de su siguiente tanda.
	//
	// Es el apoyo de VideoController: al subir un video se escribe la etiqueta del grupo, y esta ruta va
	// sugiriendo los que ya existen para no crear uno nuevo por un error de dedo. El "ordinal de su
	// siguiente tanda" es el siguienteOrdinal que calcula la consulta de GrupoRepository.
	@GetMapping("/{id}/groups")
	public ResponseEntity<Map<String, Object>> groups(@PathVariable Integer id,
			@RequestParam(required = false) String q) {
		return Respuesta.ok(experimentoService.buscarGrupos(id, q), HttpStatus.OK);
	}

	// Crea un experimento. @Valid revisa nombre y fecha (400 lo da ManejadorErrores); el servicio revisa el
	// nombre repetido (409). Responde 201 con el id nuevo, que es lo que pidió el frontend.
	//
	// Fíjate en que el cuerpo es la ENTIDAD Experimento y no un DTO, al contrario que en el resto de la
	// API. Es herencia de la práctica de clase, y la propia entidad lo explica: funciona porque sus
	// campos sensibles están protegidos de otra forma (idInstitucional lo pisa el servicio con el del
	// token, y nGrupos no tiene setter).
	@PostMapping
	public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Experimento experimento, Authentication authentication) {
		// El creador es el usuario del token (authentication.getName() es su idInstitucional).
		// Esta línea es la regla de seguridad que menciona la entidad: venga lo que venga en el cuerpo,
		// el creador es quien tiene la sesión.
		Experimento experimentoNuevo = experimentoService.crear(experimento, authentication.getName());
		// Solo se devuelve el id, no el experimento entero: es lo único que necesita el frontend para
		// llevar al usuario a la pantalla del experimento recién creado.
		return Respuesta.ok(Map.of("idExperimento", experimentoNuevo.getIdExperimento()), HttpStatus.CREATED);
	}

	// Actualiza nombre, fecha y notas. El creador no cambia aunque venga otro en el JSON.
	// PUT y no PATCH: se mandan los tres campos y se reemplazan, no se modifican a trozos.
	// Aquí se pasa el Authentication entero y no solo el nombre, porque el servicio comprueba además que
	// quien edita sea el creador o un administrador.
	@PutMapping("/{id}")
	public ResponseEntity<Map<String, Object>> update(@Valid @RequestBody Experimento experimento, @PathVariable Integer id,
			Authentication authentication) {
		return Respuesta.ok(experimentoService.actualizar(id, experimento, authentication), HttpStatus.OK);
	}

	// Elimina una tanda con sus videos y resultados, para dejar libre su letra si se subió mal: solo el creador del
	// experimento o un Administrador (403); 404 si la tanda no es de ese experimento; 409 si su análisis está procesando o
	// en pausa. Si era la última tanda de su grupo, el grupo también se borra. 204 sin cuerpo.
	//
	// El caso de uso es el del laboratorio: alguien sube el video equivocado y hay que deshacerlo.
	// El 409 cuando el análisis está en marcha evita borrarle el trabajo al worker a media faena.
	@DeleteMapping("/{id}/tandas/{idTanda}")
	public ResponseEntity<Map<String, Object>> deleteTanda(@PathVariable Integer id, @PathVariable Integer idTanda,
			Authentication authentication) {
		experimentoService.eliminarTanda(id, idTanda, authentication);
		// noContent() es el 204: el borrado salió bien y no hay nada que devolver.
		return ResponseEntity.noContent().build();
	}

	// Elimina un experimento: solo su creador o un Administrador (403). Por ahora solo los que no tienen
	// grupos (409 si los tiene, 404 si no existe).
	//
	// "Por ahora solo los que no tienen grupos" es una decisión prudente: borrar un experimento con datos
	// arrastraría grupos, tandas, videos, análisis y decenas de miles de segundos. Mientras no haya un
	// borrado en cascada bien pensado, se prefiere negarse. Recuerda que el servicio sí tiene que borrar
	// antes los reportes y las notificaciones, porque sus llaves foráneas son ON DELETE NO ACTION
	// (ver ReporteRepository y NotificacionRepository).
	@DeleteMapping("/{id}")
	public ResponseEntity<Map<String, Object>> delete(@PathVariable Integer id, Authentication authentication) {
		experimentoService.eliminar(id, authentication);

		// 204 "sin contenido": por estándar no lleva cuerpo, así que tampoco lleva envoltorio.
		return ResponseEntity.noContent().build();
	}

}
