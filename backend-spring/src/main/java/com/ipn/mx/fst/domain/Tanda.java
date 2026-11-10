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

// Tabla tanda: de 2 a 4 especímenes de un grupo grabados juntos. ordinal 1 = tanda A, 2 = B, ...
//
// La tanda es la unidad de grabación: como en el cuadro caben hasta cuatro cilindros, un grupo de doce
// especímenes se graba en tres tandas. Cada tanda produce un video, y de ese video salen las
// observaciones de los especímenes que aparecen en él.
@Data
@NoArgsConstructor
@Entity
@Table(name = "tanda")
public class Tanda implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// Llave primaria numérica que asigna PostgreSQL al insertar (IDENTITY).
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idtanda")
	private Integer idTanda;

	// El grupo al que pertenece la tanda. Las tres primeras anotaciones evitan que Lombok y Jackson
	// recorran la relación (ver la explicación larga en Administrador).
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	// optional = false: una tanda existe siempre dentro de un grupo.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idgrupo", nullable = false)
	private Grupo grupo;

	// El número de orden de la tanda dentro de su grupo. Se guarda como número (1, 2, 3) y se muestra como
	// letra (A, B, C): ordenar por número es trivial, por letra no lo sería.
	@Column(name = "ordinal", nullable = false)
	private Short ordinal;

	// De 2 a 4 (CHECK en el SQL).
	//
	// Cuántos cilindros se usaron en esta grabación. No siempre son cuatro: el último grupo puede quedar
	// incompleto. El tope lo impone PostgreSQL con un CHECK, no esta anotación; aquí solo se declara el
	// tipo. Que la regla viva en el SQL es justo lo que explica application.properties al defender
	// ddl-auto=validate: hay restricciones que Hibernate no sabe generar.
	@Column(name = "ncilindros", nullable = false)
	private Short nCilindros;

}
