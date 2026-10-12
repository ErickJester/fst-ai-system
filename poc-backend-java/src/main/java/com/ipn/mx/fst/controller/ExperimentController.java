package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.dto.ExperimentCreated;
import com.ipn.mx.fst.dto.ExperimentItem;
import com.ipn.mx.fst.dto.ExperimentRequest;
import com.ipn.mx.fst.service.ExperimentService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/experiments")
public class ExperimentController {

	private final ExperimentService experimentService;

	public ExperimentController(ExperimentService experimentService) {
		this.experimentService = experimentService;
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody ExperimentRequest peticion) {
		if (peticion.name() == null || peticion.name().isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("error", "name required"));
		}
		ExperimentCreated creado = experimentService.crear(peticion);
		if (creado == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("error", "user not found"));
		}
		// Flask devuelve 200, no 201.
		return ResponseEntity.ok(creado);
	}

	@GetMapping
	public List<ExperimentItem> list(@RequestParam(name = "user_id", required = false) Integer userId) {
		return experimentService.listar(userId);
	}
}
