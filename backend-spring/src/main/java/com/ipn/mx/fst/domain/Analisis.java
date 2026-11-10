// Carpeta lógica de esta clase: com/ipn/mx/fst/domain.
package com.ipn.mx.fst.domain;

// @JsonIgnore: este campo NO sale en el JSON de la API.
import com.fasterxml.jackson.annotation.JsonIgnore;
// Los conversores del paquete valores y los tres enums que traduce esta entidad.
import com.ipn.mx.fst.domain.valores.Convertidores;
import com.ipn.mx.fst.domain.valores.EstadoAnalisis;
import com.ipn.mx.fst.domain.valores.EtapaAnalisis;
import com.ipn.mx.fst.domain.valores.NivelClasifAnalisis;
// Anotaciones de JPA: columna, conversor, entidad, carga de relaciones, llave generada, llave primaria,
// llave foránea, relación uno a uno y nombre de la tabla.
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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

// Tabla analisis: uno por video del Día 2 (idvideo es UNIQUE). Es también la cola del worker: el worker
// toma el análisis 'en cola' más antiguo. El backend solo escribe estado, etapa y nivelClasif al encolar,
// al reiniciar y en la pausa (sección 4.7 de la guía), y nivelClasif también al corregir segundos en la
// revisión; todo lo demás lo escribe el worker.
//
// Esta tabla es la frontera entre los dos programas del sistema. No hay una cola de mensajes ni un
// servicio aparte: la coordinación entre el backend (Java) y el worker (Python) es esta tabla.
// El backend inserta una fila 'en cola'; el worker la toma, la va marcando y escribe los resultados.
// Es importante tener clarísimo quién escribe qué, porque los dos programas tocan las mismas filas: el
// comentario de arriba delimita exactamente lo que le toca a cada uno.
@Data
@NoArgsConstructor
@Entity
@Table(name = "analisis")
public class Analisis implements Serializable {

	// Número de versión de la serialización de Java; se declara porque la clase es Serializable.
	private static final long serialVersionUID = 1L;

	// Llave primaria numérica que asigna PostgreSQL al insertar (IDENTITY).
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "idanalisis")
	private Integer idAnalisis;

	// El video que se analiza. Las tres primeras anotaciones evitan que Lombok y Jackson recorran la
	// relación (ver la explicación larga en Administrador).
	@JsonIgnore
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	// @OneToOne y no @ManyToOne, con unique = true en la columna: un video tiene como mucho UN análisis.
	// Reanalizar no crea una fila nueva, reinicia esta (de ahí "al reiniciar" en el comentario de arriba).
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "idvideo", nullable = false, unique = true)
	private Video video;

	// Dominio estado_analisis: 'en cola', 'procesando', 'en pausa', 'completado', 'error' o 'cancelado'.
	// columnDefinition con el nombre del dominio: PostgreSQL reporta la columna con ese tipo y no como
	// varchar, y sin esto "validate" no deja arrancar. Igual en las demás columnas con dominio. En Java es el enum
	// EstadoAnalisis (y EtapaAnalisis, NivelClasifAnalisis abajo); su conversor guarda el texto exacto del dominio.
	//
	// Es la columna por la que el worker busca trabajo: toma el 'en cola' más antiguo. Por eso un cambio de
	// estado desde el backend equivale a encolar, pausar o cancelar.
	@Convert(converter = Convertidores.EstadoAnalisisConvertidor.class)
	@Column(name = "estado", columnDefinition = "estado_analisis", nullable = false)
	private EstadoAnalisis estado;

	// Dominio etapa_analisis: 'preprocesamiento', 'cilindros' o 'clasificacion'.
	// Mientras el estado es 'procesando', esta columna dice por qué parte va, y es lo que alimenta la
	// barra de avance de la pantalla.
	@Convert(converter = Convertidores.EtapaAnalisisConvertidor.class)
	@Column(name = "etapa", columnDefinition = "etapa_analisis", nullable = false)
	private EtapaAnalisis etapa;

	// Dominio nivel_clasif_analisis: 'preciso' o 'agrupado'.
	// Es la única de las tres que el backend también cambia fuera del encolado: al corregir segundos en la
	// revisión, porque una corrección manual puede cambiar el detalle con el que quedó clasificado.
	@Convert(converter = Convertidores.NivelClasifAnalisisConvertidor.class)
	@Column(name = "nivelclasif", columnDefinition = "nivel_clasif_analisis", nullable = false)
	private NivelClasifAnalisis nivelClasif;

	// Al encolar, la fecha de encolado; al terminar, la reemplaza el worker (D-79).
	//
	// La columna tiene dos significados según el estado, que es poco común y conviene recordarlo: mientras
	// está en cola sirve para saber a quién le toca primero (el más antiguo), y una vez completado pasa a
	// significar cuándo se obtuvieron los resultados.
	@Column(name = "fechaanalisis", nullable = false)
	private OffsetDateTime fechaAnalisis;

	// Ruta del PDF de diagnóstico. Solo se llena si estado = 'error'.
	// Cuando el worker falla, deja un PDF explicando qué pasó, y esta columna apunta a él para que el
	// investigador pueda abrirlo. En cualquier otro estado queda en null.
	@Column(name = "rutadiagnostico")
	private String rutaDiagnostico;

}
