package com.ipn.mx.fst.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ipn.mx.fst.domain.valores.Convertidores;
import com.ipn.mx.fst.domain.valores.FormatoReporte;
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

// Tabla reporte: el registro de un reporte guardado (PDF, CSV o XLSX) de un experimento, con su ruta y su fecha. Un
// experimento puede tener varios del mismo formato, uno por alcance (el experimento, cada grupo, cada tanda); la
// unicidad es por ruta. El archivo vive en el volumen y lo escribe y borra ReporteAlmacen; esta fila se invalida
// cuando cambia lo que el reporte muestra.
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

	// Dominio formato_reporte: 'CSV', 'XLSX' o 'PDF' (enum FormatoReporte).
	@Convert(converter = Convertidores.FormatoReporteConvertidor.class)
	@Column(name = "formato", columnDefinition = "formato_reporte", nullable = false)
	private FormatoReporte formato;

	@Column(name = "ruta", nullable = false, unique = true)
	private String ruta;

	@Column(name = "fechageneracion", nullable = false)
	private OffsetDateTime fechaGeneracion;

}
