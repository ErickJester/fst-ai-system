package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.utilerias.ErrorApi;
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Manejo de errores global: cubre todos los errores de la API y los convierte en el envoltorio
// {"data", "error", "status"} (ver Respuesta). Reemplaza los mapas {mensaje, error} que cada controller de
// la práctica armaba a mano con try/catch: ahora los controllers no atrapan nada.
// Los 500 nunca mandan al cliente el mensaje de la excepción (puede traer SQL o rutas del servidor);
// ese va solo al log.
@RestControllerAdvice
public class ManejadorErrores {

	private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

	@Value("${MAX_UPLOAD_MB:2048}")
	private long maxUploadMb;

	// Regla de negocio rechazada por un servicio (404, 409...).
	@ExceptionHandler(ErrorApi.class)
	public ResponseEntity<Map<String, Object>> reglaDeNegocio(ErrorApi e) {
		return Respuesta.error(e.getMessage(), e.getStatus());
	}

	// Falló @Valid en el cuerpo. Un mensaje por campo: "El campo 'nombre' no debe estar vacío".
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> datosInvalidos(MethodArgumentNotValidException e) {
		String mensaje = e.getBindingResult().getFieldErrors()
				.stream()
				.map(err -> "El campo '" + err.getField() + "' " + err.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return Respuesta.error(mensaje, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException e) {
		return Respuesta.error("El cuerpo de la petición no es un JSON válido", HttpStatus.BAD_REQUEST);
	}

	// Por ejemplo /experiments/abc, donde el id debe ser un número.
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> parametroInvalido(MethodArgumentTypeMismatchException e) {
		return Respuesta.error("El parámetro '" + e.getName() + "' no tiene un valor válido", HttpStatus.BAD_REQUEST);
	}

	// Un parámetro de la URL (o del formulario) con bytes que no son UTF-8, por ejemplo ?q=s%E1lina: Tomcat
	// lo rechaza y, sin este handler, saldría como 500. Es un error del cliente.
	@ExceptionHandler(InvalidParameterException.class)
	public ResponseEntity<Map<String, Object>> codificacionInvalida(InvalidParameterException e) {
		return Respuesta.error("Parámetro con codificación no válida", HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Map<String, Object>> rutaInexistente(NoResourceFoundException e) {
		return Respuesta.error("La ruta no existe", HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
		return Respuesta.error("Método no permitido en esta ruta", HttpStatus.METHOD_NOT_ALLOWED);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> tipoNoSoportado(HttpMediaTypeNotSupportedException e) {
		return Respuesta.error("Tipo de contenido no soportado", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	// Se lanza cuando el archivo (o la petición) pasa del límite de spring.servlet.multipart.*
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> archivoMuyGrande(MaxUploadSizeExceededException e) {
		return Respuesta.error("El archivo excede el límite de " + maxUploadMb + " MB", HttpStatus.PAYLOAD_TOO_LARGE);
	}

	// Llave única o foránea violada que ningún servicio revisó antes (la base es la última defensa).
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> choqueDeDatos(DataIntegrityViolationException e) {
		log.warn("Violación de integridad: {}", e.getMostSpecificCause().getMessage());
		return Respuesta.error("La operación choca con datos que ya existen", HttpStatus.CONFLICT);
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<Map<String, Object>> errorDeBase(DataAccessException e) {
		log.error("Error de la base de datos", e);
		return Respuesta.error("Error de la base de datos", HttpStatus.INTERNAL_SERVER_ERROR);
	}

	// Los filtros de seguridad responden solos (SeguridadConfig), pero una excepción de seguridad lanzada desde
	// un controller o un servicio llegaría al catch-all de abajo y saldría como 500. Mismos textos que
	// SeguridadConfig.
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> sinPermiso(AccessDeniedException e) {
		return Respuesta.error("No tienes permiso para esta acción", HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<Map<String, Object>> sesionNoValida(AuthenticationException e) {
		return Respuesta.error("Sesión no válida o vencida", HttpStatus.UNAUTHORIZED);
	}

	// Lo que ningún handler de arriba atrapó.
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> errorInterno(Exception e) {
		log.error("Error interno del servidor", e);
		return Respuesta.error("Error interno del servidor", HttpStatus.INTERNAL_SERVER_ERROR);
	}

}
