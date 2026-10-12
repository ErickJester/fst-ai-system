package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Integer> {
}
