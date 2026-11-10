// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// El servicio con las reglas de las notificaciones.
import com.ipn.mx.fst.service.NotificacionService;
// ErrorApi: la excepción que se lanza para rechazar la petición con un código y un mensaje.
import com.ipn.mx.fst.utilerias.ErrorApi;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
// @Operation: el texto que aparece en la página de Swagger para esta ruta.
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// Authentication: el usuario autenticado de la petición.
import org.springframework.security.core.Authentication;
// GET, PATCH, la variable de la ruta, el cuerpo y el prefijo común.
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// La campanita. Todas las rutas son de la cuenta de la sesión: el usuario sale del token
// (authentication.getName() es su idInstitucional), nunca de la URL.
//
// Es la misma regla de UsuarioController: al no haber un id de usuario en la ruta, nadie puede leer ni
// marcar las notificaciones de otro. El {id} que sí aparece abajo es el de la NOTIFICACIÓN, y el servicio
// comprueba además que sea de quien la pide.
// Recuerda que casi todas las notificaciones las escribe el worker (ver la entidad Notificacion): este
// controller sobre todo lee y marca como leídas.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/notifications")
public class NotificacionController {

	@Autowired
	private NotificacionService notificacionService;

	// GET /notifications. Trae las cien más recientes (el tope lo pone el servicio).
	@GetMapping
	public ResponseEntity<Map<String, Object>> listar(Authentication authentication) {
		return Respuesta.ok(notificacionService.listar(authentication.getName()), HttpStatus.OK);
	}

	// Cuántas no leídas tiene la cuenta: {noLeidas: n}. Sin el tope de GET /notifications (que trae las cien más recientes).
	//
	// Existe como ruta aparte porque el número de la campanita se consulta a menudo y traer cien
	// notificaciones completas solo para contarlas sería un desperdicio. Y, sobre todo, porque el conteo
	// no debe tener tope: con 150 sin leer, contar sobre la lista recortada diría 100.
	// @Operation es solo documentación: lo que se lee en la página de Swagger. No cambia el comportamiento.
	@Operation(summary = "Notificaciones no leídas", description = "Devuelve {noLeidas: n}: el total de la cuenta de la sesión, "
			+ "sin el tope de la lista. La campanita muestra este número.")
	@GetMapping("/unread-count")
	public ResponseEntity<Map<String, Object>> noLeidas(Authentication authentication) {
		return Respuesta.ok(notificacionService.contarNoLeidas(authentication.getName()), HttpStatus.OK);
	}

	// Cuerpo {"leido": true|false}. No hay "marcar todas": el frontend hace un PATCH por cada notificación.
	// Se recibe como mapa y no como una clase con Boolean, para que {"leido": "si"} sea un 400 claro y no
	// dependa de cómo convierta Jackson un texto a booleano.
	//
	// Es la única excepción a la regla de usar un DTO para el cuerpo, y el comentario explica por qué:
	// con un DTO de campo Boolean, Jackson podría aceptar textos como "true" y convertirlos en silencio,
	// o fallar con un mensaje suyo poco claro. Recibiendo un mapa crudo, la comprobación se hace aquí y
	// el mensaje es el que se quiere.
	@PatchMapping("/{id}")
	public ResponseEntity<Map<String, Object>> marcar(@PathVariable Integer id,
			@RequestBody Map<String, Object> cuerpo, Authentication authentication) {
		// "instanceof Boolean leido" es el instanceof con patrón, de Java 16: comprueba el tipo y, si
		// coincide, declara ya la variable con ese tipo, sin tener que convertirla en una línea aparte.
		// El ! de delante cubre los tres casos malos de una vez: que no venga la clave (get devuelve null,
		// y null no es instancia de nada), que venga con otro tipo, o que venga vacía.
		if (!(cuerpo.get("leido") instanceof Boolean leido)) {
			// Es de los pocos sitios donde un controller lanza ErrorApi directamente. Se permite porque
			// es una comprobación del FORMATO del cuerpo, no una regla de negocio: el papel que en las
			// demás rutas cumple @Valid sobre un DTO.
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El campo 'leido' es obligatorio y debe ser true o false.");
		}
		// El servicio recibe también el usuario, para comprobar que la notificación sea suya.
		return Respuesta.ok(notificacionService.marcar(authentication.getName(), id, leido), HttpStatus.OK);
	}

}
