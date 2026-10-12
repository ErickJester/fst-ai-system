package com.ipn.mx.fst.service;

import com.ipn.mx.fst.dto.VideoItem;
import com.ipn.mx.fst.dto.VideoUploaded;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface VideoService {

	/** Lanza ReglaDeNegocioException cuando el experimento no existe o el día ya tiene video. */
	VideoUploaded subir(Integer experimentId, String day, MultipartFile archivo);

	List<VideoItem> listar(Integer experimentId);
}
