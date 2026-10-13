package com.ipn.mx.fst.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Llave primaria compuesta de segundo: (idobservacion, segundo).
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class SegundoId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "idobservacion")
	private Integer idObservacion;

	// Del 1 al 300 (los últimos 300 s del video, renumerados).
	@Column(name = "segundo")
	private Short segundo;

}
