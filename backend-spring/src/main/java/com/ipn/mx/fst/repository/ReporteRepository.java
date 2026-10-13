package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReporteRepository extends JpaRepository<Reporte, Integer> {

	// Borra los reportes guardados de un experimento antes de borrar el experimento: la llave foránea
	// no deja borrarlo si aún tiene reportes (ON DELETE NO ACTION).
	void deleteByExperimentoIdExperimento(Integer idExperimento);

}
