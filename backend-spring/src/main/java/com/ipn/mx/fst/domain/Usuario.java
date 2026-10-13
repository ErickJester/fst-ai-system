package com.ipn.mx.fst.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Tabla usuario. La llave primaria es el identificador institucional (boleta o número de empleado),
// no un número autogenerado. Un Administrador es un usuario que además aparece en la tabla administrador.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuario")
public class Usuario implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "idinstitucional", length = 10)
	private String idInstitucional;

	@Column(name = "nombre", length = 100, nullable = false)
	private String nombre;

	@Column(name = "apellidos", length = 100, nullable = false)
	private String apellidos;

	@Column(name = "correo", length = 254, nullable = false, unique = true)
	private String correo;

	// Hash bcrypt. @JsonIgnore: nunca sale en una respuesta JSON.
	@JsonIgnore
	@Column(name = "contrasenahash", length = 255, nullable = false)
	private String contrasenaHash;

	// Dar de baja sin borrar: las llaves foráneas no dejan borrar a un usuario con experimentos.
	@Column(name = "activo", nullable = false)
	private Boolean activo;

	// Obliga a cambiar la contraseña temporal en el primer ingreso.
	@Column(name = "cambiorequerido", nullable = false)
	private Boolean cambioRequerido;

}
