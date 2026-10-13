package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Experimento;
import org.springframework.data.jpa.repository.JpaRepository;

// Además del CRUD que da JpaRepository, dos consultas que Spring arma a partir del nombre del método.
public interface ExperimentoRepository extends JpaRepository<Experimento, Integer> {

	// ¿Ya hay un experimento con este nombre? (para el POST)
	boolean existsByNombre(String nombre);

	// ¿Hay OTRO experimento con este nombre? (para el PUT: el propio experimento no cuenta)
	boolean existsByNombreAndIdExperimentoNot(String nombre, Integer idExperimento);

}
