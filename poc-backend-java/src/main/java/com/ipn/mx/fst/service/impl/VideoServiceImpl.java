package com.ipn.mx.fst.service.impl;

import com.ipn.mx.fst.domain.Day;
import com.ipn.mx.fst.domain.Job;
import com.ipn.mx.fst.domain.JobStatus;
import com.ipn.mx.fst.domain.Video;
import com.ipn.mx.fst.dto.VideoItem;
import com.ipn.mx.fst.dto.VideoUploaded;
import com.ipn.mx.fst.repository.ExperimentRepository;
import com.ipn.mx.fst.repository.JobRepository;
import com.ipn.mx.fst.repository.VideoRepository;
import com.ipn.mx.fst.service.AlmacenamientoService;
import com.ipn.mx.fst.service.ReglaDeNegocioException;
import com.ipn.mx.fst.service.VideoService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoServiceImpl implements VideoService {

	private final VideoRepository videoRepository;
	private final ExperimentRepository experimentRepository;
	private final JobRepository jobRepository;
	private final AlmacenamientoService almacenamiento;

	public VideoServiceImpl(VideoRepository v, ExperimentRepository e,
			JobRepository j, AlmacenamientoService a) {
		this.videoRepository = v;
		this.experimentRepository = e;
		this.jobRepository = j;
		this.almacenamiento = a;
	}

	@Override
	@Transactional
	public VideoUploaded subir(Integer experimentId, String day, MultipartFile archivo) {
		String dia = (day == null ? "" : day.trim().toUpperCase());
		if (!dia.equals("DAY1") && !dia.equals("DAY2")) {
			throw new ReglaDeNegocioException(HttpStatus.BAD_REQUEST, "day must be DAY1 or DAY2");
		}

		// RF-08: solo .mp4
		String nombre = archivo.getOriginalFilename();
		if (nombre == null || nombre.isBlank()) nombre = "video.mp4";
		if (!nombre.toLowerCase().endsWith(".mp4")) {
			throw new ReglaDeNegocioException(HttpStatus.BAD_REQUEST, "Solo se aceptan archivos .mp4");
		}

		if (!experimentRepository.existsById(experimentId)) {
			throw new ReglaDeNegocioException(HttpStatus.NOT_FOUND, "experiment not found");
		}

		// RN-03: máximo un video por día por experimento
		if (videoRepository.findByExperimentIdAndDay(experimentId, Day.valueOf(dia)).isPresent()) {
			throw new ReglaDeNegocioException(HttpStatus.CONFLICT,
					"Ya existe un video para " + dia + " en este experimento");
		}

		Video v = new Video();
		v.setExperimentId(experimentId);
		v.setDay(Day.valueOf(dia));
		v.setFilename(nombre);
		v.setPath("");
		Video guardado = videoRepository.save(v);

		String ruta = almacenamiento.rutaDeVideo(experimentId, guardado.getId(), guardado.getFilename());
		try {
			almacenamiento.guardar(archivo, ruta);
		} catch (IOException e) {
			throw new ReglaDeNegocioException(HttpStatus.INTERNAL_SERVER_ERROR,
					"No se pudo guardar el archivo");
		}
		guardado.setPath(ruta);

		// RF-12: encolar análisis automáticamente
		Job j = new Job();
		j.setVideoId(guardado.getId());
		j.setStatus(JobStatus.QUEUED);
		Job trabajo = jobRepository.save(j);

		return new VideoUploaded(
				guardado.getId(), experimentId, guardado.getDay().name(),
				guardado.getFilename(), trabajo.getId());
	}

	@Override
	@Transactional(readOnly = true)
	public List<VideoItem> listar(Integer experimentId) {
		return videoRepository.findByExperimentIdOrderById(experimentId).stream()
				.map(v -> new VideoItem(
						v.getId(),
						v.getDay().name(),
						v.getFilename(),
						v.getDeletionDate() != null ? v.getDeletionDate().toString() : null))
				.toList();
	}
}
