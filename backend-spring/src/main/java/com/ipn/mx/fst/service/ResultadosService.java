// Carpeta lógica de esta interfaz: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Path: rutas de archivos del volumen (imágenes, PDF de diagnóstico, CSV del modelo).
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// Progreso y resultados de un experimento para las pantallas (secciones 6.1 y 7 de la guía). Todo es de
// lectura y cualquier usuario autenticado puede verlo (R-07). Todos lanzan 404 si el experimento no existe.
//
// Es el contrato de todo lo que se puede CONSULTAR de un experimento ya analizado, y el que alimenta las
// pantallas de resultados, el PDF y los reportes de tabla.
// Fíjate en los cinco primeros métodos: son los MISMOS datos con distinto grado de agregación (por
// espécimen, por minuto, por bloques, por grupo, y en forma de gráfica). Cada pantalla pide el que
// necesita, en vez de traerse todo y recortar en el navegador.
// "Todo es de lectura" explica que ningún método reciba Authentication: no hay nada que autorizar más
// allá de tener sesión, y de eso se encarga SeguridadConfig.
// Ver la explicación de qué es la capa de servicio en VideoService.
public interface ResultadosService {

	// {"analisis": [...]}: todos los análisis del experimento, también los completados, con su avance.
	// Es lo que alimenta la barra de progreso. Incluye los completados a propósito: la pantalla enseña
	// la lista entera, no solo lo que está en marcha.
	Map<String, Object> estado(Integer idExperimento);

	// {"especimenes": [...]}: los especímenes con análisis completado y los totales de cada conducta.
	// El nivel de detalle base: cuántos segundos de cada conducta tuvo cada espécimen en total.
	Map<String, Object> resultados(Integer idExperimento);

	// Lo mismo que resultados y, en cada espécimen, los segundos de cada conducta por minuto.
	// Los cinco minutos de la prueba (ver SegundoId). Sirve para ver cómo evoluciona la conducta a lo
	// largo de la sesión, que es lo que suele interesar: la inmovilidad crece con los minutos.
	Map<String, Object> resultadosPorMinuto(Integer idExperimento);

	// {"especimenes": [...]}: los especímenes con análisis completado y, de cada uno, sus bloques de 5 s (R-12, D-53): inicio (segundo,
	// desde 1), conducta que gana, matiz y los segundos del bloque. Se calculan desde SEGUNDO al pedirlos; no se guardan (D-52).
	//
	// Es el formato clásico con el que se puntúa la prueba a mano, para poder comparar con la literatura
	// y con lo que el laboratorio hacía antes. La regla de decisión está en BloquesDeCinco.
	Map<String, Object> resultadosPorBloques(Integer idExperimento);

	// Un renglón por grupo con el promedio, la desviación estándar y la varianza de cada conducta (guía 7).
	// Es la tabla que responde la pregunta del experimento: si el grupo tratado se comporta distinto del
	// control. Los cálculos salen de Estadisticos, con la varianza muestral (n − 1).
	List<Map<String, Object>> resultadosPorGrupo(Integer idExperimento);

	// Los datos de las gráficas de resultados: todos los grupos del Día 2 del experimento (control, referencia y luego
	// experimentales) con media, DE, varianza, coeficiente de variación, n y valores por espécimen por conducta, y por
	// minuto (1 a 5). Especificación en frontend/src/components/resultados/graficas/ESPECIFICACION.md.
	//
	// El orden (control, referencia, experimentales) es el de TipoGrupo y no es casual: así las gráficas
	// salen siempre con la línea base a la izquierda, que es como se leen.
	// Lo usan la pantalla Y el PDF: las gráficas del PDF no recalculan nada, leen esto mismo (ver
	// DatosGraficas). Es lo que garantiza que la web y el papel digan lo mismo.
	Map<String, Object> graficas(Integer idExperimento);

	// De qué grupo o tanda es un reporte CSV/XLSX: idTanda, ordinal y letra son null cuando es el grupo completo.
	//
	// Los tres null juntos son lo que distingue los dos casos; no hay un booleano aparte que pudiera
	// quedar descuadrado con ellos.
	record AlcanceDeReporte(int idGrupo, String etiqueta, Integer idTanda, Integer ordinal, String letra) {

		// El alcance de ReporteAlmacen: grupo-{idGrupo} o tanda-{idTanda}.
		// Es la clave con la que el almacén distingue los reportes guardados de un mismo experimento:
		// uno del grupo, otro de cada tanda. Por eso la tabla reporte no puede identificarlos por
		// (experimento, formato) y usa la ruta (ver la entidad Reporte).
		public String alcance() {
			return idTanda == null ? "grupo-" + idGrupo : "tanda-" + idTanda;
		}
	}

