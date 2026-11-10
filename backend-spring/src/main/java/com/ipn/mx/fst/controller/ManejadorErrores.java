// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// La excepción propia que lanzan los servicios, con su código HTTP dentro.
import com.ipn.mx.fst.utilerias.ErrorApi;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
// Collectors: utilidades para recoger el resultado de un stream (aquí, juntar textos).
import java.util.stream.Collectors;
// InvalidParameterException de Tomcat (no la de java.security): parámetro con bytes que no son UTF-8.
import org.apache.tomcat.util.http.InvalidParameterException;
// El registro (log): los errores internos se apuntan aquí y NO se le mandan al cliente.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// @Value: inyecta una propiedad (aquí, el límite de subida, para poder nombrarlo en el mensaje).
import org.springframework.beans.factory.annotation.Value;
// Las excepciones de acceso a datos de Spring: la general y la de integridad (llave única o foránea).
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// Cuerpo que no se puede leer: JSON mal formado.
import org.springframework.http.converter.HttpMessageNotReadableException;
// Las dos excepciones de seguridad: sin permiso y sin autenticar.
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
// Tipo de contenido no soportado y verbo no permitido en la ruta.
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
// Falló la validación de @Valid sobre el cuerpo de la petición.
import org.springframework.web.bind.MethodArgumentNotValidException;
// @ExceptionHandler: marca el método que atiende un tipo de excepción.
import org.springframework.web.bind.annotation.ExceptionHandler;
// @RestControllerAdvice: aplica estos manejadores a TODOS los controllers de la aplicación.
import org.springframework.web.bind.annotation.RestControllerAdvice;
// Un parámetro de la URL que no se puede convertir al tipo que pide el método (/experiments/abc).
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
// El archivo subido pasa del límite configurado.
import org.springframework.web.multipart.MaxUploadSizeExceededException;
// La ruta pedida no existe.
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Manejo de errores global: cubre todos los errores de la API y los convierte en el envoltorio
// {"data", "error", "status"} (ver Respuesta). Reemplaza los mapas {mensaje, error} que cada controller de
// la práctica armaba a mano con try/catch: ahora los controllers no atrapan nada.
// Los 500 nunca mandan al cliente el mensaje de la excepción (puede traer SQL o rutas del servidor);
// ese va solo al log.
//
// Esta clase es la contraparte de ErrorApi y, juntas, son la razón de que los controllers del proyecto se
// lean tan limpios: ni un try/catch, ni un BindingResult, ni un if comprobando valores de retorno.
// Cómo funciona: cuando una excepción sube por un controller sin que nadie la atrape, Spring busca aquí un
// método @ExceptionHandler cuyo tipo case con ella, y usa el MÁS ESPECÍFICO que encuentre. Por eso el
// manejador de Exception del final solo recoge lo que ninguno de los de arriba cubrió.
// La regla de no filtrar el mensaje en los 500 es de seguridad: el texto de una excepción de base de datos
// puede traer nombres de tabla, fragmentos de SQL o rutas del servidor, y eso es información útil para
// quien quiera atacar el sistema.
@RestControllerAdvice
public class ManejadorErrores {

	private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

	// El mismo valor que usa spring.servlet.multipart.max-file-size, para poder decirlo en el mensaje.
	// Se lee la variable de entorno directa, no la propiedad de Spring, que ya viene con "MB" pegado.
	@Value("${MAX_UPLOAD_MB:2048}")
	private long maxUploadMb;

	// Regla de negocio rechazada por un servicio (404, 409...).
	//
	// Es el manejador que más se usa. La excepción YA trae el código y el mensaje que decidió el servicio,
	// así que aquí solo se desempaqueta. No se registra en el log: no es un fallo del sistema, es el
	// sistema funcionando (un nombre repetido, un id que no existe).
	@ExceptionHandler(ErrorApi.class)
	public ResponseEntity<Map<String, Object>> reglaDeNegocio(ErrorApi e) {
		return Respuesta.error(e.getMessage(), e.getStatus());
	}

	// Falló @Valid en el cuerpo. Un mensaje por campo: "El campo 'nombre' no debe estar vacío".
	//
	// Aquí se ve por qué los mensajes de los DTO están redactados como están: se concatenan DETRÁS del
	// nombre del campo. De ahí que digan "no debe estar vacío" y no "El nombre no debe estar vacío".
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> datosInvalidos(MethodArgumentNotValidException e) {
		// getBindingResult() trae el resultado de la validación; getFieldErrors(), un error por campo.
		String mensaje = e.getBindingResult().getFieldErrors()
				.stream()
				// Por cada error se arma la frase con el nombre del campo y su mensaje.
				.map(err -> "El campo '" + err.getField() + "' " + err.getDefaultMessage())
				// Se juntan TODOS en un solo texto separado por "; ": el usuario ve de una vez todo lo que
				// está mal, en lugar de corregir un campo y descubrir el siguiente error.
				.collect(Collectors.joining("; "));
		return Respuesta.error(mensaje, HttpStatus.BAD_REQUEST);
	}

