// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// El servicio con los cálculos de resultados.
import com.ipn.mx.fst.service.ResultadosService;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
// ByteArrayOutputStream: un "archivo en memoria" donde se escribe el ZIP antes de mandarlo.
import java.io.ByteArrayOutputStream;
import java.io.IOException;
// StandardCharsets: para que el nombre del archivo descargado admita acentos.
import java.nio.charset.StandardCharsets;
// Files: utilidades de archivos (aquí, copiar uno dentro del ZIP).
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
// Las dos clases del paquete estándar de Java para escribir un ZIP.
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
// ByteArrayResource: un Resource sobre bytes que ya están en memoria (el ZIP recién armado).
import org.springframework.core.io.ByteArrayResource;
// FileSystemResource: un Resource sobre un archivo del disco.
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
// ContentDisposition y HttpHeaders: las cabeceras que controlan la descarga.
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
// Las anotaciones de ruta.
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Progreso y resultados de un experimento. Todas las rutas son de lectura, para cualquier usuario autenticado.
// Todas responden con el envoltorio {data, error, status}, menos la descarga del PDF de diagnóstico, que
// devuelve un archivo.
//
// "Para cualquier usuario autenticado" es la regla R-07: los investigadores ven los experimentos de todos,
// no solo los suyos. Es un laboratorio, no un sistema de documentos privados.
// Las seis primeras rutas son la misma información con distinto grado de agregación: por espécimen, por
// minuto, por bloques de cinco, por grupo, y en forma de datos para graficar. Cada pantalla pide la que
// necesita, en vez de traerse todo y recortar en el navegador.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/experiments")
public class ResultadosController {

	@Autowired
	private ResultadosService resultadosService;

	// GET /experiments/{id}/status: por dónde va el análisis de cada video (la barra de avance).
	@GetMapping("/{id}/status")
	public ResponseEntity<Map<String, Object>> status(@PathVariable Integer id) {
		return Respuesta.ok(resultadosService.estado(id), HttpStatus.OK);
	}

	// Los resultados por espécimen: el detalle más fino que se publica.
	@GetMapping("/{id}/results")
	public ResponseEntity<Map<String, Object>> results(@PathVariable Integer id) {
		return Respuesta.ok(resultadosService.resultados(id), HttpStatus.OK);
	}

	// Los mismos datos agrupados por minuto (cinco minutos, ver SegundoId).
	@GetMapping("/{id}/results/byminute")
	public ResponseEntity<Map<String, Object>> resultsPorMinuto(@PathVariable Integer id) {
		return Respuesta.ok(resultadosService.resultadosPorMinuto(id), HttpStatus.OK);
	}

	// Agrupados en bloques de cinco segundos (ver BloquesDeCinco), que es como se puntúa la prueba a mano.
	@GetMapping("/{id}/results/blocks")
	public ResponseEntity<Map<String, Object>> resultsPorBloques(@PathVariable Integer id) {
		return Respuesta.ok(resultadosService.resultadosPorBloques(id), HttpStatus.OK);
	}

	// Agrupados por grupo, con medias y desviaciones: es la tabla que responde la pregunta del experimento,
	// si el grupo tratado se comporta distinto del control.
	@GetMapping("/{id}/results/groups")
	public ResponseEntity<Map<String, Object>> resultsPorGrupo(@PathVariable Integer id) {
		return Respuesta.ok(resultadosService.resultadosPorGrupo(id), HttpStatus.OK);
	}

	// Datos de las gráficas de resultados (M1A/M1B): todos los grupos del Día 2 del experimento, sin importar la tanda.
	// Es la misma información que lee el PDF para dibujar sus gráficas (ver DatosGraficas): el PDF no
	// recalcula nada, usa esto.
	@GetMapping("/{id}/results/charts")
	public ResponseEntity<Map<String, Object>> resultsGraficas(@PathVariable Integer id) {
		return Respuesta.ok(resultadosService.graficas(id), HttpStatus.OK);
	}

