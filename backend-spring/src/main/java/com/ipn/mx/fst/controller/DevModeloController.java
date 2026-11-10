// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// El servicio que averigua con qué modelo está trabajando el worker.
import com.ipn.mx.fst.service.ModeloActualDeDesarrollo;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// GET y el prefijo común de las rutas.
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// El modelo que usa el worker, para las herramientas de desarrollo. Pide sesión como el resto; sin modo desarrollador, 404.
//
// Es una ruta de apoyo, no del sistema que usa el laboratorio: sirve para comparar modelos mientras se
// desarrolla, y por eso vive bajo su propio prefijo /dev.
// El 404 sin modo desarrollador lo decide el SERVICIO, no este controller ni SeguridadConfig: la ruta
// existe siempre y exige sesión como cualquier otra (cae en el anyRequest().authenticated()), pero
// responde "no existe" cuando MODO_DESARROLLADOR está en false. Devolver 404 y no 403 es deliberado: así
// la ruta no delata que existe en una instalación normal.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/dev")
public class DevModeloController {

	@Autowired
	private ModeloActualDeDesarrollo modelo;

	// GET /dev/modelo-actual. Una línea: el controller solo llama al servicio y envuelve lo que devuelva.
	@GetMapping("/modelo-actual")
	public ResponseEntity<Map<String, Object>> modeloActual() {
		return Respuesta.ok(modelo.modeloActual(), HttpStatus.OK);
	}
}
