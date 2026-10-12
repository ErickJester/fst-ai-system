package com.ipn.mx.fst.service.impl;

import com.ipn.mx.fst.service.AlmacenamientoService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AlmacenamientoServiceImpl implements AlmacenamientoService {

	private final String dataDir;

	public AlmacenamientoServiceImpl(@Value("${app.data-dir}") String dataDir) {
		this.dataDir = dataDir;
	}

	@Override
	public String rutaDeVideo(Integer experimentId, Integer videoId, String filename) {
		String seguro = filename.replace("/", "_").replace("\\", "_");
		Path carpeta = Paths.get(dataDir, "videos", "experiment_" + experimentId);
		try {
			Files.createDirectories(carpeta);
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo crear la carpeta de videos", e);
		}
		return carpeta.resolve("video_" + videoId + "_" + seguro).toString();
	}

	@Override
	public void guardar(MultipartFile archivo, String ruta) throws IOException {
		archivo.transferTo(Paths.get(ruta));
	}
}
