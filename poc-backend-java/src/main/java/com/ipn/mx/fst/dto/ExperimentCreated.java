package com.ipn.mx.fst.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /experiments devuelve solo estos cinco campos. */
public record ExperimentCreated(
		Integer id,
		String name,
		@JsonProperty("created_at") String createdAt,
		String treatment,
		String layout) {}
