// Carpeta lógica de esta clase: com/ipn/mx/fst/dto.
package com.ipn.mx.fst.dto;

// @Email: comprueba que el texto tenga forma de dirección de correo.
import jakarta.validation.constraints.Email;
// @NotBlank: rechaza null, el texto vacío y el que solo tiene espacios.
import jakarta.validation.constraints.NotBlank;
// @Pattern: exige que el texto case con una expresión regular.
import jakarta.validation.constraints.Pattern;
// @Size: limita el largo del texto.
import jakarta.validation.constraints.Size;
// @Data: Lombok genera getters, setters, toString, equals y hashCode.
import lombok.Data;

// Cuerpo de POST /admin/users: {"idInstitucional", "nombre", "apellidos", "correo"}. Toda alta nace como Investigador:
// el ascenso a Administrador es solo por PATCH /admin/users/{id}. Un campo "rol" en el cuerpo se ignora.
// Los máximos son los de las columnas de la tabla usuario.
//
// Que "rol" se ignore no es un descuido: se ignora porque el campo NO EXISTE en esta clase, y Jackson
// descarta lo que no reconoce. Es la misma idea que en PerfilPeticion: lo que no se puede cambiar por una
// ruta, simplemente no se declara.
// Fíjate en que no hay campo de contraseña. La contraseña inicial no la escribe el administrador: el
// servicio genera una temporal, la manda por correo y marca la cuenta con cambioRequerido = true.
// Esta ruta vive bajo /admin/**, que SeguridadConfig reserva a hasRole("ADMINISTRADOR").
// Ver el patrón de los DTO explicado en LoginPeticion.
@Data
public class CrearCuentaPeticion {

	// El identificador se recorta ANTES de validar (el setter quita los espacios de los lados), así que "  2021  " es "2021".
	//
	// El orden importa: Jackson llama al setter al leer el JSON, y las validaciones corren después, sobre
	// el valor ya recortado. Si fuera al revés, "  2021  " fallaría el @Size de 10 por los espacios.
	@NotBlank(message = "no debe estar vacío")
	@Size(max = 10, message = "debe tener como máximo 10 caracteres")
	// "[0-9]*" admite solo dígitos. El * (cero o más) y no + (uno o más) porque del texto vacío ya se
	// encarga @NotBlank: cada anotación comprueba una cosa y los mensajes no se pisan.
	@Pattern(regexp = "[0-9]*", message = "solo puede llevar dígitos")
	private String idInstitucional;

	@NotBlank(message = "no debe estar vacío")
	@Size(max = 100, message = "debe tener como máximo 100 caracteres")
	private String nombre;

	@NotBlank(message = "no debe estar vacío")
	@Size(max = 100, message = "debe tener como máximo 100 caracteres")
	private String apellidos;

	// A este correo llega la contraseña temporal, así que un error de dedo aquí deja la cuenta inservible.
	@NotBlank(message = "no debe estar vacío")
	@Email(message = "no es un correo válido")
	@Size(max = 254, message = "debe tener como máximo 254 caracteres")
	private String correo;

	// El setter escrito a mano PISA el que habría generado @Data. Es la forma de meter el recorte de
	// espacios justo en el momento en que Jackson rellena el objeto, sin tener que acordarse de hacerlo
	// en el servicio. Lombok respeta los métodos que ya existen y no genera el suyo.
	// La comprobación de null es necesaria: si el campo no viene en el JSON, Jackson llama al setter con
	// null y trim() sobre null lanzaría NullPointerException antes de que @NotBlank pudiera responder 400.
	public void setIdInstitucional(String idInstitucional) {
		this.idInstitucional = idInstitucional == null ? null : idInstitucional.trim();
	}

}
