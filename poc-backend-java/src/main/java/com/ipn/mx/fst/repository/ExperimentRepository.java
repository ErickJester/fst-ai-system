package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Experiment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentRepository extends JpaRepository<Experiment, Integer> {

	// Flask: select(...).order_by(Experiment.id.desc()).limit(100)
	List<Experiment> findTop100ByOrderByIdDesc();

	List<Experiment> findTop100ByUserIdOrderByIdDesc(Integer userId);
}
