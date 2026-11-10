// Carpeta lógica de esta clase: com/ipn/mx/fst/dto.
package com.ipn.mx.fst.dto;

// @Email: comprueba que el texto tenga forma de dirección de correo.
import jakarta.validation.constraints.Email;
// @NotBlank: rechaza null, el texto vacío y el que solo tiene espacios.
import jakarta.validation.constraints.NotBlank;
// @Data: Lombok genera getters, setters, toString, equals y hashCode.
import lombok.Data;

// Cuerpo de POST /auth/login: {"correo", "contrasena"}.
//
// Qué es un DTO (Data Transfer Object): una clase que existe solo para dar forma a los datos que entran o
// salen de la API. No es una entidad: no tiene @Entity, no se guarda en ninguna tabla y no tiene id.
// Por qué no se reutiliza la entidad Usuario para esto: el cuerpo del login trae la contraseña EN CLARO,
// que no existe como campo de Usuario (allí solo vive el hash), y no trae nombre ni apellidos. Un DTO deja
// escribir exactamente los campos que esa petición acepta, ni uno más. Así, un cliente que mandara
// "activo": true o "cambioRequerido": false en el cuerpo no tocaría nada: esos campos no existen aquí.
// Todas las clases de este paquete siguen el mismo patrón y se llaman XxxPeticion.
@Data
public class LoginPeticion {

	// Si falla, Spring responde 400 y ManejadorErrores arma el mensaje juntando el nombre del campo con
	// este texto, de ahí que esté redactado para leerse detrás: "El campo 'correo' no debe estar vacío".
	// Las dos anotaciones se acumulan: tiene que no estar vacío Y tener forma de correo.
	@NotBlank(message = "no debe estar vacío")
	@Email(message = "no es un correo válido")
	private String correo;

	// "vacía" en femenino, concordando con "contraseña": los mensajes están escritos para el usuario final.
	// La contraseña llega en claro y viaja solo en memoria: AuthServiceImpl la compara contra el hash con
	// bcrypt y la descarta. Nunca se registra en el log ni se guarda.
	@NotBlank(message = "no debe estar vacía")
	private String contrasena;

}
