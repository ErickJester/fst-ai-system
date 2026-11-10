// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @JsonIgnore: este campo NO sale en el JSON de la API.
import com.fasterxml.jackson.annotation.JsonIgnore;
// Anotaciones de JPA: columna, entidad, carga de relaciones, llave generada, llave primaria, llave foránea,
// relación muchos a uno y nombre de la tabla.
import jakarta.persistence.Column;
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
// Lombok: genera getters, setters, toString, equals, hashCode y el constructor vacío.
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla observacion: un espécimen en un video del Día 2. De ella cuelgan sus segundos (hasta 300).
//
// Es la tabla que une las dos ramas del modelo: por un lado la jerarquía del experimento
// (experimento → grupo → tanda → espécimen) y por otro los videos. Una observación es el cruce de "qué
// espécimen" con "en qué video", y es a ella a la que se le atribuyen los 300 segundos medidos.
// Solo del Día 2 porque el Día 1 es la sesión de entrenamiento y no se mide (ver SesionVideo).
// Si se midiera directo sobre el espécimen no habría forma de distinguir dos grabaciones del mismo animal.
@Data
@NoArgsConstructor
@Entity
@Table(name = "observacion")
public class Observacion implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// Llave primaria numérica que asigna PostgreSQL al insertar (IDENTITY).
	// Es el número que aparece dentro de SegundoId: cada segundo apunta aquí.
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idobservacion")
	private Integer idObservacion;

	// Primera mitad del cruce: QUÉ espécimen se observó. Las tres primeras anotaciones evitan que Lombok y
	// Jackson recorran la relación (ver la explicación larga en Administrador).
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idespecimen", nullable = false)
	private Especimen especimen;

	// Segunda mitad del cruce: EN QUÉ video. Las dos relaciones son obligatorias (optional = false):
	// una observación sin espécimen o sin video no significaría nada.
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idvideo", nullable = false)
	private Video video;

}