	// Resuelve y valida el alcance de un reporte (letra nula o en blanco = el grupo completo). 400 sin grupo o si la letra no es
	// una sola letra de la A a la Z; 404 si el experimento no existe, si el grupo no es de ese experimento o si el grupo no tiene
	// esa tanda.
	//
	// Un solo método que valida y traduce a la vez, y que llaman el CSV, el XLSX y los segundos de una
	// tanda. Así los tres dan exactamente los mismos códigos y mensajes ante los mismos errores.
	// "Si el grupo no es de ese experimento" también da 404, por la regla de no confirmar la existencia
	// de datos ajenos.
	AlcanceDeReporte alcanceDeReporte(Integer idExperimento, Integer idGrupo, String letra);

	// Lo que llevan los reportes CSV y XLSX del grupo o de la tanda (D-99): una fila por espécimen y minuto, media, desviación
	// estándar y varianza por conducta, y qué especímenes se revisaron. Los mismos errores que alcanceDeReporte y 409 si el
	// alcance no tiene ningún espécimen con análisis completado.
	//
	// Devuelve una TablaDeReporte, que es la estructura que luego escriben ReporteCsv y ReporteXlsx sin
	// calcular nada. Ese reparto es lo que hace que los dos formatos no puedan descuadrarse.
	TablaDeReporte tablaDeReporte(Integer idExperimento, Integer idGrupo, String letra);

	// Una fila por tanda con análisis completado, para la sección "Tiempos de análisis" del PDF de resultados:
	// grupo, tanda (letra), duracionVideo (segundos) y tiempos (lo de tiempos.json, o null si no hay archivo).
	//
	// Solo sale en el PDF en modo desarrollador: sirve para comparar cuánto tarda el análisis en distintas
	// computadoras. tiempos.json lo escribe el worker en el volumen, igual que modelo-actual.json.
	List<Map<String, Object>> tiemposDelExperimento(Integer idExperimento);

	// Una fila por tanda con análisis completado, para la tabla "Videos analizados" del PDF de resultados (siempre
	// visible, no depende del modo desarrollador): grupo, tanda (letra), nombreVideo (como lo subió el usuario, o
	// null en los videos anteriores al 20-oct-2026), duracionVideo (segundos) e idAnalisis.
	//
	// El paréntesis marca la diferencia con el método de arriba: esta tabla sale SIEMPRE, porque es parte
	// del resultado (de qué archivos salió la medición), no una herramienta de desarrollo.
	List<Map<String, Object>> videosAnalizados(Integer idExperimento);

	// Lo que se descarga con "Salida del modelo": el nombre del archivo (un CSV, o un ZIP si es de todo el grupo), si es
	// ZIP y los archivos que lleva (nombre dentro del ZIP o de la descarga -> ruta en el volumen).
	//
	// El mapa relaciona el nombre que tendrá cada archivo DENTRO del ZIP con su ruta real en el disco.
	// El controller lo recorre para armar el ZIP (ver ResultadosController.salidaModelo).
	record SalidaModelo(String nombre, boolean zip, Map<String, Path> archivos) {
	}

	// La salida por segundo del modelo TAL CUAL la deja fst_auto (trabajo/{idVideo}_cnnseg.csv): la de la tanda (con
	// letra) o, sin letra, la de cada tanda del grupo con análisis completado y su _cnnseg_resumen.csv. Es la salida
	// CRUDA del modelo, del video completo y con probabilidades: NO trae las correcciones de la revisión. Solo con
	// MODO_DESARROLLADOR=true; sin él, 404 como si la ruta no existiera. 400 sin grupo; 404 si la tanda no tiene análisis
	// completado o el archivo.
	//
	// La advertencia en mayúsculas importa: esto NO son los resultados del sistema, son lo que el modelo
	// escupió antes de que nadie lo revisara, y sirven para reentrenarlo. Confundirlos con los resultados
	// buenos (los de resultados(), que sí llevan las correcciones) daría conclusiones falsas.
	// El 404 en vez de 403 sin modo desarrollador es para que la ruta no delate que existe.
	SalidaModelo salidaDelModelo(Integer idExperimento, Integer idGrupo, String letra);

	// Ruta de la imagen de control que dejó el pipeline (trabajo/{idVideo}_control.jpg): los tubos que encontró. 404 si
	// el análisis no es de ese experimento o la imagen no existe.
	//
	// Es lo que se mira cuando un análisis falla: si el modelo detectó los cilindros donde no eran, la
	// imagen lo enseña de un vistazo.
	Path control(Integer idExperimento, Integer idAnalisis);

	// Ruta del PDF de diagnóstico de un análisis en error. 404 si el análisis no es de ese experimento, no
	// está en error o el archivo no existe.
	//
	// El PDF lo escribe el worker al fallar, y la ruta está en la columna rutaDiagnostico de Analisis,
	// que solo se llena cuando el estado es 'error'. De ahí que pedirlo de un análisis sano dé 404.
	Path diagnostico(Integer idExperimento, Integer idAnalisis);

}
