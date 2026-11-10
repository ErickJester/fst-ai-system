// Carpeta lógica de esta interfaz: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

import java.util.Map;
// Authentication: el usuario autenticado. Se recibe entero (no solo su nombre) porque hay que comprobar
// además si es administrador.
import org.springframework.security.core.Authentication;
// MultipartFile: el archivo que llegó en el formulario, ya recibido por Spring.
import org.springframework.web.multipart.MultipartFile;

// Subida de videos (secciones 4.1 y 5 de la guía): lo que el controlador puede pedirle a la capa de servicio.
//
// Qué es la capa de servicio: donde viven las REGLAS. El controller traduce HTTP y el repositorio habla con
// la base; en medio, el servicio decide qué se puede hacer y qué no.
// Por qué cada servicio es una interfaz con su clase Impl al lado: quien lo usa depende del contrato y no
// de cómo está escrito por dentro. Spring inyecta la implementación sola, porque es la única que hay.
// Fíjate en que los comentarios de esta interfaz son el CONTRATO completo: qué hace, qué devuelve y con
// qué código falla en cada caso. Se escriben aquí y no en el Impl porque es lo que lee quien va a usarlo.
public interface VideoService {

	// Guarda el video de una tanda (creando el grupo y la tanda si no existen) y, si es del Día 2, deja su
	// análisis en cola. Devuelve {idVideo, idAnalisis, estadoAnalisis}; los dos últimos son null con el Día 1.
	// Errores: 404 el experimento no existe; 403 quien sube no creó el experimento (D-107); 400 falta el archivo
	// o la sesión no es válida; 422 extensión, grupo o tanda nuevos incompletos, video ilegible o vertical;
	// 409 la tanda ya tiene el video de esa sesión.
	//
	// Un solo método para toda la subida, porque todo tiene que ocurrir o no ocurrir: crear el grupo y
	// luego fallar al guardar el archivo dejaría un grupo vacío que nadie pidió.
	// "Si es del Día 2, deja su análisis en cola" es la frontera con el worker: el backend inserta la fila
	// de análisis en estado 'en cola' y ahí termina su parte (ver la entidad Analisis). El Día 1 no se
	// analiza porque es la sesión de entrenamiento (ver SesionVideo), de ahí que esos dos campos sean null.
	// La diferencia entre 400 y 422 es fina: 400 es "la petición está mal formada" (falta el archivo) y 422
	// es "la petición se entiende, pero lo que pide no se puede hacer" (el video está de pie).
	Map<String, Object> subir(Integer idExperimento, String etiquetaGrupo, MultipartFile archivo, String sesion,
			String tipo, String tratamiento, Integer nCilindros, Integer ordinal, Authentication authentication);

}
