// Carpeta lógica de esta clase: com/ipn/mx/fst/dto.
package com.ipn.mx.fst.dto;

// @NotBlank: rechaza null, el texto vacío y el que solo tiene espacios.
import jakarta.validation.constraints.NotBlank;
// @Data: Lombok genera getters, setters, toString, equals y hashCode.
import lombok.Data;

// Cuerpo de PATCH /auth/password: {"contrasenaActual", "contrasenaNueva", "aceptaTerminos"}. El largo mínimo de la nueva
// no se revisa aquí sino en el servicio, para responder con el mensaje completo y no con el formato
// "El campo '...' ..." de @Valid. aceptaTerminos es opcional: solo lo exige el servicio cuando la cuenta
// tiene una contraseña temporal (primer acceso); en un cambio normal se ignora. La aceptación no se guarda.
//
// Esta es la ÚNICA ruta que deja pasar FiltroCambioPendiente (junto con logout y /health) cuando la cuenta
// tiene contraseña temporal. Hasta que esta petición tenga éxito, el resto de la API responde 401.
// La nota sobre el largo mínimo enseña una regla práctica: @Valid produce mensajes con un formato fijo que
// antepone el nombre del campo. Cuando el mensaje tiene que leerse como una frase completa, la validación
// se hace a mano en el servicio y se lanza un ErrorApi con el texto exacto.
// Ver el patrón de los DTO explicado en LoginPeticion.
@Data
public class CambioContrasenaPeticion {

	// La contraseña de ahora. Se exige aunque el usuario ya tenga sesión abierta: así, alguien que
	// encuentre una sesión abierta no puede cambiar la contraseña sin conocer la anterior.
	@NotBlank(message = "no debe estar vacía")
	private String contrasenaActual;

	// La nueva. Solo se comprueba aquí que no venga vacía; el largo mínimo lo revisa el servicio.
	@NotBlank(message = "no debe estar vacía")
	private String contrasenaNueva;

	// Sin validación a propósito: es opcional y su regla depende del estado de la cuenta, algo que este
	// DTO no sabe. Solo el servicio puede comprobar si la cuenta está en primer acceso y exigirlo entonces.
	// Boolean (objeto) y no boolean (primitivo) justamente para poder distinguir tres casos: true, false y
	// "no vino en el cuerpo" (null). Con un boolean primitivo, no mandarlo sería indistinguible de false.
	private Boolean aceptaTerminos;

}
