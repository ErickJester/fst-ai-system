package com.ipn.mx.fst.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla presenta: cuántos segundos de cada conducta hubo en un minuto (intervalo). Cada intervalo tiene
// exactamente cuatro filas, una por conducta, incluso con 0 segundos. Es el resumen por minuto de segundo.
@Data
@NoArgsConstructor
@Entity
@Table(name = "presenta")
public class Presenta implements Serializable {

	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private PresentaId id;

	// @MapsId: la columna de la relación es la misma que la parte "idIntervalo" de la llave compuesta.
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@MapsId("idIntervalo")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idintervalo")
	private Intervalo intervalo;

	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@MapsId("conducta")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conducta")
	private Conducta conducta;

	// De 0 a 60 (CHECK en el SQL).
	@Column(name = "segundos", precision = 6, scale = 2, nullable = false)
	private BigDecimal segundos;

}
