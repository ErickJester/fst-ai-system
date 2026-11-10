// Carpeta lógica de esta clase: com/ipn/mx/fst/dto.
package com.ipn.mx.fst.dto;

// @Email: comprueba que el texto tenga forma de dirección de correo.
import jakarta.validation.constraints.Email;
// @Pattern: exige que el texto case con una expresión regular.
import jakarta.validation.constraints.Pattern;
// @Size: limita el largo del texto.
import jakarta.validation.constraints.Size;
// @Data: Lombok genera getters, setters, toString, equals y hashCode.
import lombok.Data;

// Cuerpo de PATCH /admin/users/{id}: solo cambia lo que viene. Cualquier campo puede faltar; uno que viene vacío se rechaza.
// "idInstitucional" se acepta solo si es el mismo de la URL: el identificador es la llave de la cuenta y no se cambia.
//
// Este DTO enseña la diferencia entre PUT y PATCH. En un PUT se mandan todos los campos y se reemplaza el
// objeto entero; en un PATCH se mandan solo los que cambian. Por eso aquí NINGÚN campo lleva @NotBlank:
// que falte significa "no lo toques", no un error.
// Y de ahí viene el truco de abajo: hay que distinguir "no vino" de "vino vacío". @NotBlank no serviría,
// porque rechazaría también el campo ausente. La solución es @Pattern, que Bean Validation NO aplica
// cuando el valor es null, y que sí rechaza el texto vacío.
// Comparada con PerfilPeticion (lo que uno cambia de su propia cuenta), esta tiene dos campos más, rol y
// activo, que son justo los poderes de un administrador sobre la cuenta de otro.
// Ver el patrón de los DTO explicado en LoginPeticion.
@Data
public class ModificarCuentaPeticion {

	// Se admite en el cuerpo por comodidad del cliente (que manda el objeto entero de vuelta), pero el
	// servicio solo lo acepta si coincide con el de la URL. Cambiarlo sería cambiar la llave primaria de
	// la fila, y de ella cuelgan los experimentos.
	@Size(max = 10, message = "debe tener como máximo 10 caracteres")
	private String idInstitucional;

	// ".*\\S.*" significa: cualquier cosa, al menos un carácter que NO sea espacio, cualquier cosa.
	// Es decir, el texto tiene que traer algo que no sean espacios. Hace el trabajo de @NotBlank pero
	// dejando pasar el null, que es exactamente lo que necesita un PATCH.
	// En Java la barra se escribe doble ("\\S") porque la primera escapa a la segunda dentro del texto.
	@Pattern(regexp = ".*\\S.*", message = "no debe estar vacío")
	@Size(max = 100, message = "debe tener como máximo 100 caracteres")
	private String nombre;

	@Pattern(regexp = ".*\\S.*", message = "no debe estar vacío")
	@Size(max = 100, message = "debe tener como máximo 100 caracteres")
	private String apellidos;

	// @Email tampoco se aplica si el valor es null, así que las tres anotaciones conviven sin problema:
	// si no viene, no se comprueba nada; si viene, tiene que traer algo, ser un correo y caber en 254.
	@Pattern(regexp = ".*\\S.*", message = "no debe estar vacío")
	@Email(message = "no es un correo válido")
	@Size(max = 254, message = "debe tener como máximo 254 caracteres")
	private String correo;

	// "Investigador" o "Administrador".
	//
	// Sin validación aquí: el servicio comprueba el texto y además aplica ReglaAdministradores, que impide
	// quedarse sin ningún administrador en el sistema. Esa regla necesita consultar la base, algo que una
	// anotación no puede hacer.
	// Cambiar el rol es crear o borrar la fila de la tabla administrador (ver la entidad Administrador).
	private String rol;

	// false desactiva la cuenta; true la reactiva. Las cuentas nunca se borran.
	//
	// Boolean (objeto) y no boolean (primitivo) para poder distinguir los tres casos del PATCH: true,
	// false y "no vino" (null). Con un primitivo, no mandarlo equivaldría a mandar false y desactivaría
	// la cuenta sin que nadie lo pidiera.
	private Boolean activo;

}
