package com.ipn.mx.fst.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Cuerpo de POST /auth/recover: {"correo"}.
@Data
public class RecuperarPeticion {

	@NotBlank(message = "no debe estar vacío")
	@Email(message = "no es un correo válido")
	private String correo;

}
