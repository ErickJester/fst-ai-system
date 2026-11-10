// Carpeta lógica de esta clase: com/ipn/mx/fst/utilerias.
package com.ipn.mx.fst.utilerias;

// LinkedHashMap: un mapa (pares clave-valor) que CONSERVA el orden en que se insertaron las claves.
// Un HashMap normal no lo garantiza, y aquí el orden importa (ver abajo).
import java.util.LinkedHashMap;
// Map: la interfaz de "mapa". Se declara con la interfaz y se construye con LinkedHashMap.
import java.util.Map;
// HttpStatus: el enumerado con los códigos HTTP (OK = 200, NOT_FOUND = 404...). Usarlo en vez de números
// sueltos evita escribir 204 donde iba 404.
import org.springframework.http.HttpStatus;
// ResponseEntity: la respuesta HTTP completa que devuelve un controller (código + cabeceras + cuerpo).
import org.springframework.http.ResponseEntity;

// Envoltorio de toda respuesta JSON de la API (sección 6 de la guía): {"data", "error", "status"}.
// Con éxito "error" es null; con error "data" es null y el mensaje va en "error". Solo se salta esta regla
// una respuesta que devuelve un archivo (el PDF). En la práctica cada método armaba su propio mapa; aquí
// todos usan estos dos métodos para que el frontend lea siempre la misma forma.
//
// Por qué un envoltorio: el frontend escribe UNA sola función para leer respuestas. Sin esto, cada pantalla
// tendría que adivinar si la respuesta trae el dato directo, un mensaje de error o una lista.
// final: la clase no se puede heredar. No tiene sentido extender una caja de métodos estáticos.
public final class Respuesta {

	// Constructor privado y vacío: impide escribir "new Respuesta()". Esta clase no se instancia, solo se
	// usan sus dos métodos estáticos. Sin él, Java crearía un constructor público automático.
	private Respuesta() {
	}

	// Respuesta de ÉXITO. static: se llama como Respuesta.ok(...), sin crear un objeto.
	// El status se recibe como parámetro porque un éxito puede ser 200 (OK) o 201 (creado).
	public static ResponseEntity<Map<String, Object>> ok(Object data, HttpStatus status) {
		// LinkedHashMap conserva el orden data, error, status; Map.of no acepta el null.
		// Las dos razones son reales: con HashMap el JSON saldría con las claves desordenadas (feo al
		// depurar), y Map.of("error", null) lanza NullPointerException al construirse.
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		// data es Object para que acepte cualquier cosa: una entidad, una lista, un mapa o null.
		cuerpo.put("data", data);
		// En un éxito, error siempre va en null. El frontend comprueba este campo para saber qué pasó.
		cuerpo.put("error", null);
		// El código también va DENTRO del cuerpo, además de en la respuesta HTTP. Es redundante a propósito:
		// así el frontend lo tiene a la mano sin mirar la respuesta HTTP.
		cuerpo.put("status", status.value());
		// .status(...) fija el código HTTP real; .body(...) pone el cuerpo. Los dos tienen que coincidir.
		return ResponseEntity.status(status).body(cuerpo);
	}

	// Respuesta de ERROR. La forma es la misma, con los papeles cambiados: data en null y el mensaje en error.
	// Casi nadie la llama directo: los servicios lanzan ErrorApi y ManejadorErrores acaba aquí.
	public static ResponseEntity<Map<String, Object>> error(String mensaje, HttpStatus status) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("data", null);
		cuerpo.put("error", mensaje);
		cuerpo.put("status", status.value());
		return ResponseEntity.status(status).body(cuerpo);
	}

}
