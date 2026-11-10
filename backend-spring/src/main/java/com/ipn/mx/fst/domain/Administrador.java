// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @JsonIgnore: este campo NO sale en el JSON de la API.
import com.fasterxml.jackson.annotation.JsonIgnore;
// Las anotaciones de JPA, la norma de Java para guardar objetos en tablas. Hibernate es quien la implementa.
// @Column: enlaza un campo con una columna.
import jakarta.persistence.Column;
// @Entity: esta clase ES una tabla.
import jakarta.persistence.Entity;
// FetchType: cuándo se cargan los datos de una relación (al momento o al usarlos).
import jakarta.persistence.FetchType;
// @Id: el campo que es la llave primaria.
import jakarta.persistence.Id;
// @JoinColumn: la columna que guarda la llave foránea de una relación.
import jakarta.persistence.JoinColumn;
// @MapsId: "la llave primaria de esta tabla es la misma columna que la llave foránea".
import jakarta.persistence.MapsId;
// @OneToOne: relación uno a uno.
import jakarta.persistence.OneToOne;
// @Table: el nombre real de la tabla en la base.
import jakarta.persistence.Table;
// Serializable: interfaz marcadora; JPA pide que las entidades la implementen.
import java.io.Serializable;
// Lombok genera código repetitivo al compilar, para no escribirlo a mano:
// @Data: getters, setters, toString, equals y hashCode de todos los campos.
import lombok.Data;
// @EqualsAndHashCode.Exclude: deja un campo fuera de equals y hashCode.
import lombok.EqualsAndHashCode;
// @NoArgsConstructor: un constructor vacío.
import lombok.NoArgsConstructor;
// @ToString.Exclude: deja un campo fuera de toString.
import lombok.ToString;

// Tabla administrador: subtipo de usuario. Su llave primaria es la misma llave foránea a usuario
// (@MapsId), así que un usuario es Administrador si y solo si tiene una fila aquí.
//
// Es el patrón de herencia por tabla aparte: en vez de una columna "esAdmin" en usuario, ser administrador
// es TENER FILA en esta tabla. Por eso ConvertidorJwt decide el rol con administradorRepository.existsById.
// Ventaja: si mañana un administrador necesita campos propios, van aquí sin tocar la tabla usuario.
@Data
// JPA EXIGE un constructor sin argumentos para poder construir la entidad al leer de la base. Lombok lo
// genera con esta anotación; sin ella, Hibernate falla al arrancar.
@NoArgsConstructor
@Entity
@Table(name = "administrador")
public class Administrador implements Serializable {

	// El número de versión para la serialización de Java. No afecta al funcionamiento; se declara porque la
	// clase es Serializable y así el compilador no avisa.
	private static final long serialVersionUID = 1L;

	// La llave primaria. Es el mismo idInstitucional del usuario, no un número aparte.
	// length = 10 coincide con el VARCHAR(10) de la columna; ddl-auto=validate lo comprueba al arrancar.
	// Todo en minúsculas ("idinstitucional") porque PostgreSQL guarda así los nombres sin comillas, como
	// explica application.properties en la sección de la estrategia de nombres.
	@Id
	@Column(name = "idinstitucional", length = 10)
	private String idInstitucional;

	// Las relaciones se excluyen de toString, equals y hashCode (los genera @Data) para que Lombok no
	// recorra la relación y dispare consultas de más.
	//
	// El riesgo es real: sin estas exclusiones, imprimir un Administrador en el log cargaría el Usuario de
	// la base, y si el Usuario apuntara de vuelta se entraría en un ciclo infinito. Es un error clásico de JPA.
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	// LAZY: el Usuario NO se trae al leer el Administrador; solo si alguien llama a getUsuario().
	// Lo contrario (EAGER) traería el usuario entero en cada consulta, aunque no se use.
	@OneToOne(fetch = FetchType.LAZY)
	// @MapsId es la pieza clave: dice que la llave primaria de arriba se rellena con la llave de este
	// usuario. Por eso AdminInicial solo hace setUsuario(...) y no asigna el id a mano.
	@MapsId
	@JoinColumn(name = "idinstitucional")
	private Usuario usuario;

}
