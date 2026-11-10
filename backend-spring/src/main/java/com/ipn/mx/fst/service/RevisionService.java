// Carpeta lógica de esta interfaz: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Path: la ruta del archivo de video en el volumen.
import java.nio.file.Path;
import java.util.Map;
import org.springframework.security.core.Authentication;

// Revisión segundo a segundo de un análisis completado (R-14, sección 9.7 de la guía). Todos lanzan 404 si el
// experimento no existe o el análisis no es de ese experimento.
//
// Es donde una persona comprueba el trabajo del modelo: ve el video y, segundo a segundo, confirma,
// corrige o marca como dudoso. Lo que escribe queda en la columna origen de la tabla segundo (ver
// OrigenSegundo), que es la que permite decir después qué parte del dato es automática y cuál revisada.
//
// "El análisis no es de ese experimento" también da 404 por la regla de siempre: decir "existe pero no es
// de ahí" confirmaría que ese análisis existe.
// Fíjate en el reparto de permisos: VER lo puede cualquiera con sesión (R-07, los investigadores ven el
// trabajo de todos), pero CAMBIAR algo solo quien subió el video o un administrador.
// Ver la explicación de qué es la capa de servicio en VideoService.
public interface RevisionService {

	// Lo que necesita la pantalla de revisión: si el usuario puede corregir, la ruta del video, el tamaño del
	// video y, por espécimen, su recuadro y TODOS sus segundos. 409 si el análisis no está completado. Verlo
	// puede cualquier usuario autenticado (R-07).
	//
	// "Si el usuario puede corregir" viaja como un dato más: así la pantalla desactiva los botones en vez
	// de dejar intentarlo y responder 403. Es comodidad, no seguridad: el permiso se vuelve a comprobar al
	// corregir de verdad, porque nada impide llamar a la API sin pasar por la pantalla.
	// "TODOS sus segundos" son hasta 300 por espécimen y hasta 4 especímenes: unas 1 200 filas de una vez.
	// Se mandan juntas a propósito, para poder moverse por el video sin pedir nada más al servidor.
	Map<String, Object> revision(Integer idExperimento, Integer idAnalisis, Authentication authentication);

	// {"token": "..."}: un token de solo lectura del video de ese análisis. 403 si quien pide no es quien subió
	// el video ni Administrador; 409 si el análisis no está completado o el video ya no está.
	//
	// Existe por una limitación del navegador: una etiqueta <video> no puede mandar la cabecera
	// Authorization, así que no hay forma de pedir el archivo con el token de sesión (ver la ruta /video
	// en RevisionController). La salida es un token aparte que va en la URL.
	// Por eso es de solo lectura, de UN análisis y dura 120 minutos (app.video.minutos): viajar en la URL
	// significa quedar escrito en los registros del servidor, así que conviene que valga poco y poco tiempo.
	// Y por eso SeguridadConfig exige el claim tipo="sesion" para el resto de la API: para que este token
	// no abra ninguna otra puerta.
	Map<String, Object> crearTokenVideo(Integer idExperimento, Integer idAnalisis, Authentication authentication);

	// Ruta de web.mp4 si el token es de VIDEO y de ESTE análisis. 401 si el token falta, no sirve o es de otro
	// análisis; 404 si el video ya no existe.
	//
	// Fíjate en que NO recibe Authentication: es la única ruta pública de /experiments (permitAll en
	// SeguridadConfig), y toda su protección es este token. De ahí que se comprueben las dos cosas: que el
	// token sea del tipo correcto Y que sea de este análisis, no de otro cualquiera.
	// "web.mp4" y no el archivo original: el worker deja una copia preparada para reproducir en el
	// navegador. El original puede venir en un formato que el navegador no sepa mostrar.
	// El 404 cubre el caso de que el video ya se haya borrado por antigüedad (ver la entidad Video).
	Path videoPorToken(Integer idExperimento, Integer idAnalisis, String token);

	// Guarda las correcciones (solo los segundos que cambiaron) y devuelve los totales nuevos de cada espécimen
	// del análisis, en la misma forma que /results. 403, 409 o 400 según las reglas de R-14.
	//
	// "Solo los segundos que cambiaron": la pantalla manda un puñado de filas, no las 1 200 que cargó.
	// Devolver los totales recalculados ahorra una segunda petición y, sobre todo, evita que la pantalla
	// intente recalcularlos por su cuenta y acabe enseñando algo distinto de lo que dirá el reporte.
	// Esta operación invalida los reportes guardados del experimento: los resultados cambiaron, así que el
	// PDF que hubiera en disco ya no vale (ver ReporteAlmacen).
	Map<String, Object> corregir(Integer idExperimento, Integer idAnalisis, Map<String, Object> cuerpo,
			Authentication authentication);

	// R-04: tira el resultado del análisis (OBSERVACION, SEGUNDO y las correcciones humanas), invalida los reportes del
	// experimento y lo deja 'en cola' con los valores de arranque, para que el worker lo corra desde cero sobre el mismo video.
	// 403 si quien pide no subió el video ni es Administrador; 409 si no está completado, si otro reinicio se adelantó o si el
	// video ya se borró. Devuelve {idAnalisis, estado}.
	//
	// Es la operación más destructiva del sistema y conviene verlo claro: se pierden TAMBIÉN las
	// correcciones humanas, que pueden ser horas de trabajo de alguien. No hay deshacer. Por eso exige
	// permiso y la pantalla pregunta antes.
	// Tiene sentido cuando se cambió el modelo y se quiere volver a medir con el nuevo, o cuando el
	// resultado salió tan mal que corregirlo a mano cuesta más que repetirlo.
	// "Si otro reinicio se adelantó" es la misma defensa que la regla b de PausaService: dos personas
	// pulsando a la vez; gana una y la otra recibe 409 en vez de duplicar el trabajo.
	// "Con los valores de arranque" quiere decir estado 'en cola' y etapa 'preprocesamiento', es decir,
	// desde el principio (ver la entidad Analisis).
	Map<String, Object> reiniciar(Integer idExperimento, Integer idAnalisis, Authentication authentication);

}
