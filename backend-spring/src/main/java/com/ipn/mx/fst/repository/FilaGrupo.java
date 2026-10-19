package com.ipn.mx.fst.repository;

// Una fila de GrupoRepository.buscarGrupos: un grupo, con el ordinal que tendría su siguiente tanda.
public interface FilaGrupo {

	Integer getIdExperimento();

	Integer getIdGrupo();

	String getEtiqueta();

	String getTipo();

	String getTratamiento();

	// max(ordinal) + 1, o 1 si el grupo todavía no tiene tandas
	Number getSiguienteOrdinal();

}
