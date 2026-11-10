// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// TypeReference: sirve para decirle a Jackson el tipo genérico exacto que se quiere (ver abajo).
import com.fasterxml.jackson.core.type.TypeReference;
// JsonMapper: el lector de JSON.
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ipn.mx.fst.utilerias.ErrorApi;
import java.io.IOException;
// Files y Path: comprobar y leer archivos del volumen.
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

// Con qué modelo analiza el worker, para la pantalla de carga de video (modo desarrollador). El worker lo escribe en
// modelo-actual.json, en el volumen de archivos que comparten las dos. Aquí solo se lee: sin modo desarrollador responde
// 404, como la salida del modelo; si el worker todavía no escribió el archivo, o no se puede leer, dice por qué.
//
// Es otro ejemplo del segundo canal entre backend y worker, el mismo que usa la pausa: además de la base,
// los dos programas se comunican por archivos del volumen compartido. Aquí el worker deja un JSON diciendo
// qué modelo tiene cargado, y el backend lo lee sin más.
// Fíjate en el criterio de errores: cualquier problema al leer el archivo devuelve "todavía no informó",
// NO un 500. Es información de apoyo para desarrollo, y que falte no es motivo para que la pantalla de
// subir video deje de funcionar.
@Service
public class ModeloActualDeDesarrollo {

	// El nombre del archivo, en una constante porque las pruebas lo usan para crear uno falso.
	static final String ARCHIVO = "modelo-actual.json";
	static final String MOTIVO_SIN_INFORME = "El worker todavía no informó qué modelo usa.";

	// Un lector de JSON para toda la clase: construirlo es caro y se puede compartir entre hilos.
	private static final JsonMapper JSON = JsonMapper.builder().build();

	// Raíz del volumen, la misma que usan los reportes y los archivos del worker (app.archivos.raiz).
	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	@Value("${app.modo-desarrollador:false}")
	private boolean modoDesarrollador;

	public Map<String, Object> modeloActual() {
		// 404 y no 403: así la ruta no delata que existe en una instalación normal (ver DevModeloController).
		if (!modoDesarrollador) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "No encontrado");
		}
		// toAbsolutePath().normalize() deja la ruta en forma canónica, resolviendo los ".." y las
		// diagonales repetidas. Aquí la raíz viene de configuración y no del usuario, así que es higiene
		// más que defensa, pero es la costumbre correcta al armar rutas.
		Path archivo = Path.of(raizConfigurada).toAbsolutePath().normalize().resolve(ARCHIVO);
		// isRegularFile comprueba a la vez que exista y que sea un archivo (no una carpeta).
		// Es lo normal mientras el worker no haya arrancado todavía.
		if (!Files.isRegularFile(archivo)) {
			return sinInforme();
		}
		Map<String, Object> modelo;
		try {
			// new TypeReference<Map<String, Object>>() {} con las llaves al final crea una subclase
			// anónima. Es el truco clásico para conservar el tipo genérico, que Java borra al compilar:
			// sin él, Jackson no sabría que se quiere un Map<String, Object>.
			modelo = JSON.readValue(archivo.toFile(), new TypeReference<Map<String, Object>>() {
			});
		} catch (IOException | RuntimeException e) {
			// Se atrapa hasta RuntimeException, que es más de lo que se suele hacer. Aquí está
			// justificado: el archivo lo escribe otro programa y puede pillarse a medio escribir, con
			// JSON incompleto. Que eso rompa la pantalla sería peor que no enseñar el dato.
			return sinInforme();
		}
		// Un archivo con el texto "null" se lee sin error y devuelve null. Caso raro, pero cubierto.
		if (modelo == null) {
			return sinInforme();
		}
		// Se copia el mapa en vez de modificar el leído: no se devuelve al llamador la misma estructura
		// que salió del archivo, y el LinkedHashMap conserva el orden de las claves del JSON original.
		Map<String, Object> salida = new LinkedHashMap<>(modelo);
		// Se añade la línea ya redactada, con el MISMO formato que usan el PDF y los reportes. Así la
		// pantalla no tiene que armarla por su cuenta y arriesgarse a escribirla distinto.
		if (salida.get("archivo") != null) {
			salida.put("texto", ModeloDelAnalisis.texto(salida));
		}
		return salida;
	}

	// La respuesta cuando no se sabe nada. Devuelve 200 con el motivo dentro, no un error: la pantalla
	// enseña "el worker todavía no informó" y sigue funcionando.
	// archivo = null es lo que mira el cliente para saber que no hay dato.
	private static Map<String, Object> sinInforme() {
		Map<String, Object> salida = new LinkedHashMap<>();
		salida.put("archivo", null);
		salida.put("motivo", MOTIVO_SIN_INFORME);
		return salida;
	}
}
