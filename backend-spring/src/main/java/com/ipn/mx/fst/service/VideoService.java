package com.ipn.mx.fst.service;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

// Subida de videos (secciones 4.1 y 5 de la guía): lo que el controlador puede pedirle a la capa de servicio.
public interface VideoService {

	// Guarda el video de una tanda (creando el grupo y la tanda si no existen) y, si es del Día 2, deja su
	// análisis en cola. Devuelve {idVideo, idAnalisis, estadoAnalisis}; los dos últimos son null con el Día 1.
	// Errores: 404 el experimento no existe; 403 quien sube no creó el experimento (D-107); 400 falta el archivo
	// o la sesión no es válida; 422 extensión, grupo o tanda nuevos incompletos, video ilegible o vertical;
	// 409 la tanda ya tiene el video de esa sesión.
	Map<String, Object> subir(Integer idExperimento, String etiquetaGrupo, MultipartFile archivo, String sesion,
			String tipo, String tratamiento, Integer nCilindros, Integer ordinal, Authentication authentication);

}
