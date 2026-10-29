package com.ipn.mx.fst.service.impl;

import com.ipn.mx.fst.domain.Experiment;
import com.ipn.mx.fst.dto.ExperimentCreated;
import com.ipn.mx.fst.dto.ExperimentItem;
import com.ipn.mx.fst.dto.ExperimentRequest;
import com.ipn.mx.fst.repository.ExperimentRepository;
import com.ipn.mx.fst.repository.UserRepository;
import com.ipn.mx.fst.service.ExperimentService;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperimentServiceImpl implements ExperimentService {

	private final ExperimentRepository experimentRepository;
	private final UserRepository userRepository;

	public ExperimentServiceImpl(ExperimentRepository e, UserRepository u) {
		this.experimentRepository = e;
		this.userRepository = u;
	}

	@Override
	@Transactional
	public ExperimentCreated crear(ExperimentRequest p) {
		// Flask: user_id llega en el cuerpo hasta que exista el JWT; por omisión 1.
		Integer userId = p.userId() != null ? p.userId() : 1;
		if (!userRepository.existsById(userId)) {
			return null;
		}

		Experiment exp = new Experiment();
		exp.setUserId(userId);
		exp.setName(p.name().trim());
		exp.setExperimentDate(fechaONull(p.experimentDate()));
		exp.setTreatment(p.treatment());
		exp.setSpecies(p.species());
		exp.setLayout(p.layout() != null ? p.layout() : "auto");
		exp.setNotes(p.notes());

		Experiment guardado = experimentRepository.save(exp);
		return new ExperimentCreated(
				guardado.getId(),
				guardado.getName(),
				guardado.getCreatedAt().toString(),
				guardado.getTreatment(),
				guardado.getLayout());
	}

	/** Flask ignora en silencio una fecha mal formada y la deja en null. */
	private LocalDateTime fechaONull(String iso) {
		if (iso == null || iso.isBlank()) return null;
		try {
			return LocalDateTime.parse(iso);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<ExperimentItem> listar(Integer userId) {
		List<Experiment> filas = (userId != null)
				? experimentRepository.findTop100ByUserIdOrderByIdDesc(userId)
				: experimentRepository.findTop100ByOrderByIdDesc();

		return filas.stream().map(e -> new ExperimentItem(
				e.getId(),
				e.getName(),
				e.getCreatedAt().toString(),
				e.getTreatment(),
				e.getSpecies(),
				e.getLayout(),
				e.getExperimentDate() != null ? e.getExperimentDate().toString() : null,
				e.getUserId())).toList();
	}
}
