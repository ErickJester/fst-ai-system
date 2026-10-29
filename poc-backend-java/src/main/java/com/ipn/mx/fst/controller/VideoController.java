package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.dto.VideoItem;
import com.ipn.mx.fst.dto.VideoUploaded;
import com.ipn.mx.fst.service.VideoService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/experiments/{expId}/videos")
public class VideoController {

	private final VideoService videoService;

	public VideoController(VideoService videoService) {
		this.videoService = videoService;
	}

	@PostMapping
	public ResponseEntity<?> upload(
			@PathVariable Integer expId,
			@RequestParam(name = "file", required = false) MultipartFile file,
			@RequestParam(name = "day", required = false) String day) {

		if (file == null || file.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("error", "file required"));
		}
		VideoUploaded subido = videoService.subir(expId, day, file);
		return ResponseEntity.ok(subido);
	}

	@GetMapping
	public List<VideoItem> list(@PathVariable Integer expId) {
		return videoService.listar(expId);
	}
}
