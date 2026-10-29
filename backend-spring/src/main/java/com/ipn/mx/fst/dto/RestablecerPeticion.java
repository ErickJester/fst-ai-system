package com.ipn.mx.fst.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Cuerpo de POST /auth/reset: {"token", "contrasenaNueva"}. El token es el del enlace del correo.
@Data
public class RestablecerPeticion {

	@NotBlank(message = "no debe estar vacío")
	private String token;

	@NotBlank(message = "no debe estar vacía")
	private String contrasenaNueva;

}
