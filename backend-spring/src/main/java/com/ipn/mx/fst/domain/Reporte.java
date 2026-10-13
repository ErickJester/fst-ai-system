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
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla reporte: el archivo de reporte guardado de un experimento, uno por formato. Todavía no se usa:
// por ahora el PDF se genera en memoria en cada descarga, como en la práctica.
@Data
@NoArgsConstructor
@Entity
@Table(name = "reporte")
public class Reporte implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idreporte")
	private Integer idReporte;

	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idexperimento", nullable = false)
	private Experimento experimento;

	// Dominio formato_reporte: 'CSV', 'XLSX' o 'PDF'.
	@Column(name = "formato", columnDefinition = "formato_reporte", nullable = false)
	private String formato;

	@Column(name = "ruta", nullable = false, unique = true)
	private String ruta;

	@Column(name = "fechageneracion", nullable = false)
	private OffsetDateTime fechaGeneracion;

}
