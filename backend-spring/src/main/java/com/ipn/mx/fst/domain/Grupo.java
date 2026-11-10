// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @JsonIgnore: este campo NO sale en el JSON de la API.
import com.fasterxml.jackson.annotation.JsonIgnore;
// Los conversores del paquete valores y el enum que traduce este.
import com.ipn.mx.fst.domain.valores.Convertidores;
import com.ipn.mx.fst.domain.valores.TipoGrupo;
// Anotaciones de JPA. @Column enlaza campo y columna.
import jakarta.persistence.Column;
// @Convert: aplica un AttributeConverter a este campo (el puente con el dominio de PostgreSQL).
import jakarta.persistence.Convert;
// @Entity: esta clase es una tabla.
import jakarta.persistence.Entity;
// FetchType: cuándo se cargan los datos de una relación.
import jakarta.persistence.FetchType;
// @GeneratedValue y GenerationType: quién asigna la llave primaria y cómo.
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
// @Id: la llave primaria.
import jakarta.persistence.Id;
// @JoinColumn: la columna con la llave foránea.
import jakarta.persistence.JoinColumn;
// @ManyToOne: relación muchos a uno (muchos grupos pertenecen a un experimento).
import jakarta.persistence.ManyToOne;
// @Table: el nombre real de la tabla.
import jakarta.persistence.Table;
// Serializable: interfaz marcadora que piden las entidades JPA.
import java.io.Serializable;
// Lombok: genera getters, setters, toString, equals, hashCode y el constructor vacío.
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla grupo: control, referencia o tratamiento experimental dentro de un experimento.
// La etiqueta no se repite dentro del mismo experimento (UNIQUE en el SQL).
//
// El grupo es el segundo nivel de la jerarquía del sistema:
//   experimento → grupo → tanda → espécimen → observación → segundo
// Comparar grupos entre sí es el objetivo final de todo el proyecto.
@Data
@NoArgsConstructor
@Entity
@Table(name = "grupo")
public class Grupo implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// Llave primaria numérica.
	// IDENTITY: la asigna PostgreSQL al insertar (GENERATED ALWAYS AS IDENTITY en el SQL), no Java.
	// Por eso el campo es Integer y no int: antes de guardar vale null, y un int no puede ser null.
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idgrupo")
	private Integer idGrupo;

	// El experimento al que pertenece. Las tres primeras anotaciones evitan que Lombok y Jackson recorran
	// la relación (ver la explicación larga en Administrador).
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	// optional = false: todo grupo pertenece SIEMPRE a un experimento, nunca queda suelto.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	// nullable = false repite esa misma regla al nivel de la columna, que es lo que valida Hibernate
	// contra el esquema al arrancar.
	@JoinColumn(name = "idexperimento", nullable = false)
	private Experimento experimento;

	// El nombre que le pone el investigador ("Control", "Fluoxetina 10 mg"). Es único dentro del
	// experimento, pero esa regla la impone el UNIQUE del SQL, no esta anotación.
	@Column(name = "etiqueta", length = 100, nullable = false)
	private String etiqueta;

	// Dominio tipo_grupo: 'control', 'referencia' o 'tratamiento experimental'. Lo valida PostgreSQL; en Java es el enum
	// TipoGrupo y el conversor guarda su texto exacto.
	//
	// Las dos anotaciones trabajan juntas: @Convert traduce enum <-> texto, y columnDefinition le dice a
	// Hibernate que el tipo de la columna no es un VARCHAR cualquiera sino el DOMAIN tipo_grupo. Sin el
	// columnDefinition, ddl-auto=validate se quejaría al arrancar de que los tipos no coinciden.
	@Convert(converter = Convertidores.TipoGrupoConvertidor.class)
	@Column(name = "tipo", columnDefinition = "tipo_grupo", nullable = false)
	private TipoGrupo tipo;

	// Qué se le administró al grupo, en texto libre. Sin length: en la base es TEXT, sin tope.
	// Es uno de los campos por los que se puede filtrar la lista de experimentos (ver ExperimentoController).
	@Column(name = "tratamiento", nullable = false)
	private String tratamiento;

}
