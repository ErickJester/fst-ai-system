package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrupoRepository extends JpaRepository<Grupo, Integer> {

	// ¿El experimento ya tiene grupos? Mientras el CRUD no sepa borrar grupos, tandas y videos, un
	// experimento con grupos no se puede eliminar.
	boolean existsByExperimentoIdExperimento(Integer idExperimento);

}
