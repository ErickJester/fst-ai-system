package com.ipn.mx.fst.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Llave primaria compuesta de presenta: (idintervalo, conducta). JPA pide que una llave compuesta sea su
// propia clase, Serializable y con equals y hashCode (los genera @Data).
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class PresentaId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "idintervalo")
	private Integer idIntervalo;

	@Column(name = "conducta", length = 30)
	private String conducta;

}
