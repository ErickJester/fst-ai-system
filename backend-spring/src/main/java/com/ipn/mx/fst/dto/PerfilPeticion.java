// Carpeta lógica de esta clase: com/ipn/mx/fst/dto.
package com.ipn.mx.fst.dto;

// @Email: comprueba que el texto tenga forma de dirección de correo.
import jakarta.validation.constraints.Email;
// @NotBlank: rechaza null, el texto vacío y el que solo tiene espacios.
import jakarta.validation.constraints.NotBlank;
// @Size: limita el largo del texto.
import jakarta.validation.constraints.Size;
// @Data: Lombok genera getters, setters, toString, equals y hashCode.
import lombok.Data;

// Cuerpo de PATCH /users/me: {"nombre", "apellidos", "correo"}. Los máximos son los de las columnas de
// la tabla usuario.
//
// Lo que un usuario puede cambiar de SU PROPIA cuenta. Compárese con ModificarCuentaPeticion, que es lo
// que un administrador puede cambiar de la cuenta de otro: allí además están "rol" y "activo", y aquí no.
// Esa diferencia es la regla de seguridad escrita en el tipo: por esta ruta nadie se asciende a
// administrador ni se reactiva solo, porque esos campos sencillamente no existen en el cuerpo que acepta.
// La contraseña tampoco está aquí: tiene su propia ruta (PATCH /auth/password), porque exige la actual.
// Los máximos repiten los de las columnas de Usuario para rechazar en la API lo que la base rechazaría
// después, y así dar un 400 claro en vez de un error de base de datos.
// Ver el patrón de los DTO explicado en LoginPeticion.
@Data
public class PerfilPeticion {

	// Obligatorio: aunque la ruta sea PATCH, aquí los tres campos van siempre juntos.
	@NotBlank(message = "no debe estar vacío")
	@Size(max = 100, message = "debe tener como máximo 100 caracteres")
	private String nombre;

	@NotBlank(message = "no debe estar vacío")
	@Size(max = 100, message = "debe tener como máximo 100 caracteres")
	private String apellidos;

	// 254 es el largo máximo de una dirección de correo según la norma, y el de la columna.
	// Cambiar el correo tiene consecuencias: es con lo que se inicia sesión y a donde llegan los enlaces
	// de recuperación. Por eso el servicio comprueba además que no lo tenga ya otra cuenta.
	@NotBlank(message = "no debe estar vacío")
	@Email(message = "no es un correo válido")
	@Size(max = 254, message = "debe tener como máximo 254 caracteres")
	private String correo;

}
