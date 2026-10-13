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

// Tabla tanda: de 2 a 4 especímenes de un grupo grabados juntos. ordinal 1 = tanda A, 2 = B, ...
@Data
@NoArgsConstructor
@Entity
@Table(name = "tanda")
public class Tanda implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idtanda")
	private Integer idTanda;

	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idgrupo", nullable = false)
	private Grupo grupo;

	@Column(name = "ordinal", nullable = false)
	private Short ordinal;

	// De 2 a 4 (CHECK en el SQL).
	@Column(name = "ncilindros", nullable = false)
	private Short nCilindros;

}
