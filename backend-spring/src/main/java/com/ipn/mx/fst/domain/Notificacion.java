package com.ipn.mx.fst.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ipn.mx.fst.domain.valores.Convertidores;
import com.ipn.mx.fst.domain.valores.TipoNotificacion;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla notificacion: avisos de la campanita. Todavía no se usa: casi todas las crea el worker.
@Data
@NoArgsConstructor
@Entity
@Table(name = "notificacion")
public class Notificacion implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idnotificacion")
	private Integer idNotificacion;

	// Destinatario.
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idinstitucional", nullable = false)
	private Usuario usuario;

	// Opcional: la alerta de disco no es de ningún experimento.
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "idexperimento")
	private Experimento experimento;

	// Dominio tipo_notificacion: las seis de la guía (enum TipoNotificacion).
	@Convert(converter = Convertidores.TipoNotificacionConvertidor.class)
	@Column(name = "tipo", columnDefinition = "tipo_notificacion", nullable = false)
	private TipoNotificacion tipo;

	@Column(name = "mensaje", nullable = false)
	private String mensaje;

	@Column(name = "leido", nullable = false)
	private Boolean leido;

	@Column(name = "fechacreacion", nullable = false)
	private OffsetDateTime fechaCreacion;

}
