// Carpeta lógica de esta clase: com/ipn/mx/fst/utilerias.
package com.ipn.mx.fst.utilerias;

// IOException: error al leer o escribir (aquí, al hablar con el proceso de ffprobe).
import java.io.IOException;
// StandardCharsets: para interpretar la salida de ffprobe como UTF-8 y no como la codificación del sistema.
import java.nio.charset.StandardCharsets;
// Path: una ruta de archivo. Es la forma moderna de nombrar archivos en Java, mejor que File.
import java.nio.file.Path;
// TimeUnit: la unidad de un plazo (SECONDS, MILLISECONDS...). Se usa al poner el límite de espera.
import java.util.concurrent.TimeUnit;
// El registro (log).
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// @Component: Spring crea esta clase y la inyecta donde se pida (la usa VideoServiceImpl).
import org.springframework.stereotype.Component;
// Las clases de Jackson 3 para leer JSON. Fíjate en el paquete: "tools.jackson", no "com.fasterxml.jackson".
// Ese cambio de paquete es de Jackson 3, el que trae Spring Boot 4, y es justo el motivo que da el pom.xml
// para no usar la biblioteca jjwt (que arrastra Jackson 2).
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Mide un video con ffprobe (viene con ffmpeg, que está en el PATH de la imagen del backend: ver el
// Dockerfile). Sirve para rechazar al subirlo un archivo que no se puede reproducir o que es vertical
// (sección 5 de la guía). Se corre como un proceso aparte (ProcessBuilder): no hay una biblioteca de Java
// vista en clase para leer videos, y ffprobe es el mismo programa que ya usa el worker.
//
// Qué es ffprobe: un programa de línea de comandos que lee un archivo de video y dice sus características
// (dimensiones, duración, códec) sin reproducirlo. Viene junto con ffmpeg.
@Component
public class Ffprobe {

	private static final Logger log = LoggerFactory.getLogger(Ffprobe.class);

	// Cuánto se espera a ffprobe antes de darlo por colgado. Un archivo corrupto puede hacer que el
	// programa se quede dando vueltas; sin este límite, la petición de subir el video no terminaría nunca.
	private static final long LIMITE_SEGUNDOS = 60;
	// El lector de JSON, uno solo para toda la clase. static final porque construirlo es caro y es seguro
	// usarlo desde varios hilos a la vez.
	private static final JsonMapper JSON = JsonMapper.builder().build();

	// Lo que se necesita saber del video. ancho y alto ya van como se ven al reproducirlo (ver giro abajo).
	//
	// Un "record" es una clase de solo datos: Java genera solo el constructor, los getters (que se llaman
	// ancho(), alto() y duracion(), sin "get"), equals, hashCode y toString. Sus campos son inmutables.
	// Es el sustituto moderno de escribir una clase con tres campos y quince líneas de código repetido.
	public record Sonda(int ancho, int alto, double duracion) {
	}

	// Devuelve la sonda, o null si no se pudo leer: ffprobe falla, pasa del límite de tiempo, el archivo no
	// tiene una pista de video o no trae duración. Quien llama decide el mensaje al usuario.
	//
	// Devolver null y no lanzar excepción es deliberado: "este archivo no sirve" no es un error del
	// programa, es un resultado posible y esperado al dejar que el usuario suba lo que quiera.
	public Sonda sondear(Path archivo) {
		try {
			// ProcessBuilder lanza un programa externo. Cada argumento va en su propio texto, no en una
			// sola línea: así no hay que preocuparse por los espacios del nombre del archivo ni existe el
			// riesgo de que un nombre malicioso se cuele como un comando más.
			Process proceso = new ProcessBuilder("ffprobe",
					// -v error: que no imprima nada salvo errores de verdad.
					"-v", "error",
					// -select_streams v:0: solo la PRIMERA pista de video (v:0). Un archivo puede traer
					// varias pistas de audio y subtítulos que aquí no importan.
					"-select_streams", "v:0",
					// -show_entries: exactamente qué datos se quieren, y nada más.
					"-show_entries",
					// width y height (dimensiones), la rotación en sus dos formatos posibles, y la duración.
					"stream=width,height:stream_side_data=rotation:stream_tags=rotate:format=duration",
					// -of json: que la salida sea JSON, para leerla con Jackson en vez de con texto a pelo.
					"-of", "json", archivo.toString())
					// DISCARD: tira lo que ffprobe escriba en la salida de errores. Si no se leyera ni se
					// descartara, el búfer podría llenarse y dejar el proceso trabado para siempre.
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			// waitFor con plazo devuelve false si el proceso NO terminó a tiempo.
			if (!proceso.waitFor(LIMITE_SEGUNDOS, TimeUnit.SECONDS)) {
				// destroyForcibly: lo mata. Sin esto quedaría un proceso huérfano consumiendo CPU por cada
				// video corrupto que alguien suba.
				proceso.destroyForcibly();
				log.warn("ffprobe pasó de {} s con {}", LIMITE_SEGUNDOS, archivo.getFileName());
				return null;
			}
			// Código de salida distinto de 0 = el programa falló (archivo ilegible o que no es video).
			// Por convención de Unix, 0 significa éxito.
			if (proceso.exitValue() != 0) {
				return null;
			}
			// La salida es de unos cientos de bytes: cabe en el búfer de la tubería, así que leerla después
			// de que el proceso terminó no puede trabarlo.
			//
			// La advertencia importa: con una salida grande, esperar primero y leer después sí puede
			// trabarse, porque el proceso hijo se queda bloqueado al llenarse el búfer y nunca termina.
			// Aquí se puede hacer así porque -show_entries limita la salida a unos pocos campos.
			// getInputStream() es la salida ESTÁNDAR del proceso hijo (se llama "input" desde el punto de
			// vista de Java, que la recibe).
			JsonNode raiz = JSON.readTree(new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
			// path() navega el JSON sin tronar: si la clave no existe devuelve un nodo "ausente" en vez de
			// null. Por eso se encadena sin comprobar cada paso. streams[0] es la primera pista de video.
			JsonNode flujo = raiz.path("streams").path(0);
			// Si no hay ni una pista de video, el archivo puede ser un audio o un documento renombrado a .mp4.
			if (flujo.isMissingNode()) {
				return null;
			}

			// Los celulares guardan el video acostado con una marca de giro. Al reproducirlo se endereza, así
			// que con 90 o 270 grados el ancho y el alto se intercambian antes de decidir si es horizontal.
			//
			// Sin esto, un video grabado con el celular horizontal se guardaría como 1080x1920 (vertical) con
			// una marca de giro, y el sistema lo rechazaría por vertical aunque se vea horizontal.
			// asInt(0): si la clave no está, vale 0 (sin giro).
			int giro = flujo.path("tags").path("rotate").asInt(0);
			// La rotación viene en dos sitios distintos según cómo se grabó: la etiqueta "rotate" (formato
			// viejo) o la lista side_data_list (formato nuevo). Este bucle busca la segunda y, si la
			// encuentra, pisa lo que se leyó arriba, porque es la que mandan los archivos modernos.
			for (JsonNode dato : flujo.path("side_data_list")) {
				if (dato.has("rotation")) {
					// Se lee como decimal y se redondea: ffprobe puede dar -90.0 en vez de -90.
					giro = (int) Math.round(dato.path("rotation").asDouble());
				}
			}
			int ancho = flujo.path("width").asInt(0);
			int alto = flujo.path("height").asInt(0);
			// Math.abs porque el giro puede venir negativo (-90 es lo mismo que 270 para este cálculo).
			// % 180 == 90 es cierto para 90 y 270, y falso para 0 y 180: justo los casos en que las
			// dimensiones se intercambian al reproducir.
			if (Math.abs(giro) % 180 == 90) {
				// El intercambio clásico con una variable auxiliar.
				int auxiliar = ancho;
				ancho = alto;
				alto = auxiliar;
			}

			// La duración viene como texto dentro de "format". asText("") da "" si no está, y entonces
			// parseDouble lanza NumberFormatException, que se atrapa abajo. Es intencional: tratar la
			// ausencia de duración como el mismo caso que una duración ilegible.
			double duracion = Double.parseDouble(raiz.path("format").path("duration").asText(""));
			// NaN ("no es un número") e infinito son valores válidos de double que no sirven como duración.
			// Un archivo con la cabecera dañada puede producirlos, y colarlos reventaría los cálculos después.
			if (Double.isNaN(duracion) || Double.isInfinite(duracion)) {
				return null;
			}
			return new Sonda(ancho, alto, duracion);
		} catch (InterruptedException e) {
			// Interrupción: alguien está parando este hilo (el servidor se está apagando, por ejemplo).
			// Thread.currentThread().interrupt() vuelve a poner la marca de interrupción, que el catch borró.
			// Es obligatorio hacerlo: si no, el código de arriba nunca se entera de que debe detenerse.
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException | JacksonException | NumberFormatException e) {
			// Las tres se atrapan juntas (el | del multi-catch) porque llevan a la misma respuesta: no se
			// pudo medir. IOException = falló el proceso; JacksonException = la salida no era JSON válido;
			// NumberFormatException = la duración no era un número.
			log.warn("No se pudo medir {} con ffprobe: {}", archivo.getFileName(), e.getMessage());
			return null;
		}
	}

}
