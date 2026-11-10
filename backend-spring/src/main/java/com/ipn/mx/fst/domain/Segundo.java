// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @JsonIgnore: este campo NO sale en el JSON de la API.
import com.fasterxml.jackson.annotation.JsonIgnore;
// Los conversores del paquete valores y los dos enums que traduce esta entidad.
import com.ipn.mx.fst.domain.valores.Convertidores;
import com.ipn.mx.fst.domain.valores.OrigenSegundo;
import com.ipn.mx.fst.domain.valores.TipoConducta;
// Anotaciones de JPA.
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
// @EmbeddedId: la llave primaria no es un campo suelto, sino una clase @Embeddable (aquí, SegundoId).
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
// @MapsId: enlaza una relación con una parte de la llave compuesta.
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
// Serializable: interfaz marcadora que piden las entidades JPA.
import java.io.Serializable;
// Lombok: genera getters, setters, toString, equals, hashCode y el constructor vacío.
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla segundo: la conducta de un espécimen en cada segundo analizado (hasta 300 por observación).
// Es lo que se revisa y corrige en la pantalla de revisión.
//
// Es la tabla con diferencia más grande del sistema y donde está el dato que importa: 300 filas por cada
// espécimen de cada video. Un experimento con 4 grupos de 12 especímenes son 14 400 filas de estas.
// Por eso los resultados no se calculan recorriendo objetos en Java, sino con consultas agregadas en SQL
// (ver ResultadosServiceImpl): traerse 14 400 entidades a memoria para sumarlas sería absurdo.
@Data
@NoArgsConstructor
@Entity
@Table(name = "segundo")
public class Segundo implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// La llave primaria compuesta (idObservacion + segundo), en su propia clase. Ver SegundoId.
	// No lleva @GeneratedValue: los dos valores los pone quien inserta, no la base.
	@EmbeddedId
	private SegundoId id;

	// La observación a la que pertenece. Las tres primeras anotaciones evitan que Lombok y Jackson
	// recorran la relación (ver la explicación larga en Administrador).
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	// @MapsId("idObservacion") es la pieza fina: dice que esta relación RELLENA el campo idObservacion de
	// la llave compuesta. Sin ella, la misma columna estaría declarada dos veces (en la llave y en la
	// relación) y Hibernate no sabría cuál manda al escribir.
	@MapsId("idObservacion")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idobservacion")
	private Observacion observacion;

	// Dominio tipo_conducta: 'nado activo', 'inmovilidad', 'escalamiento' o 'conducta activa' (D-102, no es
	// tabla). Nula si el worker no decidió el segundo, si no se ve al espécimen o si el usuario lo marcó dudoso.
	// En Java es el enum TipoConducta; el conversor deja pasar el null.
	//
	// Fíjate en que NO lleva nullable = false, al revés que casi todas las demás columnas: el null aquí es
	// un valor legítimo y frecuente, no un dato que falte. Es lo que en los reportes aparece como "sin ver".
	@Convert(converter = Convertidores.TipoConductaConvertidor.class)
	@Column(name = "conducta", columnDefinition = "tipo_conducta")
	private TipoConducta conducta;

	// Dominio origen_segundo: 'maquina', 'confirmada', 'corregida' o 'dudosa' (enum OrigenSegundo).
	//
	// Esta sí es obligatoria: todo segundo tiene un origen, aunque no tenga conducta. Es la columna que
	// permite decir, en un artículo, cuántos segundos revisó una persona y cuántos quedaron como los dejó
	// el modelo.
	@Convert(converter = Convertidores.OrigenSegundoConvertidor.class)
	@Column(name = "origen", columnDefinition = "origen_segundo", nullable = false)
	private OrigenSegundo origen;

}
