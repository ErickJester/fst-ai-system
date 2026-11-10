// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Con qué modelo se hizo un análisis (el bloque «modelo» de tiempos.json que anota el worker en modo desarrollador),
// escrito igual en el PDF del experimento y en los reportes CSV y XLSX. Es una clase pura: no toca la base ni el volumen.
//
// Para qué sirve: al comparar un modelo nuevo con el viejo hay que poder decir con cuál se midió cada
// análisis. Sin eso, dos resultados distintos del mismo video no se podrían explicar.
// "Clase pura" quiere decir que solo transforma lo que recibe: no lee archivos ni consulta la base. Por
// eso se puede probar con un mapa escrito a mano, sin levantar nada (lo hace ModeloDelAnalisisTest).
// Sin "public": solo la ve su propio paquete. Es una utilidad interna, no parte de la API del servicio.
final class ModeloDelAnalisis {

	// Lo que se muestra de un análisis de antes de que se anotara el modelo, o sin modo desarrollador al hacerlo.
	// Un texto y no null: quien lo use lo imprime tal cual, sin tener que comprobar nada.
	static final String SIN_REGISTRO = "sin registro";

	// Cuántos caracteres de la huella (SHA-256) se muestran: 16 hex son 64 bits, de sobra para reconocer el archivo.
	//
	// Un SHA-256 completo son 64 caracteres, ilegibles en una línea de un PDF. Con 16 hay 2^64
	// combinaciones: la probabilidad de que dos modelos distintos coincidan es despreciable para esto,
	// que es reconocer un archivo, no verificar su integridad.
	private static final int LARGO_DE_HUELLA = 16;

	// Impide instanciarla: es una caja de métodos estáticos.
	private ModeloDelAnalisis() {
	}

	// «fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5 · 119.6 MB · archivo del 2026-10-19». Lo que no se sabe se omite.
	//
	// "Lo que no se sabe se omite" es la regla de todo el método: en vez de escribir "versión: desconocida",
	// se deja fuera. Así la línea solo afirma lo que consta.
	static String texto(Map<String, Object> modelo) {
		// Sin bloque «modelo» no hay nada que decir. Es el caso de los análisis viejos o los hechos sin
		// modo desarrollador.
		if (modelo == null) {
			return SIN_REGISTRO;
		}
		// Se van juntando las partes que sí se conocen y al final se unen con el separador.
		List<String> partes = new ArrayList<>();
		// El nombre del archivo es la única parte que siempre sale: si falta, se dice así en vez de
		// devolver una línea que empezara por el separador.
		partes.add(modelo.get("archivo") != null ? modelo.get("archivo").toString() : "modelo desconocido");
		// Las cuatro siguientes solo se añaden si están.
		if (modelo.get("version") != null) {
			partes.add("red " + modelo.get("version"));
		}
		// huella() se llama dos veces (en el if y dentro). Es barato y se lee mejor que guardarlo en una
		// variable, porque el método no toca nada de fuera.
		if (huella(modelo) != null) {
			partes.add("SHA-256 " + huella(modelo));
		}
		if (modelo.get("tamanoMB") != null) {
			partes.add(modelo.get("tamanoMB") + " MB");
		}
		if (fecha(modelo) != null) {
			partes.add("archivo del " + fecha(modelo));
		}
		// String.join pone el separador ENTRE los elementos, nunca al principio ni al final, así que la
		// línea sale bien tanto con una parte como con cinco.
		return String.join(" · ", partes);
	}

	// Los primeros caracteres del SHA-256, o null si no hay.
	static String huella(Map<String, Object> modelo) {
		// El doble null (el mapa entero y la clave) se comprueba en una línea con el operador ternario.
		Object sha = modelo == null ? null : modelo.get("sha256");
		// isBlank cubre también el texto de solo espacios, que es lo mismo que no tener huella.
		if (sha == null || sha.toString().isBlank()) {
			return null;
		}
		String texto = sha.toString();
		// Si ya es más corto que el tope, se devuelve entero: substring con un índice mayor que el largo
		// lanzaría una excepción.
		return texto.length() <= LARGO_DE_HUELLA ? texto : texto.substring(0, LARGO_DE_HUELLA);
	}

	// Solo el día del archivo («2026-10-19») de la fecha con hora y zona que anota el worker, o null si no hay.
	static String fecha(Map<String, Object> modelo) {
		Object modificado = modelo == null ? null : modelo.get("modificado");
		if (modificado == null || modificado.toString().isBlank()) {
			return null;
		}
		String texto = modificado.toString();
		// Se corta por los diez primeros caracteres porque el formato ISO empieza por AAAA-MM-DD, que son
		// exactamente diez. La hora y la zona, que van detrás, no aportan nada aquí: lo que interesa es de
		// qué día es el archivo del modelo.
		// La misma protección que arriba: si el texto ya es más corto, se devuelve entero.
		return texto.length() <= 10 ? texto : texto.substring(0, 10);
	}

}
