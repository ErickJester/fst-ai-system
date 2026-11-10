// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @JsonIgnore: este campo NO sale en el JSON de la API.
import com.fasterxml.jackson.annotation.JsonIgnore;
// Los conversores del paquete valores y el enum que traduce este.
import com.ipn.mx.fst.domain.valores.Convertidores;
import com.ipn.mx.fst.domain.valores.FormatoReporte;
// Anotaciones de JPA: columna, conversor, entidad, carga de relaciones, llave generada, llave primaria,
// llave foránea, relación muchos a uno y nombre de la tabla.
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
// Serializable: interfaz marcadora que piden las entidades JPA.
import java.io.Serializable;
// OffsetDateTime: fecha y hora con zona horaria.
import java.time.OffsetDateTime;
// Lombok: genera getters, setters, toString, equals, hashCode y el constructor vacío.
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla reporte: el registro de un reporte guardado (PDF, CSV o XLSX) de un experimento, con su ruta y su fecha. Un
// experimento puede tener varios del mismo formato, uno por alcance (el experimento, cada grupo, cada tanda); la
// unicidad es por ruta. El archivo vive en el volumen y lo escribe y borra ReporteAlmacen; esta fila se invalida
// cuando cambia lo que el reporte muestra.
//
// En el fondo es una caché en disco. Armar un PDF con gráficas es caro, y el mismo reporte se descarga
// muchas veces: se genera una vez, se guarda el archivo y esta fila dice dónde está y de cuándo es.
// "Se invalida cuando cambia lo que el reporte muestra" es la parte delicada de toda caché: si alguien
// corrige segundos en la revisión, el PDF guardado quedó mal y hay que borrarlo. De eso se encarga
// ReporteAlmacen, que además invalida todos los PDF cuando cambia su VERSION_PDF.
@Data
@NoArgsConstructor
@Entity
@Table(name = "reporte")
public class Reporte implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// Llave primaria numérica que asigna PostgreSQL al insertar (IDENTITY).
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idreporte")
	private Integer idReporte;

	// El experimento del que habla el reporte. Las tres primeras anotaciones evitan que Lombok y Jackson
	// recorran la relación (ver la explicación larga en Administrador).
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

	// Dónde está el archivo dentro del volumen, en ruta relativa (como el archivo del video).
	// unique = true: la ruta es lo que identifica de verdad al reporte. No se puede usar la pareja
	// (experimento, formato), porque un mismo experimento tiene varios PDF: uno del experimento entero,
	// uno por grupo y uno por tanda. Lo que los distingue es justo la ruta.
	@Column(name = "ruta", nullable = false, unique = true)
	private String ruta;

	// Cuándo se generó el archivo. Es el dato con el que ReporteAlmacen decide si el reporte guardado
	// sigue sirviendo o hay que rehacerlo.
	@Column(name = "fechageneracion", nullable = false)
	private OffsetDateTime fechaGeneracion;

}
