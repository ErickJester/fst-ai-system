// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// BigDecimal: número decimal exacto. La duración del video se guarda así (ver la entidad Video).
import java.math.BigDecimal;

// Una fila de TandaRepository.buscarTandasYVideos: una tanda con uno de sus videos y el análisis de ese
// video. Una tanda sin videos sale en una sola fila con todo lo del video en null, por eso los tipos son
// de objeto (Integer, Boolean...) y no primitivos.
//
// Esa última frase es la clave del archivo: con LEFT JOIN, las columnas de las tablas que no casaron
// llegan en null. Un int o un boolean primitivos no pueden ser null y el mapeo reventaría; Integer y
// Boolean sí pueden. Por eso toda proyección que venga de un LEFT JOIN usa tipos de objeto.
// Ver la explicación general de las proyecciones en FilaGrupo.
public interface FilaTandaVideo {

	// --- De la tanda y su grupo: estas tres nunca son null (los JOIN son obligatorios) ---

	Integer getIdExperimento();

	Integer getIdGrupo();

	Integer getIdTanda();

	// 1 = A, 2 = B, ... (smallint: llega como Short, por eso Number)
	// La traducción de número a letra se hace al mostrar, no aquí.
	Number getOrdinal();

	// Cuántos cilindros se usaron en la grabación, de 2 a 4.
	Number getCilindros();

	// --- Del video: a partir de aquí todo puede ser null (la tanda puede no tener video) ---

	Integer getIdVideo();

	// 'Dia 1' o 'Dia 2'
	// Texto crudo de la base, no el enum SesionVideo: una proyección no pasa por los conversores de JPA.
	String getSesion();

	// La duración en segundos, con dos decimales.
	BigDecimal getDuracion();

	// Nombre del archivo como lo subió el usuario; null en los videos anteriores al 20-oct-2026
	// Es solo para mostrar: nunca se usa para armar una ruta (ver la advertencia en la entidad Video).
	String getNombreOriginal();

	// Fecha de carga, AAAA-MM-DD en la zona America/Mexico_City
	// Llega ya formateada como TEXTO desde la base, no como fecha. Es a propósito: así la fecha que ve el
	// usuario no cambia según la zona horaria del servidor ni la del navegador.
	String getFecha();

	// false cuando el archivo ya se borró (la columna archivo es NULL)
	// Lo calcula la propia consulta con (v.archivo IS NOT NULL). Recuerda que la fila del video sobrevive
	// al archivo: los resultados se conservan aunque el video ya no esté.
	Boolean getVideoDisponible();

	// --- Del análisis: null si el video todavía no tiene análisis ---

	Integer getIdAnalisis();

	// Texto crudo del dominio estado_analisis ('en cola', 'procesando', 'completado'...).
	String getEstadoAnalisis();

	// Días enteros desde fechaanalisis hasta ahora; null si el video no tiene análisis
	// Se calcula en la base y no en Java, para no depender de cómo Hibernate convierta la fecha con zona
	// horaria (ver la nota de la consulta en TandaRepository). Es lo que alimenta el aviso de los videos
	// que están a punto de borrarse por antigüedad.
	Number getDiasDesdeAnalisis();

}
