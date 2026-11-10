// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @JsonIgnore: este campo NO sale en el JSON de la API.
import com.fasterxml.jackson.annotation.JsonIgnore;
// Los conversores del paquete valores y el enum que traduce este.
import com.ipn.mx.fst.domain.valores.Convertidores;
import com.ipn.mx.fst.domain.valores.SesionVideo;
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
// BigDecimal: número decimal EXACTO, sin el error de redondeo de double. Ver la nota de la duración.
import java.math.BigDecimal;
// OffsetDateTime: fecha y hora CON zona horaria (el desfase respecto a UTC).
import java.time.OffsetDateTime;
// Lombok: genera getters, setters, toString, equals, hashCode y el constructor vacío.
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Tabla video: hasta dos por tanda, uno por sesión (Día 1 y Día 2). El archivo vive en el volumen, no en
// la base; aquí solo va su ruta.
//
// Guardar el archivo fuera de la base es lo normal con video: un .mp4 de 1 GB dentro de PostgreSQL haría
// lentos los respaldos y las consultas. La base guarda la ruta y el volumen guarda los bytes; el precio es
// que las dos cosas se pueden desincronizar, y por eso el campo archivo puede quedar en null.
@Data
@NoArgsConstructor
@Entity
@Table(name = "video")
public class Video implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// Llave primaria numérica que asigna PostgreSQL al insertar (IDENTITY).
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idvideo")
	private Integer idVideo;

	// La tanda que se grabó en este video. Las tres primeras anotaciones evitan que Lombok y Jackson
	// recorran la relación (ver la explicación larga en Administrador).
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idtanda", nullable = false)
	private Tanda tanda;

	// Dominio sesion_video: 'Dia 1' o 'Dia 2' (enum SesionVideo).
	// Junto con la tanda forma la pareja que no se repite: una tanda tiene como mucho un video por sesión.
	@Convert(converter = Convertidores.SesionVideoConvertidor.class)
	@Column(name = "sesion", columnDefinition = "sesion_video", nullable = false)
	private SesionVideo sesion;

	// Ruta relativa al volumen. Nula cuando el video ya se borró (a los 30 días o por falta de disco).
	//
	// RELATIVA a propósito (videos/{idVideo}.mp4, como dice application.properties): si el volumen se monta
	// en otra carpeta o el sistema se mueve de máquina, las rutas guardadas siguen sirviendo.
	// Que pueda ser null es parte del diseño: la fila del video sobrevive al archivo. Así se conservan los
	// resultados ya calculados aunque el video original ya no esté.
	@Column(name = "archivo")
	private String archivo;

	// Nombre del archivo como lo subió el usuario (por ejemplo "acu-yintang2.mp4"), solo para mostrarlo: NUNCA arma
	// rutas. Nulo en los videos anteriores al 20-oct-2026.
	//
	// La advertencia en mayúsculas es de seguridad: un nombre que viene del usuario puede traer "../" o
	// caracteres raros, y usarlo para construir una ruta dejaría escribir fuera del volumen. Por eso el
	// archivo real se guarda con el id, y este campo solo se enseña.
	// Nulo en los antiguos porque la columna se añadió después, con la migración idempotente de
	// migraciones.sql: las filas que ya existían se quedaron sin ese dato.
	@Column(name = "nombreoriginal", length = 255)
	private String nombreOriginal;

	// Duración en segundos.
	//
	// BigDecimal y no double: con double, 0.1 + 0.2 no da exactamente 0.3, y esos errores se acumulan al
	// sumar duraciones. precision = 8, scale = 2 significa 8 cifras en total y 2 decimales, es decir, hasta
	// 999999.99 segundos. Coincide con el NUMERIC(8,2) de la columna, que es lo que valida Hibernate.
	// La escribe VideoServiceImpl con lo que midió Ffprobe al subir el archivo.
	@Column(name = "duracion", precision = 8, scale = 2, nullable = false)
	private BigDecimal duracion;

	// Cuándo se subió. OffsetDateTime y no LocalDateTime: lleva la zona horaria, así que la hora sigue
	// significando lo mismo aunque el servidor y el navegador estén en husos distintos.
	// Es la fecha desde la que se cuentan los 30 días para borrar el video.
	@Column(name = "fechacarga", nullable = false)
	private OffsetDateTime fechaCarga;

}
