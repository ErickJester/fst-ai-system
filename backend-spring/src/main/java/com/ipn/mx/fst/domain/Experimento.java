package com.ipn.mx.fst.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Formula;

// Tabla experimento. Igual que el Evento de la práctica, esta clase es a la vez la tabla y el JSON que
// entra y sale de la API: {"idExperimento", "idInstitucional", "nombre", "fecha", "notas", "nGrupos"}.
@Data
@NoArgsConstructor
@Entity
@Table(name = "experimento")
public class Experimento implements Serializable {

	private static final long serialVersionUID = 1L;

	// IDENTITY: PostgreSQL asigna el número (GENERATED ALWAYS AS IDENTITY en el SQL).
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idexperimento")
	private Integer idExperimento;

	// Quién creó el experimento. Sale del token de sesión, no del cuerpo: si el POST trae otro valor, el
	// servicio lo sobrescribe. No lleva @NotBlank porque el cliente no lo manda.
	@Size(max = 10, message = "debe tener como máximo 10 caracteres")
	@Column(name = "idinstitucional", length = 10, nullable = false)
	private String idInstitucional;

	// La misma columna, vista como relación, para leer los datos del creador. insertable y updatable en
	// false: la columna se escribe con idInstitucional, no con este campo.
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "idinstitucional", insertable = false, updatable = false)
	private Usuario usuario;

	// Nombre único en todo el sistema (RF-11).
	@NotBlank(message = "no debe estar vacío")
	@Size(max = 150, message = "debe tener como máximo 150 caracteres")
	@Column(name = "nombre", length = 150, nullable = false, unique = true)
	private String nombre;

	@NotNull(message = "es obligatoria")
	@Column(name = "fecha", nullable = false)
	private LocalDate fecha;

	// Opcional.
	@Column(name = "notas")
	private String notas;

	// Cuántos grupos tiene. No es una columna: Hibernate lo calcula con esta subconsulta cada vez que lee
	// el experimento. Solo se lee (sin setter) y vale 0 en un experimento recién creado.
	@Setter(AccessLevel.NONE)
	@Formula("(select count(*) from grupo g where g.idexperimento = idexperimento)")
	private Long nGrupos = 0L;

	// Lombok llamaría a este getter getNGrupos y Jackson lo escribiría como "ngrupos". Se escribe a mano
	// para que el JSON diga "nGrupos", como en el contrato con el frontend.
	@JsonProperty("nGrupos")
	public Long getNGrupos() {
		return nGrupos;
	}

}
