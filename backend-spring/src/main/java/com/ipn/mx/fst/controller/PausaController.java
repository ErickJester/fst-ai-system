// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// El servicio con las reglas de la pausa.
import com.ipn.mx.fst.service.PausaService;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
// Resource: una fuente de datos binarios (un archivo) que Spring sabe volcar en la respuesta sin
// cargarla entera en memoria.
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
// Authentication: el usuario autenticado; se pasa entero al servicio.
import org.springframework.security.core.Authentication;
// Las anotaciones de ruta: GET, variable de ruta, POST, cuerpo y prefijo común.
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// La pausa de un análisis (§6.1): ver la imagen, ajustar el recuadro o cancelar. Respuestas con el envoltorio
// {data, error, status}, salvo la imagen, que es un JPG.
//
// Qué es la pausa: cuando el worker no logra detectar con seguridad los cilindros donde nadan los
// especímenes, no se arriesga a medir mal. Deja el análisis en estado 'en pausa' (ver EstadoAnalisis),
// guarda una imagen con el recuadro que propuso y espera a que una persona lo corrija o cancele.
// Es el punto donde el sistema admite que el modelo no es infalible y pide ayuda en vez de inventar datos.
// Fíjate en el @RequestMapping: lleva DOS variables de ruta, así que las cinco rutas de abajo las reciben
// todas. Las tres de acción devuelven null como data: no hay nada que contar, solo confirmar.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/experiments/{id}/analyses/{idAnalisis}")
public class PausaController {

	@Autowired
	private PausaService pausaService;

	// GET .../pause: los datos de la pausa (por qué se detuvo, dónde está el recuadro propuesto).
	// El id del experimento viaja además del análisis para que el servicio compruebe que ese análisis es
	// de ese experimento: sin ello, cambiando el número de la ruta se podría husmear en otro.
	@GetMapping("/pause")
	public ResponseEntity<Map<String, Object>> pausa(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		return Respuesta.ok(pausaService.pausa(id, idAnalisis, authentication), HttpStatus.OK);
	}

	// GET .../pause/image: el fotograma con el recuadro dibujado, para que la persona vea qué detectó.
	// Es la excepción al envoltorio: devuelve un JPG. produces declara el tipo, y Spring lo pone en la
	// cabecera; contentType lo repite al armar la respuesta.
	// Resource y no byte[]: Spring vuelca el archivo directo a la respuesta sin cargarlo entero en memoria.
	// Ojo con la seguridad: esta ruta SÍ exige sesión, al revés que el video de la revisión, que es
	// permitAll y se protege con su propio token en ?vt= (ver SeguridadConfig).
	@GetMapping(value = "/pause/image", produces = MediaType.IMAGE_JPEG_VALUE)
	public ResponseEntity<Resource> imagen(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
				.body(pausaService.imagen(id, idAnalisis, authentication));
	}

	// POST .../pause/adjust: la persona mueve el recuadro y manda las coordenadas nuevas.
	// Se recibe como Map crudo y no como DTO, igual que en NotificacionController: las coordenadas son
	// unos pocos números y el servicio los valida con sus propios mensajes.
	// Aquí entra en juego app.pausa.umbral-recuadro-px (3 px por omisión): si el recuadro casi no se movió,
	// el servicio responde 422, porque no cuenta como un ajuste de verdad.
	@PostMapping("/pause/adjust")
	public ResponseEntity<Map<String, Object>> ajustar(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			@RequestBody Map<String, Object> recuadro, Authentication authentication) {
		pausaService.ajustar(id, idAnalisis, recuadro, authentication);
		// data en null: la acción no devuelve nada, solo confirma que se hizo.
		return Respuesta.ok(null, HttpStatus.OK);
	}

	// POST .../pause/continue: seguir adelante con el recuadro tal como está.
	// Devuelve el análisis a la cola cambiándole el estado, y el worker lo retoma.
	@PostMapping("/pause/continue")
	public ResponseEntity<Map<String, Object>> continuar(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		pausaService.continuar(id, idAnalisis, authentication);
		return Respuesta.ok(null, HttpStatus.OK);
	}

	// POST .../cancel: abandonar el análisis. Fíjate en que la ruta NO lleva /pause: cancelar vale para
	// cualquier análisis en marcha, no solo para uno pausado.
	// El análisis queda en estado 'cancelado' (ver EstadoAnalisis); la fila no se borra, para que quede
	// constancia de que se intentó.
	@PostMapping("/cancel")
	public ResponseEntity<Map<String, Object>> cancelar(@PathVariable Integer id, @PathVariable Integer idAnalisis,
			Authentication authentication) {
		pausaService.cancelar(id, idAnalisis, authentication);
		return Respuesta.ok(null, HttpStatus.OK);
	}
}
