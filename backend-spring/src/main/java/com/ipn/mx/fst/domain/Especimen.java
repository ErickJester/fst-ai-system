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

// Tabla especimen. Se distingue solo por su posición en el cuadro (numeroCilindro, de 1 a 4), no por el
// número del laboratorio (D-55). Como ese número se repite en cada tanda, se muestra junto con la tanda.
//
// Vale la pena entender la decisión: en el video se ven cuatro cilindros a la vez, y el sistema solo sabe
// "el del cilindro 2". Qué animal concreto estaba ahí es algo que lleva el laboratorio en su libreta, no
// la base. Por eso el espécimen no tiene nombre ni número propio: identificarlo exige decir también de
// qué tanda se habla, y de ahí la nota sobre mostrarlo junto con la tanda.
@Data
@NoArgsConstructor
@Entity
@Table(name = "especimen")
public class Especimen implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// Llave primaria numérica que asigna PostgreSQL al insertar (IDENTITY).
	// Es Integer y no int porque antes de guardar vale null.
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idespecimen")
	private Integer idEspecimen;

	// La tanda (la grabación) en la que aparece este espécimen. Las tres primeras anotaciones evitan que
	// Lombok y Jackson recorran la relación (ver la explicación larga en Administrador).
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	// optional = false: un espécimen existe siempre dentro de una tanda.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idtanda", nullable = false)
	private Tanda tanda;

	// La posición en el cuadro, del 1 al 4. Es lo ÚNICO que identifica al espécimen dentro de su tanda.
	// Short y no Integer porque el valor nunca pasa de 4: el tipo refleja el rango real del dato y
	// coincide con el SMALLINT de la columna, que es lo que comprueba ddl-auto=validate.
	@Column(name = "numerocilindro", nullable = false)
	private Short numeroCilindro;

}
