// Carpeta lógica de esta clase: com/ipn/mx/fst/dto.
package com.ipn.mx.fst.dto;

// @NotBlank: rechaza null, el texto vacío y el que solo tiene espacios.
import jakarta.validation.constraints.NotBlank;
// @Data: Lombok genera getters, setters, toString, equals y hashCode.
import lombok.Data;

// Cuerpo de POST /auth/reset: {"token", "contrasenaNueva"}. El token es el del enlace del correo.
//
// El segundo paso de "olvidé mi contraseña": el usuario abre el enlace que le llegó y escribe su nueva
// contraseña. Es público (permitAll), porque el token del enlace hace de credencial.
// Ese token es un JWT aparte, no uno de sesión: SeguridadConfig exige el claim tipo = "sesion" para entrar
// a la API, y este no lo lleva, así que no sirve para nada más. Dura 60 minutos (RESET_LINK_MINUTES) y se
// usa una sola vez: lleva una huella del hash de la contraseña, de modo que al cambiarla deja de valer.
// Ver el patrón de los DTO explicado en LoginPeticion.
@Data
public class RestablecerPeticion {

	// El token que venía en la URL del correo. El frontend lo saca del enlace y lo manda aquí, en el
	// cuerpo y no en la URL: las URL quedan registradas en los logs del servidor, los cuerpos no.
	@NotBlank(message = "no debe estar vacío")
	private String token;

	// La contraseña nueva. Como en CambioContrasenaPeticion, aquí solo se comprueba que no venga vacía;
	// el largo mínimo lo revisa el servicio, para poder dar un mensaje redactado en vez del formato
	// automático de @Valid.
	@NotBlank(message = "no debe estar vacía")
	private String contrasenaNueva;

}
