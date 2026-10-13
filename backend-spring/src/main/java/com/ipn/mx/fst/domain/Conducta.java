package com.ipn.mx.fst.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Tabla conducta: catálogo con cuatro valores semilla ('nado activo', 'inmovilidad', 'escalamiento' y
// 'conducta activa'). Es tabla y no dominio; la llave primaria es el propio nombre.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "conducta")
public class Conducta implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "nombre", length = 30)
	private String nombre;

}
