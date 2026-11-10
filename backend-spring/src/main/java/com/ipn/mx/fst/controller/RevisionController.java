// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// El servicio con las reglas de la revisión.
import com.ipn.mx.fst.service.RevisionService;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
// Path: una ruta de archivo.
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
// FileSystemResource: un Resource que apunta a un archivo del disco. Spring lo vuelca a la respuesta sin
// cargarlo entero en memoria, que con un video de 1 GB es imprescindible.
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
// Las anotaciones de ruta: GET, variable de ruta, POST, PUT, cuerpo, prefijo y parámetro de consulta.
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
//
// Es la pantalla donde una persona comprueba el trabajo del modelo: ve el video, va segundo a segundo y
// confirma, corrige o marca como dudoso. Lo que escribe aquí es la columna origen de la tabla segundo
// (ver OrigenSegundo), que es la que da trazabilidad al dato.
// Comparte prefijo con PausaController, pero son dos momentos distintos: la pausa ocurre DURANTE el
// análisis y la revisión DESPUÉS de completarlo.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/experiments/{id}/analyses/{idAnalisis}")
public class RevisionController {

	@Autowired
	private RevisionService revisionService;

	// GET .../review: los datos de la pantalla (los segundos con su conducta y su origen).
	@GetMapping("/review")
	public ResponseEntity<Map<String, Object>> revision(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		return Respuesta.ok(revisionService.revision(id, idAnalisis, authentication), HttpStatus.OK);
	}

	// POST .../video-token: pide un token de corta duración para poder ver el video.
	// Es POST y no GET porque CREA algo (un token), aunque no toque la base.
	// Ese token dura 120 minutos (app.video.minutos) y sirve solo para este análisis. Existe por el
	// problema que explica la ruta siguiente.
	@PostMapping("/video-token")
	public ResponseEntity<Map<String, Object>> tokenDeVideo(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		return Respuesta.ok(revisionService.crearTokenVideo(id, idAnalisis, authentication), HttpStatus.OK);
	}

	// Esta ruta es pública en SeguridadConfig (la etiqueta <video> no manda Authorization): quien la protege es
	// el token de video de ?vt=, que revisa el servicio. Spring MVC atiende la cabecera Range (206 Partial
	// Content) por sí solo cuando el cuerpo es un Resource.
	//
	// El problema de fondo: un <video src="..."> del navegador descarga la URL por su cuenta y no hay forma
	// de añadirle la cabecera Authorization. La API exige token en esa cabecera, así que el video no se
	// podría reproducir. La solución es dejar la ruta abierta en la configuración de seguridad y ponerle
	// su propio token en la URL (?vt=), que el servicio comprueba.
	// Por eso ese token es distinto del de sesión: dura poco, sirve para un solo análisis y es de solo
	// lectura. Y por eso SeguridadConfig exige el claim tipo="sesion" para todo lo demás, de modo que este
	// token no abra nada más. Que viaje en la URL es justamente lo que obliga a que dure poco: las URL
	// quedan registradas en los logs del servidor.
	// Qué es Range: la cabecera con la que el navegador pide "dame del byte X al Y", que es lo que permite
	// saltar a la mitad del video sin descargarlo entero. Spring la atiende solo porque el cuerpo es un
	// Resource; con un byte[] habría que implementarla a mano.
	@GetMapping("/video")
	public ResponseEntity<Resource> video(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			// name = "vt" porque el parámetro de la URL se llama distinto que la variable de Java.
			// required = false: el truco de siempre, para que la falta del token la conteste el servicio
			// con su mensaje y no acabe en un 500.
			@RequestParam(name = "vt", required = false) String vt) {
		// El servicio comprueba el token Y que el análisis sea de ese experimento antes de dar la ruta.
		Path video = revisionService.videoPorToken(id, idAnalisis, vt);
		return ResponseEntity
				.ok()
				.contentType(MediaType.parseMediaType("video/mp4"))
				.body(new FileSystemResource(video));
	}

	// PUT .../segundos: guarda las correcciones que hizo la persona.
	// PUT y no PATCH porque reemplaza el estado de los segundos que manda, no los modifica a trozos.
	// El cuerpo es un Map y además required = false: la falta de cuerpo la contesta el servicio con un 400
	// redactado, en vez de la excepción genérica de Spring.
	@PutMapping("/segundos")
	public ResponseEntity<Map<String, Object>> corregir(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			@RequestBody(required = false) Map<String, Object> cuerpo, Authentication authentication) {
		return Respuesta.ok(revisionService.corregir(id, idAnalisis, cuerpo, authentication), HttpStatus.OK);
	}

	// R-04: 202, porque el trabajo sigue: el análisis queda en cola y el worker lo corre otra vez.
	//
	// 202 (ACCEPTED) es el código para "recibí tu petición y la voy a hacer, pero todavía no está hecha".
	// Es exactamente el caso: el backend solo cambia el estado del análisis a 'en cola'; quien de verdad
	// reanaliza es el worker, minutos después. Responder 200 daría a entender que ya terminó.
	@PostMapping("/restart")
	public ResponseEntity<Map<String, Object>> reiniciar(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		return Respuesta.ok(revisionService.reiniciar(id, idAnalisis, authentication), HttpStatus.ACCEPTED);
	}

}
