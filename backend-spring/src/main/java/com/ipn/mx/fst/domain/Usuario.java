// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @JsonIgnore: este campo NO sale en el JSON de la API. Aquí es una medida de seguridad, no un adorno.
import com.fasterxml.jackson.annotation.JsonIgnore;
// Anotaciones de JPA: columna, entidad, llave primaria y nombre de la tabla.
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
// Serializable: interfaz marcadora que piden las entidades JPA.
import java.io.Serializable;
// @AllArgsConstructor: Lombok genera un constructor con los siete campos, en el orden en que se declaran.
import lombok.AllArgsConstructor;
// @Data: getters, setters, toString, equals y hashCode.
import lombok.Data;
// @NoArgsConstructor: el constructor vacío que JPA necesita para leer de la base.
import lombok.NoArgsConstructor;

// Tabla usuario. La llave primaria es el identificador institucional (boleta o número de empleado),
// no un número autogenerado. Un Administrador es un usuario que además aparece en la tabla administrador.
//
// Usar la boleta como llave es una decisión de diseño: el identificador ya existe fuera del sistema, es
// único y no cambia, así que inventar un número aparte solo añadiría una traducción constante.
// Fíjate en que esta entidad NO tiene relaciones: no apunta a sus experimentos ni a su fila de
// administrador. Es a propósito. Las relaciones se recorren en el otro sentido (Experimento → Usuario), y
// así leer un usuario nunca arrastra media base detrás.
@Data
@NoArgsConstructor
// El constructor con todos los campos es el que usa AdminInicial para crear el primer administrador en
// una sola línea. El orden de los parámetros es el de los campos de abajo.
@AllArgsConstructor
@Entity
@Table(name = "usuario")
public class Usuario implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// La boleta o número de empleado. Sin @GeneratedValue: lo escribe quien crea la cuenta, no la base.
	// length = 10 es el tope que comprueba AdminInicial antes de insertar.
	@Id
	@Column(name = "idinstitucional", length = 10)
	private String idInstitucional;

	@Column(name = "nombre", length = 100, nullable = false)
	private String nombre;

	@Column(name = "apellidos", length = 100, nullable = false)
	private String apellidos;

	// unique = true: no puede haber dos cuentas con el mismo correo. Es lo que comprueba AdminInicial con
	// existsByCorreoIgnoreCase antes de insertar, para no chocar contra esta restricción.
	// 254 es la longitud máxima de una dirección de correo según la norma.
	@Column(name = "correo", length = 254, nullable = false, unique = true)
	private String correo;

	// Hash bcrypt. @JsonIgnore: nunca sale en una respuesta JSON.
	//
	// Es la anotación más importante del archivo. Sin ella, cualquier endpoint que devolviera un Usuario
	// publicaría el hash de su contraseña. Aquí no se guarda la contraseña, solo su hash: bcrypt es de una
	// sola dirección, así que ni siquiera el sistema puede recuperar la original.
	// 255 da espacio de sobra: un hash de bcrypt mide 60 caracteres.
	@JsonIgnore
	@Column(name = "contrasenahash", length = 255, nullable = false)
	private String contrasenaHash;

	// Dar de baja sin borrar: las llaves foráneas no dejan borrar a un usuario con experimentos.
	//
	// Es lo que se llama borrado lógico. Además de la razón técnica, hay una científica: borrar al autor de
	// un experimento dejaría datos sin responsable. ConvertidorJwt lee este campo en CADA petición, así que
	// dar de baja a alguien lo deja fuera al instante, sin esperar a que venza su token.
	// Boolean (objeto) y no boolean (primitivo) para que el campo pueda ser null antes de guardarse.
	@Column(name = "activo", nullable = false)
	private Boolean activo;

	// Obliga a cambiar la contraseña temporal en el primer ingreso.
	// Lo lee ConvertidorJwt para añadir la marca CAMBIO_PENDIENTE, y FiltroCambioPendiente es quien
	// entonces bloquea todo salvo cambiar la contraseña.
	@Column(name = "cambiorequerido", nullable = false)
	private Boolean cambioRequerido;

}
