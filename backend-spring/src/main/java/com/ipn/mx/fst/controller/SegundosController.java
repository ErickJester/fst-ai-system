// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// El servicio que arma la lista de segundos de una tanda.
import com.ipn.mx.fst.service.SegundosDeResultados;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// GET, la variable de la ruta, los parámetros de consulta y el prefijo común.
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Los segundos de una tanda, uno por uno, para la pantalla segundo a segundo. Lectura para cualquier usuario autenticado,
// como los demás resultados. Con envoltorio {data, error, status}.
//
// Es la pantalla que más le importa al investigador: ver el detalle segundo a segundo de cada espécimen,
// no solo los totales. Sale directo de la tabla segundo, con las correcciones ya aplicadas.
// Fíjate en que comparte el prefijo /experiments con ExperimentoController: dos clases distintas pueden
// atender rutas bajo el mismo prefijo, y se separan para que ninguna crezca demasiado.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/experiments")
public class SegundosController {

	@Autowired
	private SegundosDeResultados segundos;

	// GET /experiments/{id}/results/seconds?grupo=...&tanda=...
	// @PathVariable saca el {id} de la ruta; @RequestParam, lo que va después del signo de interrogación.
	@GetMapping("/{id}/results/seconds")
	// grupo y tanda son opcionales en la firma para que su falta llegue a deUnaTanda, que responde 400 con su mensaje (y no el
	// 500 del manejador general que da la ausencia de un parámetro obligatorio).
	//
	// Es un truco que vale la pena entender. Un @RequestParam es obligatorio por omisión, y cuando falta,
	// Spring lanza una excepción que ManejadorErrores no tiene prevista y acaba en el catch-all como 500
	// (un error del servidor), cuando en realidad la culpa es de quien llamó mal.
	// Declarándolos opcionales, llegan como null al servicio, que comprueba y lanza un ErrorApi de 400 con
	// un mensaje redactado. El precio es que la firma no refleja que de verdad hacen falta, y de ahí este
	// comentario.
	public ResponseEntity<Map<String, Object>> segundosDeUnaTanda(@PathVariable Integer id,
			@RequestParam(required = false) Integer grupo, @RequestParam(required = false) String tanda) {
		return Respuesta.ok(segundos.deUnaTanda(id, grupo, tanda), HttpStatus.OK);
	}
}
