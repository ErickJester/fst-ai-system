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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla video: hasta dos por tanda, uno por sesión (Día 1 y Día 2). El archivo vive en el volumen, no en
// la base; aquí solo va su ruta.
@Data
@NoArgsConstructor
@Entity
@Table(name = "video")
public class Video implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idvideo")
	private Integer idVideo;

	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idtanda", nullable = false)
	private Tanda tanda;

	// Dominio sesion_video: 'Dia 1' o 'Dia 2'.
	@Column(name = "sesion", columnDefinition = "sesion_video", nullable = false)
	private String sesion;

	// Ruta relativa al volumen. Nula cuando el video ya se borró (a los 30 días o por falta de disco).
	@Column(name = "archivo")
	private String archivo;

	// Duración en segundos.
	@Column(name = "duracion", precision = 8, scale = 2, nullable = false)
	private BigDecimal duracion;

	@Column(name = "fechacarga", nullable = false)
	private OffsetDateTime fechaCarga;

}
