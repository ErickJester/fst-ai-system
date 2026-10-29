package com.ipn.mx.fst.repository;

import java.math.BigDecimal;

// Una fila de la consulta de resultados de SegundoRepository: los segundos de una conducta, en un minuto,
// de un espécimen. Spring llena cada método con la columna del mismo nombre (el alias "AS" del SQL).
public interface FilaResultado {

	String getGrupo();

	// ordinal de la tanda: 1 = A, 2 = B, ...
	Number getOrdinal();

	Number getCilindro();

	Number getMinuto();

	String getConducta();

	BigDecimal getSegundos();

}
