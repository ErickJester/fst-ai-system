// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @Column: enlaza un campo con una columna.
import jakarta.persistence.Column;
// @Embeddable: esta clase no es una tabla, sino un bloque de campos que se INCRUSTA dentro de otra entidad.
import jakarta.persistence.Embeddable;
// Serializable: aquí no es opcional. JPA EXIGE que una llave compuesta sea serializable.
import java.io.Serializable;
// @AllArgsConstructor: Lombok genera un constructor con todos los campos.
import lombok.AllArgsConstructor;
// @Data: getters, setters, toString, equals y hashCode.
import lombok.Data;
// @NoArgsConstructor: constructor vacío, que JPA necesita.
import lombok.NoArgsConstructor;

// Llave primaria compuesta de segundo: (idobservacion, segundo).
//
// Qué es una llave compuesta: una llave primaria formada por DOS columnas en vez de una. Aquí tiene
// sentido porque un segundo no existe por su cuenta: es "el segundo 47 de la observación 128". No hace
// falta inventarle un identificador propio, porque esa pareja ya es única.
//
// Por qué una clase aparte: JPA no permite poner dos @Id en una entidad. La forma de declarar una llave
// compuesta es meter las dos columnas en una clase @Embeddable y usarla con @EmbeddedId en la entidad.
//
// @Data genera aquí equals y hashCode, y eso NO es un detalle: JPA los usa para saber si dos filas son la
// misma. Sin ellos, el mismo segundo leído dos veces parecería dos objetos distintos y el mapa de
// entidades de Hibernate se rompería.
@Data
@NoArgsConstructor
// El constructor con los dos campos permite escribir new SegundoId(128, (short) 47) en una sola línea,
// que es como lo usan los servicios al buscar un segundo concreto.
@AllArgsConstructor
@Embeddable
public class SegundoId implements Serializable {

	// Número de versión de la serialización de Java; obligatorio de hecho en una llave compuesta.
	private static final long serialVersionUID = 1L;

	// La observación a la que pertenece el segundo. Aquí es el número pelado, no la relación: una llave
	// compuesta guarda valores, no objetos.
	@Column(name = "idobservacion")
	private Integer idObservacion;

	// Del 1 al 300 (los últimos 300 s del video, renumerados).
	//
	// Es el dato clave para entender el sistema: la prueba mide los últimos cinco minutos del video, no el
	// video entero, y esos 300 segundos se renumeran del 1 al 300. Así dos videos de duración distinta se
	// comparan segundo a segundo. Short, porque 300 cabe de sobra y la columna es SMALLINT.
	@Column(name = "segundo")
	private Short segundo;

}
