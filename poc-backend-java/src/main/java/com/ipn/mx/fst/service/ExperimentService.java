package com.ipn.mx.fst.service;

import com.ipn.mx.fst.dto.ExperimentCreated;
import com.ipn.mx.fst.dto.ExperimentItem;
import com.ipn.mx.fst.dto.ExperimentRequest;
import java.util.List;

public interface ExperimentService {

	/** Devuelve null si el usuario no existe (el controlador lo traduce a 404). */
	ExperimentCreated crear(ExperimentRequest peticion);

	List<ExperimentItem> listar(Integer userId);
}
