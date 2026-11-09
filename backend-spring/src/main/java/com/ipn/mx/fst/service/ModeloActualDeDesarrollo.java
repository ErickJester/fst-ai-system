package com.ipn.mx.fst.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ipn.mx.fst.utilerias.ErrorApi;
import java.io.IOException;
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
@Service
public class ModeloActualDeDesarrollo {

	static final String ARCHIVO = "modelo-actual.json";
	static final String MOTIVO_SIN_INFORME = "El worker todavía no informó qué modelo usa.";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	// Raíz del volumen, la misma que usan los reportes y los archivos del worker (app.archivos.raiz).
	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	@Value("${app.modo-desarrollador:false}")
	private boolean modoDesarrollador;

	public Map<String, Object> modeloActual() {
		if (!modoDesarrollador) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "No encontrado");
		}
		Path archivo = Path.of(raizConfigurada).toAbsolutePath().normalize().resolve(ARCHIVO);
		if (!Files.isRegularFile(archivo)) {
			return sinInforme();
		}
		Map<String, Object> modelo;
		try {
			modelo = JSON.readValue(archivo.toFile(), new TypeReference<Map<String, Object>>() {
			});
		} catch (IOException | RuntimeException e) {
			return sinInforme();
		}
		if (modelo == null) {
			return sinInforme();
		}
		Map<String, Object> salida = new LinkedHashMap<>(modelo);
		if (salida.get("archivo") != null) {
			salida.put("texto", ModeloDelAnalisis.texto(salida));
		}
		return salida;
	}

	private static Map<String, Object> sinInforme() {
		Map<String, Object> salida = new LinkedHashMap<>();
		salida.put("archivo", null);
		salida.put("motivo", MOTIVO_SIN_INFORME);
		return salida;
	}
}
