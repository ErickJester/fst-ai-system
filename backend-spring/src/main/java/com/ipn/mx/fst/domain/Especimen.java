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

// Tabla especimen. Se distingue solo por su posición en el cuadro (numeroCilindro, de 1 a 4), no por el
// número del laboratorio (D-55). Como ese número se repite en cada tanda, se muestra junto con la tanda.
@Data
@NoArgsConstructor
@Entity
@Table(name = "especimen")
public class Especimen implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idespecimen")
	private Integer idEspecimen;

	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idtanda", nullable = false)
	private Tanda tanda;

	@Column(name = "numerocilindro", nullable = false)
	private Short numeroCilindro;

}
