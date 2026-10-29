package com.ipn.mx.fst.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Misma forma que devuelve Flask en POST /auth/login. */
public record LoginResponse(String token, Usuario user) {

	public record Usuario(
			Integer id,
			String email,
			String name,
			String role,
			@JsonProperty("must_change_password") Boolean mustChangePassword) {}
}
