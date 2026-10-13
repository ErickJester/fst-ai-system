package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

	// Igual que con los reportes: las notificaciones de un experimento se borran antes que él.
	void deleteByExperimentoIdExperimento(Integer idExperimento);

}
