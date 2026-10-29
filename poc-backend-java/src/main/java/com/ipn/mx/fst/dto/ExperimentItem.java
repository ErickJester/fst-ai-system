package com.ipn.mx.fst.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /experiments devuelve ocho campos por elemento. */
public record ExperimentItem(
		Integer id,
		String name,
		@JsonProperty("created_at") String createdAt,
		String treatment,
		String species,
		String layout,
		@JsonProperty("experiment_date") String experimentDate,
		@JsonProperty("user_id") Integer userId) {}
