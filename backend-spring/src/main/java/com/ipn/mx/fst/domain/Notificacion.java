// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @JsonIgnore: este campo NO sale en el JSON de la API.
import com.fasterxml.jackson.annotation.JsonIgnore;
// Los conversores del paquete valores y el enum que traduce este.
import com.ipn.mx.fst.domain.valores.Convertidores;
import com.ipn.mx.fst.domain.valores.TipoNotificacion;
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

// Tabla notificacion: avisos de la campanita. Todavía no se usa: casi todas las crea el worker.
//
// "Casi todas las crea el worker" explica por qué esta entidad casi no aparece en el código del backend:
// el backend sobre todo las LEE (NotificacionController) y las marca como leídas. Quien las escribe es el
// proceso de Python cuando termina o falla un análisis.
@Data
@NoArgsConstructor
@Entity
@Table(name = "notificacion")
public class Notificacion implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// Llave primaria numérica que asigna PostgreSQL al insertar (IDENTITY).
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idnotificacion")
	private Integer idNotificacion;

	// Destinatario.
	// optional = false: una notificación siempre es PARA alguien. Las tres primeras anotaciones evitan que
	// Lombok y Jackson recorran la relación (ver la explicación larga en Administrador).
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idinstitucional", nullable = false)
	private Usuario usuario;

	// Opcional: la alerta de disco no es de ningún experimento.
	//
	// Fíjate en la diferencia con la relación de arriba: aquí NO hay optional = false ni nullable = false,
	// porque este campo puede quedar vacío. Cuatro de los seis tipos de TipoNotificacion hablan de un
	// análisis concreto y llenan este campo; ALERTA_DE_DISCO es del sistema entero y lo deja en null.
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "idexperimento")
	private Experimento experimento;

	// Dominio tipo_notificacion: las seis de la guía (enum TipoNotificacion).
	@Convert(converter = Convertidores.TipoNotificacionConvertidor.class)
	@Column(name = "tipo", columnDefinition = "tipo_notificacion", nullable = false)
	private TipoNotificacion tipo;

	// El texto que se le enseña al usuario, ya redactado por quien creó la notificación.
	// Sin length: en la base es TEXT, sin tope.
	@Column(name = "mensaje", nullable = false)
	private String mensaje;

	// Si el usuario ya la vio. Es lo único que cambia el backend de una notificación ya creada.
	@Column(name = "leido", nullable = false)
	private Boolean leido;

	// Cuándo se generó. Es el campo por el que se ordenan en la campanita, la más reciente arriba.
	@Column(name = "fechacreacion", nullable = false)
	private OffsetDateTime fechaCreacion;

}
