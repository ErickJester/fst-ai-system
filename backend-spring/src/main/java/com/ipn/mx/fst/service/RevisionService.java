package com.ipn.mx.fst.service;

import java.nio.file.Path;
import java.util.Map;
import org.springframework.security.core.Authentication;

// Revisión segundo a segundo de un análisis completado (R-14, sección 9.7 de la guía). Todos lanzan 404 si el
// experimento no existe o el análisis no es de ese experimento.
public interface RevisionService {

	// Lo que necesita la pantalla de revisión: si el usuario puede corregir, la ruta del video, el tamaño del
	// video y, por espécimen, su recuadro y TODOS sus segundos. 409 si el análisis no está completado. Verlo
	// puede cualquier usuario autenticado (R-07).
	Map<String, Object> revision(Integer idExperimento, Integer idAnalisis, Authentication authentication);

	// {"token": "..."}: un token de solo lectura del video de ese análisis. 403 si quien pide no es quien subió
	// el video ni Administrador; 409 si el análisis no está completado o el video ya no está.
	Map<String, Object> crearTokenVideo(Integer idExperimento, Integer idAnalisis, Authentication authentication);

	// Ruta de web.mp4 si el token es de VIDEO y de ESTE análisis. 401 si el token falta, no sirve o es de otro
	// análisis; 404 si el video ya no existe.
	Path videoPorToken(Integer idExperimento, Integer idAnalisis, String token);

	// Guarda las correcciones (solo los segundos que cambiaron) y devuelve los totales nuevos de cada espécimen
	// del análisis, en la misma forma que /results. 403, 409 o 400 según las reglas de R-14.
	Map<String, Object> corregir(Integer idExperimento, Integer idAnalisis, Map<String, Object> cuerpo,
			Authentication authentication);

	// R-04: tira el resultado del análisis (OBSERVACION, SEGUNDO y las correcciones humanas), invalida los reportes del
	// experimento y lo deja 'en cola' con los valores de arranque, para que el worker lo corra desde cero sobre el mismo video.
	// 403 si quien pide no subió el video ni es Administrador; 409 si no está completado, si otro reinicio se adelantó o si el
	// video ya se borró. Devuelve {idAnalisis, estado}.
	Map<String, Object> reiniciar(Integer idExperimento, Integer idAnalisis, Authentication authentication);

}
