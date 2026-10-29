package com.ipn.mx.fst.repository;

import java.math.BigDecimal;

// Una fila de TandaRepository.buscarTandasYVideos: una tanda con uno de sus videos y el análisis de ese
// video. Una tanda sin videos sale en una sola fila con todo lo del video en null, por eso los tipos son
// de objeto (Integer, Boolean...) y no primitivos.
public interface FilaTandaVideo {

	Integer getIdExperimento();

	Integer getIdGrupo();

	Integer getIdTanda();

	// 1 = A, 2 = B, ... (smallint: llega como Short, por eso Number)
	Number getOrdinal();

	Number getCilindros();

	Integer getIdVideo();

	// 'Dia 1' o 'Dia 2'
	String getSesion();

	BigDecimal getDuracion();

	// Nombre del archivo como lo subió el usuario; null en los videos anteriores al 20-oct-2026
	String getNombreOriginal();

	// Fecha de carga, AAAA-MM-DD en la zona America/Mexico_City
	String getFecha();

	// false cuando el archivo ya se borró (la columna archivo es NULL)
	Boolean getVideoDisponible();

	Integer getIdAnalisis();

	String getEstadoAnalisis();

	// Días enteros desde fechaanalisis hasta ahora; null si el video no tiene análisis
	Number getDiasDesdeAnalisis();

}