	// El cuerpo no es JSON válido (una coma de más, una llave sin cerrar). El mensaje es fijo: el de la
	// excepción traería la posición exacta del fallo y detalles internos de Jackson que no aportan nada.
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException e) {
		return Respuesta.error("El cuerpo de la petición no es un JSON válido", HttpStatus.BAD_REQUEST);
	}

	// Por ejemplo /experiments/abc, donde el id debe ser un número.
	// e.getName() da el nombre del parámetro, para que el mensaje diga cuál de ellos viene mal.
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> parametroInvalido(MethodArgumentTypeMismatchException e) {
		return Respuesta.error("El parámetro '" + e.getName() + "' no tiene un valor válido", HttpStatus.BAD_REQUEST);
	}

	// Un parámetro de la URL (o del formulario) con bytes que no son UTF-8, por ejemplo ?q=s%E1lina: Tomcat
	// lo rechaza y, sin este handler, saldría como 500. Es un error del cliente.
	//
	// El ejemplo es real: %E1 es la "á" en Latin-1, no en UTF-8. Lo manda un cliente viejo o una URL
	// escrita a mano. Sin este manejador caería en el catch-all y se reportaría como fallo del servidor,
	// cuando la culpa es de quien llama.
	@ExceptionHandler(InvalidParameterException.class)
	public ResponseEntity<Map<String, Object>> codificacionInvalida(InvalidParameterException e) {
		return Respuesta.error("Parámetro con codificación no válida", HttpStatus.BAD_REQUEST);
	}

	// Una ruta que no existe. Se atrapa para que también el 404 salga con el envoltorio, en vez de con la
	// página de error por omisión de Tomcat, que es HTML.
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Map<String, Object>> rutaInexistente(NoResourceFoundException e) {
		return Respuesta.error("La ruta no existe", HttpStatus.NOT_FOUND);
	}

	// La ruta existe pero no con ese verbo (un POST donde solo hay GET). 405.
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
		return Respuesta.error("Método no permitido en esta ruta", HttpStatus.METHOD_NOT_ALLOWED);
	}

	// El Content-Type no es el que espera la ruta (texto plano donde se pide JSON). 415.
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> tipoNoSoportado(HttpMediaTypeNotSupportedException e) {
		return Respuesta.error("Tipo de contenido no soportado", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	// Se lanza cuando el archivo (o la petición) pasa del límite de spring.servlet.multipart.*
	//
	// 413 (PAYLOAD_TOO_LARGE) es el código correcto para esto. Recuerda la nota de
	// application.properties: sin server.tomcat.max-swallow-size=-1, Tomcat corta la conexión antes de
	// que este 413 llegue a viajar, y el usuario solo ve un error de red.
	// El mensaje nombra el límite para que quien suba el archivo sepa a qué atenerse.
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> archivoMuyGrande(MaxUploadSizeExceededException e) {
		return Respuesta.error("El archivo excede el límite de " + maxUploadMb + " MB", HttpStatus.PAYLOAD_TOO_LARGE);
	}

	// Llave única o foránea violada que ningún servicio revisó antes (la base es la última defensa).
	//
	// "La última defensa" resume bien la estrategia: los servicios comprueban antes para dar mensajes
	// claros, pero entre la comprobación y el INSERT puede colarse otra petición simultánea. La
	// restricción de la base siempre gana, y esto traduce su error a un 409 presentable.
	// La causa real va al log (en nivel warn) y no al cliente: traería el nombre de la restricción.
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> choqueDeDatos(DataIntegrityViolationException e) {
		// getMostSpecificCause() baja por la cadena de excepciones hasta la original de PostgreSQL, que
		// es la única que dice algo útil.
		log.warn("Violación de integridad: {}", e.getMostSpecificCause().getMessage());
		return Respuesta.error("La operación choca con datos que ya existen", HttpStatus.CONFLICT);
	}

	// Cualquier otro fallo de la base (conexión caída, tiempo agotado). Es un 500 de verdad: la excepción
	// ENTERA va al log, con su rastro de pila, porque alguien tiene que mirarlo.
	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<Map<String, Object>> errorDeBase(DataAccessException e) {
		log.error("Error de la base de datos", e);
		return Respuesta.error("Error de la base de datos", HttpStatus.INTERNAL_SERVER_ERROR);
	}

	// Los filtros de seguridad responden solos (SeguridadConfig), pero una excepción de seguridad lanzada desde
	// un controller o un servicio llegaría al catch-all de abajo y saldría como 500. Mismos textos que
	// SeguridadConfig.
	//
	// Es un detalle fino: los filtros corren ANTES que los controllers, así que esta clase no los ve. Pero
	// si un servicio lanza AccessDeniedException a media petición, esa sí pasa por aquí. Los textos se
	// repiten literalmente para que el cliente reciba siempre lo mismo, venga de donde venga el rechazo.
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> sinPermiso(AccessDeniedException e) {
		return Respuesta.error("No tienes permiso para esta acción", HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<Map<String, Object>> sesionNoValida(AuthenticationException e) {
		return Respuesta.error("Sesión no válida o vencida", HttpStatus.UNAUTHORIZED);
	}

	// Lo que ningún handler de arriba atrapó.
	//
	// La red de seguridad. Al ser Exception, el tipo más general, Spring solo lo usa cuando ningún otro
	// manejador encaja. Garantiza que NINGÚN error salga de la API sin el envoltorio.
	// El mensaje al cliente es genérico a propósito; el detalle, con rastro de pila completo, va al log.
	// Si un error aparece aquí es, casi siempre, señal de que falta un manejador propio o una validación.
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> errorInterno(Exception e) {
		log.error("Error interno del servidor", e);
		return Respuesta.error("Error interno del servidor", HttpStatus.INTERNAL_SERVER_ERROR);
	}

}
