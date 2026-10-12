package com.ipn.mx.fst.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VideoUploaded(
		@JsonProperty("video_id") Integer videoId,
		@JsonProperty("experiment_id") Integer experimentId,
		String day,
		String filename,
		@JsonProperty("job_id") Integer jobId) {}
