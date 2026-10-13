package com.ipn.mx.fst.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla administrador: subtipo de usuario. Su llave primaria es la misma llave foránea a usuario
// (@MapsId), así que un usuario es Administrador si y solo si tiene una fila aquí.
@Data
@NoArgsConstructor
@Entity
@Table(name = "administrador")
public class Administrador implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "idinstitucional", length = 10)
	private String idInstitucional;

	// Las relaciones se excluyen de toString, equals y hashCode (los genera @Data) para que Lombok no
	// recorra la relación y dispare consultas de más.
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "idinstitucional")
	private Usuario usuario;

}
