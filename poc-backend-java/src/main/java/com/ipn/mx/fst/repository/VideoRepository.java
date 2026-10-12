package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Day;
import com.ipn.mx.fst.domain.Video;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Integer> {

	// RN-03: máximo un video por día por experimento
	Optional<Video> findByExperimentIdAndDay(Integer experimentId, Day day);

	List<Video> findByExperimentIdOrderById(Integer experimentId);
}
