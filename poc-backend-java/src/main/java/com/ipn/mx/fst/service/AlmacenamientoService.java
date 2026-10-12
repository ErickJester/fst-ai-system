package com.ipn.mx.fst.service;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/** Equivale a app/storage.py. */
public interface AlmacenamientoService {

	String rutaDeVideo(Integer experimentId, Integer videoId, String filename);

	void guardar(MultipartFile archivo, String ruta) throws IOException;
}
