package com.ipn.mx.fst.utilerias;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Mide un video con ffprobe (viene con ffmpeg, que está en el PATH de la imagen del backend: ver el
// Dockerfile). Sirve para rechazar al subirlo un archivo que no se puede reproducir o que es vertical
// (sección 5 de la guía). Se corre como un proceso aparte (ProcessBuilder): no hay una biblioteca de Java
// vista en clase para leer videos, y ffprobe es el mismo programa que ya usa el worker.
@Component
public class Ffprobe {

	private static final Logger log = LoggerFactory.getLogger(Ffprobe.class);

	private static final long LIMITE_SEGUNDOS = 60;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	// Lo que se necesita saber del video. ancho y alto ya van como se ven al reproducirlo (ver giro abajo).
	public record Sonda(int ancho, int alto, double duracion) {
	}

	// Devuelve la sonda, o null si no se pudo leer: ffprobe falla, pasa del límite de tiempo, el archivo no
	// tiene una pista de video o no trae duración. Quien llama decide el mensaje al usuario.
	public Sonda sondear(Path archivo) {
		try {
			Process proceso = new ProcessBuilder("ffprobe", "-v", "error", "-select_streams", "v:0",
					"-show_entries",
					"stream=width,height:stream_side_data=rotation:stream_tags=rotate:format=duration",
					"-of", "json", archivo.toString())
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			if (!proceso.waitFor(LIMITE_SEGUNDOS, TimeUnit.SECONDS)) {
				proceso.destroyForcibly();
				log.warn("ffprobe pasó de {} s con {}", LIMITE_SEGUNDOS, archivo.getFileName());
				return null;
			}
			if (proceso.exitValue() != 0) {
				return null;
			}
			// La salida es de unos cientos de bytes: cabe en el búfer de la tubería, así que leerla después
			// de que el proceso terminó no puede trabarlo.
			JsonNode raiz = JSON.readTree(new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
			JsonNode flujo = raiz.path("streams").path(0);
			if (flujo.isMissingNode()) {
				return null;
			}

			// Los celulares guardan el video acostado con una marca de giro. Al reproducirlo se endereza, así
			// que con 90 o 270 grados el ancho y el alto se intercambian antes de decidir si es horizontal.
			int giro = flujo.path("tags").path("rotate").asInt(0);
			for (JsonNode dato : flujo.path("side_data_list")) {
				if (dato.has("rotation")) {
					giro = (int) Math.round(dato.path("rotation").asDouble());
				}
			}
			int ancho = flujo.path("width").asInt(0);
			int alto = flujo.path("height").asInt(0);
			if (Math.abs(giro) % 180 == 90) {
				int auxiliar = ancho;
				ancho = alto;
				alto = auxiliar;
			}

			double duracion = Double.parseDouble(raiz.path("format").path("duration").asText(""));
			if (Double.isNaN(duracion) || Double.isInfinite(duracion)) {
				return null;
			}
			return new Sonda(ancho, alto, duracion);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException | JacksonException | NumberFormatException e) {
			log.warn("No se pudo medir {} con ffprobe: {}", archivo.getFileName(), e.getMessage());
			return null;
		}
	}

}
