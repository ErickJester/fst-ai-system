package com.ipn.mx.fst.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Cuerpo de POST /admin/users: {"idInstitucional", "nombre", "apellidos", "correo"}. Toda alta nace como Investigador:
// el ascenso a Administrador es solo por PATCH /admin/users/{id}. Un campo "rol" en el cuerpo se ignora.
// Los máximos son los de las columnas de la tabla usuario.
@Data
public class CrearCuentaPeticion {

	// El identificador se recorta ANTES de validar (el setter quita los espacios de los lados), así que "  2021  " es "2021".
	@NotBlank(message = "no debe estar vacío")
	@Size(max = 10, message = "debe tener como máximo 10 caracteres")
	@Pattern(regexp = "[0-9]*", message = "solo puede llevar dígitos")
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

	public void setIdInstitucional(String idInstitucional) {
		this.idInstitucional = idInstitucional == null ? null : idInstitucional.trim();
	}

}
