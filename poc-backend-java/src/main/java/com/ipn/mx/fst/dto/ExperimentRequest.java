package com.ipn.mx.fst.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExperimentRequest(
		String name,
		@JsonProperty("user_id") Integer userId,
		@JsonProperty("experiment_date") String experimentDate,
		String treatment,
		String species,
		String layout,
		String notes) {}
