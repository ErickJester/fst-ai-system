package com.ipn.mx.fst.repository;

// Una fila de las consultas de experimentos de ExperimentoRepository: el experimento y su creador. Spring
// llena cada método con la columna del mismo nombre (el alias "AS" del SQL, entre comillas para que
// PostgreSQL conserve las mayúsculas). Es una proyección, no la entidad Experimento: así la lista y el
// detalle no dependen de relaciones LAZY.
public interface FilaExperimento {

	Integer getIdExperimento();

	String getNombre();

	// AAAA-MM-DD, ya como texto
	String getFecha();

	String getNotas();

	String getCreadorId();

	String getCreadorNombre();

	String getCreadorApellidos();

}
