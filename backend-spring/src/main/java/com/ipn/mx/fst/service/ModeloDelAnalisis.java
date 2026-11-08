package com.ipn.mx.fst.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Con qué modelo se hizo un análisis (el bloque «modelo» de tiempos.json que anota el worker en modo desarrollador),
// escrito igual en el PDF del experimento y en los reportes CSV y XLSX. Es una clase pura: no toca la base ni el volumen.
final class ModeloDelAnalisis {

	// Lo que se muestra de un análisis de antes de que se anotara el modelo, o sin modo desarrollador al hacerlo.
	static final String SIN_REGISTRO = "sin registro";

	// Cuántos caracteres de la huella (SHA-256) se muestran: 16 hex son 64 bits, de sobra para reconocer el archivo.
	private static final int LARGO_DE_HUELLA = 16;

	private ModeloDelAnalisis() {
	}

	// «fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5 · 119.6 MB · archivo del 2026-10-19». Lo que no se sabe se omite.
	static String texto(Map<String, Object> modelo) {
		if (modelo == null) {
			return SIN_REGISTRO;
		}
		List<String> partes = new ArrayList<>();
		partes.add(modelo.get("archivo") != null ? modelo.get("archivo").toString() : "modelo desconocido");
		if (modelo.get("version") != null) {
			partes.add("red " + modelo.get("version"));
		}
		if (huella(modelo) != null) {
			partes.add("SHA-256 " + huella(modelo));
		}
		if (modelo.get("tamanoMB") != null) {
			partes.add(modelo.get("tamanoMB") + " MB");
		}
		if (fecha(modelo) != null) {
			partes.add("archivo del " + fecha(modelo));
		}
		return String.join(" · ", partes);
	}

	// Los primeros caracteres del SHA-256, o null si no hay.
	static String huella(Map<String, Object> modelo) {
		Object sha = modelo == null ? null : modelo.get("sha256");
		if (sha == null || sha.toString().isBlank()) {
			return null;
		}
		String texto = sha.toString();
		return texto.length() <= LARGO_DE_HUELLA ? texto : texto.substring(0, LARGO_DE_HUELLA);
	}

	// Solo el día del archivo («2026-10-19») de la fecha con hora y zona que anota el worker, o null si no hay.
	static String fecha(Map<String, Object> modelo) {
		Object modificado = modelo == null ? null : modelo.get("modificado");
		if (modificado == null || modificado.toString().isBlank()) {
			return null;
		}
		String texto = modificado.toString();
		return texto.length() <= 10 ? texto : texto.substring(0, 10);
	}

}
