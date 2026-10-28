package com.ipn.mx.fst.repository;

// Un espécimen con revisión humana, de la consulta buscarRevisados de SegundoRepository. Spring llena cada método con la columna
// del mismo nombre (el alias "AS" del SQL).
public interface FilaEspecimenRevisado {

	String getGrupo();

	// ordinal de la tanda: 1 = A, 2 = B, ...
	Number getOrdinal();

	Number getCilindro();

}
