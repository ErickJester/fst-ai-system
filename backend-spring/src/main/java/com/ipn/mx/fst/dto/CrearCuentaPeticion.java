package com.ipn.mx.fst.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Cuerpo de POST /admin/users: {"idInstitucional", "nombre", "apellidos", "correo"} y, si se quiere, "rol" (Investigador por omisión).
// Los máximos son los de las columnas de la tabla usuario.
@Data
public class CrearCuentaPeticion {

	@NotBlank(message = "no debe estar vacío")
	@Size(max = 10, message = "debe tener como máximo 10 caracteres")
	@Pattern(regexp = "[A-Za-z0-9]*", message = "solo puede llevar letras y números")
	private String idInstitucional;

	@NotBlank(message = "no debe estar vacío")
	@Size(max = 100, message = "debe tener como máximo 100 caracteres")
	private String nombre;

	@NotBlank(message = "no debe estar vacío")
	@Size(max = 100, message = "debe tener como máximo 100 caracteres")
	private String apellidos;

	@NotBlank(message = "no debe estar vacío")
	@Email(message = "no es un correo válido")
	@Size(max = 254, message = "debe tener como máximo 254 caracteres")
	private String correo;

	private String rol;

}
