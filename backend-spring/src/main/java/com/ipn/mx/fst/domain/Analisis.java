package com.ipn.mx.fst.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla analisis: uno por video del Día 2 (idvideo es UNIQUE). Es también la cola del worker: el worker
// toma el análisis 'en cola' más antiguo. El backend solo escribe estado, etapa y nivelClasif al encolar,
// al reiniciar y en la pausa (sección 4.7 de la guía); todo lo demás lo escribe el worker.
@Data
@NoArgsConstructor
@Entity
@Table(name = "analisis")
public class Analisis implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idanalisis")
	private Integer idAnalisis;

	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idvideo", nullable = false, unique = true)
	private Video video;

	// Dominio estado_analisis: 'en cola', 'procesando', 'en pausa', 'completado', 'error' o 'cancelado'.
	// columnDefinition con el nombre del dominio: PostgreSQL reporta la columna con ese tipo y no como
	// varchar, y sin esto "validate" no deja arrancar. Igual en las demás columnas con dominio.
	@Column(name = "estado", columnDefinition = "estado_analisis", nullable = false)
	private String estado;

	// Dominio etapa_analisis: 'preprocesamiento', 'cilindros' o 'clasificacion'.
	@Column(name = "etapa", columnDefinition = "etapa_analisis", nullable = false)
	private String etapa;

	// Dominio nivel_clasif_analisis: 'preciso' o 'agrupado'.
	@Column(name = "nivelclasif", columnDefinition = "nivel_clasif_analisis", nullable = false)
	private String nivelClasif;

	// Al encolar, la fecha de encolado; al terminar, la reemplaza el worker (D-79).
	@Column(name = "fechaanalisis", nullable = false)
	private OffsetDateTime fechaAnalisis;

	// Ruta del PDF de diagnóstico. Solo se llena si estado = 'error'.
	@Column(name = "rutadiagnostico")
	private String rutaDiagnostico;

}