	// "Salida del modelo" (solo modo desarrollador, sin documentar): lo que decidió el modelo en cada segundo, TAL CUAL lo
	// deja fst_auto, byte por byte, sin reescribirlo; sirve para reentrenar. Con grupo y tanda, el CSV de esa tanda; con
	// solo grupo, un ZIP con el de cada tanda completada y su resumen. OJO: es la salida CRUDA del modelo (el video
	// completo, con probabilidades) y NO trae las correcciones de la revisión. No es "segundos" del producto (la
	// revisión y los resultados por segundo): por eso vive bajo /dev y con otro nombre. Sin MODO_DESARROLLADOR el
	// servicio responde 404; sin sesión, 401 (lo pone la seguridad).
	//
	// La advertencia en mayúsculas importa de verdad: estos datos NO son los resultados del sistema. Son
	// lo que el modelo escupió antes de que nadie lo revisara, y sirven para reentrenarlo. Confundirlos con
	// los resultados buenos (los de /results, que sí llevan las correcciones) daría conclusiones falsas.
	// De ahí que la ruta lleve /dev y un nombre distinto: para que nadie los tome por lo que no son.
	@GetMapping("/{id}/dev/salida-modelo")
	public ResponseEntity<Resource> salidaModelo(@PathVariable Integer id, @RequestParam(required = false) Integer grupo,
			@RequestParam(required = false) String tanda) throws IOException {
		// El servicio decide si toca un CSV suelto o un ZIP, y devuelve las rutas de los archivos.
		ResultadosService.SalidaModelo salida = resultadosService.salidaDelModelo(id, grupo, tanda);

		HttpHeaders headers = new HttpHeaders();
		// attachment: se descarga, no se muestra. El segundo argumento (UTF_8) codifica el nombre para que
		// los acentos de la etiqueta del grupo lleguen bien al navegador.
		headers.setContentDisposition(ContentDisposition.attachment().filename(salida.nombre(), StandardCharsets.UTF_8).build());
		// Caso simple: un solo CSV. Se manda el archivo del disco tal cual, sin leerlo a memoria.
		if (!salida.zip()) {
			// values().iterator().next() saca el único archivo del mapa, ya que no importa su nombre.
			return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType("text/csv"))
					.body(new FileSystemResource(salida.archivos().values().iterator().next()));
		}
		// Caso ZIP: se arma en memoria. Son CSV de unos pocos cientos de kilobytes, así que cabe de sobra;
		// con archivos grandes habría que escribir directo a la respuesta para no llenar la memoria.
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		// try-con-recursos: el ZipOutputStream se cierra solo al salir del bloque, incluso si algo falla.
		// Cerrarlo es obligatorio: es lo que escribe el índice final del ZIP, sin el cual el archivo no
		// se puede abrir.
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, Path> archivo : salida.archivos().entrySet()) {
				// La clave del mapa es el nombre que tendrá dentro del ZIP; el valor, la ruta en disco.
				zip.putNextEntry(new ZipEntry(archivo.getKey()));
				// Files.copy vuelca el archivo dentro de la entrada abierta.
				Files.copy(archivo.getValue(), zip);
				// Hay que cerrar cada entrada antes de abrir la siguiente.
				zip.closeEntry();
			}
		}
		// ByteArrayResource y no FileSystemResource: el ZIP está en memoria, no en disco.
		return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType("application/zip"))
				.body(new ByteArrayResource(bytes.toByteArray()));
	}

	// La imagen de control (los tubos que encontró el pipeline) tal cual, sin envoltorio: la pantalla de un análisis
	// con error la muestra. Pide sesión como el resto; si no existe, el servicio lanza el 404 con envoltorio.
	//
	// Esa última frase explica cómo conviven las dos formas: el caso bueno devuelve bytes de imagen, y el
	// malo devuelve JSON con envoltorio, porque el 404 lo arma ManejadorErrores a partir del ErrorApi que
	// lanzó el servicio. Son respuestas de tipos distintos para la misma ruta, y es correcto.
	@GetMapping("/{id}/analyses/{idAnalisis}/control")
	public ResponseEntity<Resource> control(@PathVariable Integer id, @PathVariable Integer idAnalisis) {
		return ResponseEntity
				.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.body(new FileSystemResource(resultadosService.control(id, idAnalisis)));
	}

	// El PDF de diagnóstico tal cual, sin envoltorio, como el reporte de resultados: Content-Disposition
	// "inline" para que el navegador lo muestre. Si no existe, el servicio lanza el 404 con envoltorio.
	//
	// Es el PDF que deja el worker cuando un análisis falla (la columna rutaDiagnostico de Analisis).
	// "inline" hace que se abra en la pestaña; compárese con el "attachment" del Excel y del ZIP de arriba,
	// que fuerzan la descarga. Un PDF el navegador sabe mostrarlo, y verlo sin bajarlo es más cómodo.
	@GetMapping("/{id}/analyses/{idAnalisis}/diagnostic")
	public ResponseEntity<Resource> diagnostico(@PathVariable Integer id, @PathVariable Integer idAnalisis) {
		Path pdf = resultadosService.diagnostico(id, idAnalisis);

		HttpHeaders headers = new HttpHeaders();
		// Aquí la cabecera se escribe a mano en vez de con ContentDisposition: el nombre lo arma el código
		// con el id y no lleva acentos ni espacios, así que no hace falta codificarlo.
		headers.add("Content-Disposition", "inline; filename=diagnostico_analisis_" + idAnalisis + ".pdf");

		return ResponseEntity
				.ok()
				.headers(headers)
				.contentType(MediaType.APPLICATION_PDF)
				.body(new FileSystemResource(pdf));
	}

}
