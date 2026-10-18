package com.ipn.mx.fst.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Cuerpo de POST /auth/login: {"correo", "contrasena"}.
@Data
public class LoginPeticion {

	@NotBlank(message = "no debe estar vacío")
	@Email(message = "no es un correo válido")
	private String correo;

	@NotBlank(message = "no debe estar vacía")
	private String contrasena;

}
