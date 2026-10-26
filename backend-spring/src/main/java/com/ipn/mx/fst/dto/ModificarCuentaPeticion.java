package com.ipn.mx.fst.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Cuerpo de PATCH /admin/users/{id}: solo cambia lo que viene. Cualquier campo puede faltar; uno que viene vacío se rechaza.
// "idInstitucional" se acepta solo si es el mismo de la URL: el identificador es la llave de la cuenta y no se cambia.
@Data
public class ModificarCuentaPeticion {

	@Size(max = 10, message = "debe tener como máximo 10 caracteres")
	private String idInstitucional;

	@Pattern(regexp = ".*\\S.*", message = "no debe estar vacío")
	@Size(max = 100, message = "debe tener como máximo 100 caracteres")
	private String nombre;

	@Pattern(regexp = ".*\\S.*", message = "no debe estar vacío")
	@Size(max = 100, message = "debe tener como máximo 100 caracteres")
	private String apellidos;

	@Pattern(regexp = ".*\\S.*", message = "no debe estar vacío")
	@Email(message = "no es un correo válido")
	@Size(max = 254, message = "debe tener como máximo 254 caracteres")
	private String correo;

	// "Investigador" o "Administrador".
	private String rol;

	// false desactiva la cuenta; true la reactiva. Las cuentas nunca se borran.
	private Boolean activo;

}
