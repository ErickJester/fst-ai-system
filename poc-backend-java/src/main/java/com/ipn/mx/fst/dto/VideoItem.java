package com.ipn.mx.fst.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VideoItem(
		Integer id,
		String day,
		String filename,
		@JsonProperty("deletion_date") String deletionDate) {}
