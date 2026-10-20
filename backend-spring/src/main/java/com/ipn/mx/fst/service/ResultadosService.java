package com.ipn.mx.fst.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// Progreso y resultados de un experimento para las pantallas (secciones 6.1 y 7 de la guía). Todo es de
// lectura y cualquier usuario autenticado puede verlo (R-07). Todos lanzan 404 si el experimento no existe.
public interface ResultadosService {

	// {"analisis": [...]}: todos los análisis del experimento, también los completados, con su avance.
	Map<String, Object> estado(Integer idExperimento);

	// {"especimenes": [...]}: los especímenes con análisis completado y los totales de cada conducta.
	Map<String, Object> resultados(Integer idExperimento);

	// Lo mismo que resultados y, en cada espécimen, los segundos de cada conducta por minuto.
	Map<String, Object> resultadosPorMinuto(Integer idExperimento);

	// Un renglón por grupo con el promedio y la desviación estándar de cada conducta (guía 7).
	List<Map<String, Object>> resultadosPorGrupo(Integer idExperimento);

	// Una fila por tanda con análisis completado, para la sección "Tiempos de análisis" del PDF de resultados:
	// grupo, tanda (letra), duracionVideo (segundos) y tiempos (lo de tiempos.json, o null si no hay archivo).
	List<Map<String, Object>> tiemposDelExperimento(Integer idExperimento);

	// Una fila por tanda con análisis completado, para la tabla "Videos analizados" del PDF de resultados (siempre
	// visible, no depende del modo desarrollador): grupo, tanda (letra), nombreVideo (como lo subió el usuario, o
	// null en los videos anteriores al 20-oct-2026), duracionVideo (segundos) e idAnalisis.
	List<Map<String, Object>> videosAnalizados(Integer idExperimento);

	// Lo que se descarga con "Segundos": el nombre del archivo (un CSV, o un ZIP si es de todo el grupo), si es ZIP y
	// los archivos que lleva (nombre dentro del ZIP o de la descarga -> ruta en el volumen).
	record SalidaSegundos(String nombre, boolean zip, Map<String, Path> archivos) {
	}

	// La salida por segundo del modelo TAL CUAL la deja fst_auto (trabajo/{idVideo}_cnnseg.csv): la de la tanda (con
	// letra) o, sin letra, la de cada tanda del grupo con análisis completado y su _cnnseg_resumen.csv. Es la salida
	// CRUDA del modelo, del video completo y con probabilidades: NO trae las correcciones de la revisión. Solo con
	// MODO_DESARROLLADOR=true; sin él, 404 como si la ruta no existiera. 400 sin grupo; 404 si la tanda no tiene análisis
	// completado o el archivo.
	SalidaSegundos segundosDelModelo(Integer idExperimento, Integer idGrupo, String letra);

	// Ruta de la imagen de control que dejó el pipeline (trabajo/{idVideo}_control.jpg): los tubos que encontró. 404 si
	// el análisis no es de ese experimento o la imagen no existe.
	Path control(Integer idExperimento, Integer idAnalisis);

	// Ruta del PDF de diagnóstico de un análisis en error. 404 si el análisis no es de ese experimento, no
	// está en error o el archivo no existe.
	Path diagnostico(Integer idExperimento, Integer idAnalisis);

}
