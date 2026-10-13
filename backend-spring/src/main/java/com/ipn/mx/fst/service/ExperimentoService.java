package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Experimento;
import com.ipn.mx.fst.domain.Usuario;
import java.io.ByteArrayInputStream;
import java.util.List;

// Lo que el controlador puede pedirle a la capa de servicio sobre experimentos.
public interface ExperimentoService {

	List<Experimento> findAll();

	Experimento findById(Integer id);

	Experimento save(Experimento experimento);

	// Borra el experimento junto con sus reportes y notificaciones.
	void delete(Integer id);

	Usuario findUsuarioById(String idInstitucional);

	// idExcluido: el experimento que se está editando (null al crear).
	boolean existeNombre(String nombre, Integer idExcluido);

	boolean tieneGrupos(Integer id);

	// Reporte de resultados en PDF, generado en memoria.
	ByteArrayInputStream generarReporte(Integer idExperimento);

}
