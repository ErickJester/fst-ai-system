// Carpeta lógica de esta clase: com/ipn/mx/fst/dto.
package com.ipn.mx.fst.dto;

// @Email: comprueba que el texto tenga forma de dirección de correo.
import jakarta.validation.constraints.Email;
// @NotBlank: rechaza null, el texto vacío y el que solo tiene espacios.
import jakarta.validation.constraints.NotBlank;
// @Data: Lombok genera getters, setters, toString, equals y hashCode.
import lombok.Data;

// Cuerpo de POST /auth/recover: {"correo"}.
//
// El primer paso de "olvidé mi contraseña": el usuario escribe su correo y el sistema le manda un enlace.
// Es una de las tres rutas públicas (permitAll en SeguridadConfig), porque quien la usa no puede iniciar
// sesión todavía.
// Un detalle de seguridad que está en AuthServiceImpl y no aquí: la respuesta es la misma exista o no la
// cuenta. Si dijera "ese correo no está registrado", cualquiera podría averiguar quién tiene cuenta.
// Ver el patrón de los DTO explicado en LoginPeticion.
@Data
public class RecuperarPeticion {

	// Un solo campo, con las dos validaciones de siempre: que venga algo y que tenga forma de correo.
	@NotBlank(message = "no debe estar vacío")
	@Email(message = "no es un correo válido")
	private String correo;

}
