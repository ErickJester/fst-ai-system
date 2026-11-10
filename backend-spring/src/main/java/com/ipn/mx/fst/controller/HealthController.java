// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// El envoltorio {data, error, status} que usa toda respuesta JSON de la API.
import com.ipn.mx.fst.utilerias.Respuesta;
// LinkedHashMap: mapa que conserva el orden de inserción, para que el JSON salga ordenado.
import java.util.LinkedHashMap;
import java.util.Map;
// @Value: inyecta una propiedad de application.properties.
import org.springframework.beans.factory.annotation.Value;
// HttpStatus: el enumerado con los códigos HTTP. ResponseEntity: la respuesta completa.
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// @GetMapping: enlaza un método con una ruta a la que se llega por GET.
import org.springframework.web.bind.annotation.GetMapping;
// @RestController: esta clase atiende peticiones HTTP y lo que devuelven sus métodos se convierte en JSON.
import org.springframework.web.bind.annotation.RestController;

// Endpoint de salud: responde si el backend está arriba. Sirve para probar que docker compose levantó todo.
// Es público (no pide token) y usa el mismo envoltorio {data, error, status} que el resto de la API.
// También dice si el sistema corre en modo desarrollador y qué versión es: la web lo pregunta UNA vez al cargar
// y con eso muestra u oculta las herramientas de desarrollo, así que cambiar MODO_DESARROLLADOR y reiniciar la
// api basta, sin volver a compilar la web.
//
// Qué es un controller: la capa que recibe las peticiones HTTP, saca los datos de la URL y del cuerpo, se
// los pasa al servicio y devuelve la respuesta. No lleva reglas de negocio: esas viven en el servicio.
// @RestController es @Controller + @ResponseBody: lo que devuelve cada método se serializa a JSON en vez
// de interpretarse como el nombre de una página que haya que pintar.
//
// Este endpoint tiene dos usos distintos, y conviene no confundirlos:
//   1. El healthcheck de Docker, que lo llama cada 15 segundos para saber si el contenedor sigue sano
//      (ver el healthcheck del compose, que usa curl contra esta ruta).
//   2. El frontend, que lo consulta al cargar cualquier pantalla para conocer la versión y el modo.
// Ese segundo uso es el motivo de que FiltroCambioPendiente lo deje pasar aun con contraseña temporal.
@RestController
public class HealthController {

	// El :false del final es el valor por omisión, para que arranque aunque la propiedad no esté definida.
	// Sale de MODO_DESARROLLADOR (ver application.properties).
	@Value("${app.modo-desarrollador:false}")
	private boolean modoDesarrollador;

	// La escribe tools/version.py en version.properties (ver application.properties).
	// El valor por omisión "desconocida" cubre el caso de ejecutar sin ese archivo, por ejemplo al correr
	// las pruebas o desde el IDE sin empaquetar.
	@Value("${app.version:desconocida}")
	private String version;

	// @GetMapping("/health"): responde a GET /health. Es una de las rutas que SeguridadConfig declara
	// permitAll, así que funciona sin token.
	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		// Se arma el mapa a mano en vez de usar una clase: son tres campos fijos y no merece un DTO.
		// LinkedHashMap para que el JSON salga en este orden y no en uno arbitrario.
		Map<String, Object> datos = new LinkedHashMap<>();
		// "estado" es constante: si el backend puede responder, está vivo. No comprueba la base a
		// propósito: si la base estuviera caída, el arranque ya habría fallado (ddl-auto=validate).
		datos.put("estado", "ok");
		datos.put("modoDesarrollador", modoDesarrollador);
		datos.put("version", version);
		// Respuesta.ok envuelve los datos en {data, error, status} y fija el código HTTP 200.
		return Respuesta.ok(datos, HttpStatus.OK);
	}

}
