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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla segundo: la conducta de un espécimen en cada segundo analizado (hasta 300 por observación).
// Es lo que se revisa y corrige en la pantalla de revisión.
@Data
@NoArgsConstructor
@Entity
@Table(name = "segundo")
public class Segundo implements Serializable {

	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private SegundoId id;

	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@MapsId("idObservacion")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idobservacion")
	private Observacion observacion;

	// Dominio tipo_conducta: 'nado activo', 'inmovilidad', 'escalamiento' o 'conducta activa' (D-102, no es
	// tabla). Nula si el worker no decidió el segundo, si no se ve al espécimen o si el usuario lo marcó dudoso.
	@Column(name = "conducta", columnDefinition = "tipo_conducta")
	private String conducta;

	// Dominio origen_segundo: 'maquina', 'confirmada', 'corregida' o 'dudosa'.
	@Column(name = "origen", columnDefinition = "origen_segundo", nullable = false)
	private String origen;

}
