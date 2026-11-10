// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// El servicio con las reglas de la subida.
import com.ipn.mx.fst.service.VideoService;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
// MediaType: aquí se usa su constante del formato multipart.
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
// Authentication: el usuario autenticado; se pasa entero al servicio, no solo su nombre.
import org.springframework.security.core.Authentication;
// Las anotaciones de ruta: variable de ruta, POST, prefijo común y parámetros del formulario.
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
// MultipartFile: el archivo que llega en un formulario, ya recibido por Spring.
import org.springframework.web.multipart.MultipartFile;

// Subida de un video desde la página. Cuerpo multipart/form-data: archivo, sesion ('Dia 1' o 'Dia 2'),
// tipo y tratamiento (solo si el grupo es nuevo), nCilindros (solo si la tanda es nueva) y ordinal (la tanda
// confirmada; si falta, la siguiente). {grupo} es la ETIQUETA del grupo; Spring ya la decodifica de la URL.
// Todos los campos son opcionales para Spring: las reglas y sus mensajes (400, 422, 409) los pone el
// servicio, para que el frontend reciba siempre el envoltorio {data, error, status}.
//
// Es la ruta más compleja de la API, porque una sola petición puede crear hasta cuatro cosas: el grupo
// (si la etiqueta es nueva), la tanda, el video y el análisis que se encola. De ahí que haya campos que
// solo se usan en algunos casos.
// Qué es multipart/form-data: el formato con el que un navegador manda un formulario que lleva archivos.
// No es JSON: cada campo va en su propia parte, y por eso aquí hay @RequestParam en lugar de un DTO con
// @RequestBody.
// La nota sobre que todo sea opcional es el mismo truco de SegundosController, y está explicado allí:
// un @RequestParam obligatorio que falta produce un 500 en vez de un 400.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/experiments")
public class VideoController {

	@Autowired
	private VideoService videoService;

	// consumes = MULTIPART_FORM_DATA_VALUE: esta ruta SOLO acepta formularios con archivo. Si alguien
	// manda JSON, Spring responde 415 (lo convierte ManejadorErrores) sin llegar al método.
	// {grupo} es la etiqueta, no un id: la pantalla manda el nombre que escribió el investigador, y el
	// servicio busca el grupo por él o lo crea si no existe. Spring decodifica solo el %20 de los espacios.
	@PostMapping(path = "/{id}/groups/{grupo}/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> subir(@PathVariable Integer id, @PathVariable String grupo,
			// El archivo en sí. Spring ya lo recibió y lo dejó en disco antes de llamar a este método,
			// en la carpeta de spring.servlet.multipart.location (dentro del mismo volumen, para que
			// VideoServiceImpl pueda MOVERLO a su destino sin copiar un gigabyte: ver application.properties).
			@RequestParam(required = false) MultipartFile archivo,
			// 'Dia 1' o 'Dia 2'. Llega como texto crudo y el servicio lo convierte al enum SesionVideo.
			@RequestParam(required = false) String sesion,
			// Estos dos solo se usan si el grupo no existía y hay que crearlo.
			@RequestParam(required = false) String tipo,
			@RequestParam(required = false) String tratamiento,
			// Solo se usa si la tanda es nueva: cuántos cilindros se grabaron (de 2 a 4).
			@RequestParam(required = false) Integer nCilindros,
			// Qué tanda es. Si no viene, el servicio usa la siguiente (el siguienteOrdinal que calcula la
			// consulta de GrupoRepository).
			@RequestParam(required = false) Integer ordinal,
			Authentication authentication) {
		// Se pasa el objeto Authentication ENTERO, y no solo getName() como en los demás controllers:
		// el servicio necesita algo más que el identificador, por ejemplo comprobar permisos.
		return Respuesta.ok(
				videoService.subir(id, grupo, archivo, sesion, tipo, tratamiento, nCilindros, ordinal, authentication),
				// 201: la petición creó recursos (como mínimo el video).
				HttpStatus.CREATED);
	}

}
