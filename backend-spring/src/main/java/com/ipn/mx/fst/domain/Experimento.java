// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @JsonIgnore: este campo NO sale en el JSON de la API.
import com.fasterxml.jackson.annotation.JsonIgnore;
// @JsonProperty: fija con qué nombre sale un campo en el JSON, cuando el automático no sirve.
import com.fasterxml.jackson.annotation.JsonProperty;
// Anotaciones de JPA: columna, entidad, carga de relaciones, llave generada, llave primaria, llave
// foránea, relación muchos a uno y nombre de la tabla.
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
// Las validaciones de Bean Validation. Spring las aplica solo cuando el parámetro del controller lleva
// @Valid, y si fallan responde 400 con la lista de errores (ver ManejadorErrores).
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
// Serializable: interfaz marcadora que piden las entidades JPA.
import java.io.Serializable;
// LocalDate: una fecha SIN hora ni zona. La fecha del experimento es un día del calendario, no un instante.
import java.time.LocalDate;
// AccessLevel: con @Setter(AccessLevel.NONE) se le dice a Lombok que NO genere el setter de un campo.
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
// @Formula: un campo que no es columna, sino una subconsulta SQL que Hibernate ejecuta al leer.
import org.hibernate.annotations.Formula;

// Tabla experimento. Igual que el Evento de la práctica, esta clase es a la vez la tabla y el JSON que
// entra y sale de la API en el POST y el PUT: {"idExperimento", "idInstitucional", "nombre", "fecha",
// "notas", "nGrupos"}. La lista y el detalle (GET /experiments y /experiments/{id}) ya NO salen de esta
// entidad: ExperimentoServiceImpl los arma con consultas agregadas, con la forma que lee el frontend.
//
// Esa última frase marca una evolución del proyecto que conviene entender: al principio la entidad servía
// para todo, como en la práctica de clase. Al crecer la pantalla (filtros, resumen, conteos por estado),
// devolver la entidad tal cual dejó de alcanzar, y la lista pasó a armarse con consultas hechas a medida.
// Hoy esta clase solo se usa para escribir (POST y PUT) y como punto de partida de las relaciones.
@Data
@NoArgsConstructor
@Entity
@Table(name = "experimento")
public class Experimento implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// IDENTITY: PostgreSQL asigna el número (GENERATED ALWAYS AS IDENTITY en el SQL).
	// Integer y no int: antes de guardar vale null, y un int no puede serlo.
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idexperimento")
	private Integer idExperimento;

	// Quién creó el experimento. Sale del token de sesión, no del cuerpo: si el POST trae otro valor, el
	// servicio lo sobrescribe. No lleva @NotBlank porque el cliente no lo manda.
	//
	// Es una regla de seguridad, no una comodidad: si se confiara en el cuerpo de la petición, cualquiera
	// podría crear experimentos a nombre de otro. El dato de verdad está en el token, que va firmado.
	@Size(max = 10, message = "debe tener como máximo 10 caracteres")
	@Column(name = "idinstitucional", length = 10, nullable = false)
	private String idInstitucional;

	// La misma columna, vista como relación, para leer los datos del creador. insertable y updatable en
	// false: la columna se escribe con idInstitucional, no con este campo.
	//
	// Es el patrón de tener el mismo dato dos veces: el texto pelado para escribir y la relación para leer
	// (y poder sacar el nombre del creador sin otra consulta a mano). Los dos false son obligatorios: sin
	// ellos, Hibernate vería dos campos peleándose por escribir la misma columna y fallaría al arrancar.
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "idinstitucional", insertable = false, updatable = false)
	private Usuario usuario;

	// Nombre único en todo el sistema (RF-11).
	// @NotBlank rechaza el vacío y el que solo trae espacios; el mensaje es el que acaba viendo el usuario
	// en la respuesta 400, por eso está redactado para leerse detrás del nombre del campo.
	// unique = true lo impone la base; el servicio comprueba antes para poder responder 409 con un mensaje
	// claro en vez de dejar que reviente la restricción.
	@NotBlank(message = "no debe estar vacío")
	@Size(max = 150, message = "debe tener como máximo 150 caracteres")
	@Column(name = "nombre", length = 150, nullable = false, unique = true)
	private String nombre;

	// @NotNull y no @NotBlank porque no es texto: una fecha o está o no está.
	// LocalDate: el día en que se hizo el experimento, sin hora. Compárese con los OffsetDateTime de otras
	// entidades, que sí marcan un instante exacto.
	@NotNull(message = "es obligatoria")
	@Column(name = "fecha", nullable = false)
	private LocalDate fecha;

	// Opcional.
	// Sin validación y sin nullable = false: puede llegar vacío o no llegar. Sin length, porque en la base
	// es TEXT.
	@Column(name = "notas")
	private String notas;

	// Cuántos grupos tiene. No es una columna: Hibernate lo calcula con esta subconsulta cada vez que lee
	// el experimento. Solo se lee (sin setter) y vale 0 en un experimento recién creado.
	//
	// @Formula mete SQL crudo dentro de la consulta que genera Hibernate. Evita traerse la lista entera de
	// grupos solo para contarlos. El precio es que ese SQL es de PostgreSQL y se ejecuta en CADA lectura
	// del experimento, aunque nadie vaya a mirar el conteo.
	// @Setter(AccessLevel.NONE) le quita a Lombok el setter: el valor lo pone la base, y permitir
	// escribirlo solo serviría para confundir.
	@Setter(AccessLevel.NONE)
	@Formula("(select count(*) from grupo g where g.idexperimento = idexperimento)")
	private Long nGrupos = 0L;

	// Lombok llamaría a este getter getNGrupos y Jackson lo escribiría como "ngrupos". Se escribe a mano
	// para que el JSON diga "nGrupos", como en el contrato con el frontend.
	//
	// Es un caso límite de las reglas de nombres de Java: con dos mayúsculas seguidas (getNGrupos), la
	// convención deja la "n" en minúscula y Jackson acaba produciendo "ngrupos". El frontend espera
	// "nGrupos", así que el nombre se fija a mano con @JsonProperty.
	@JsonProperty("nGrupos")
	public Long getNGrupos() {
		return nGrupos;
	}

}
