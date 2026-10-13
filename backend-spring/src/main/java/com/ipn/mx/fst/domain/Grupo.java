package com.ipn.mx.fst.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla grupo: control, referencia o tratamiento experimental dentro de un experimento.
// La etiqueta no se repite dentro del mismo experimento (UNIQUE en el SQL).
@Data
@NoArgsConstructor
@Entity
@Table(name = "grupo")
public class Grupo implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idgrupo")
	private Integer idGrupo;

	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idexperimento", nullable = false)
	private Experimento experimento;

	@Column(name = "etiqueta", length = 100, nullable = false)
	private String etiqueta;

	// Dominio tipo_grupo: 'control', 'referencia' o 'tratamiento experimental'. Lo valida PostgreSQL.
	@Column(name = "tipo", columnDefinition = "tipo_grupo", nullable = false)
	private String tipo;

	@Column(name = "tratamiento", nullable = false)
	private String tratamiento;

}
