package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoint de salud: responde si el backend está arriba. Sirve para probar que docker compose levantó todo.
// Es público (no pide token) y usa el mismo envoltorio {data, error, status} que el resto de la API.
// También dice si el sistema corre en modo desarrollador y qué versión es: la web lo pregunta UNA vez al cargar
// y con eso muestra u oculta las herramientas de desarrollo, así que cambiar MODO_DESARROLLADOR y reiniciar la
// api basta, sin volver a compilar la web.
@RestController
public class HealthController {

	@Value("${app.modo-desarrollador:false}")
	private boolean modoDesarrollador;

	// La escribe tools/version.py en version.properties (ver application.properties).
	@Value("${app.version:desconocida}")
	private String version;

	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("estado", "ok");
		datos.put("modoDesarrollador", modoDesarrollador);
		datos.put("version", version);
		return Respuesta.ok(datos, HttpStatus.OK);
	}

}
