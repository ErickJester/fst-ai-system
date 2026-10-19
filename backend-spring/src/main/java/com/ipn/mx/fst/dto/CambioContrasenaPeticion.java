package com.ipn.mx.fst.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Cuerpo de PATCH /auth/password: {"contrasenaActual", "contrasenaNueva"}. El largo mínimo de la nueva
// no se revisa aquí sino en el servicio, para responder con el mensaje completo y no con el formato
// "El campo '...' ..." de @Valid.
@Data
public class CambioContrasenaPeticion {

	@NotBlank(message = "no debe estar vacía")
	private String contrasenaActual;

	@NotBlank(message = "no debe estar vacía")
	private String contrasenaNueva;

}
