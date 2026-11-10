// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// Una fila de las consultas de experimentos de ExperimentoRepository: el experimento y su creador. Spring
// llena cada método con la columna del mismo nombre (el alias "AS" del SQL, entre comillas para que
// PostgreSQL conserve las mayúsculas). Es una proyección, no la entidad Experimento: así la lista y el
// detalle no dependen de relaciones LAZY.
//
// Ese último motivo merece desarrollarse, porque es el que justifica todo este paquete de proyecciones.
// La entidad Experimento tiene un campo usuario marcado LAZY: no se carga hasta que alguien lo pide. Si la
// lista devolviera entidades y luego quisiera el nombre del creador, Hibernate lanzaría una consulta por
// cada experimento (el problema N+1). Y como open-in-view está en false (ver application.properties), esa
// carga tardía ni siquiera funcionaría fuera de la transacción: daría LazyInitializationException.
// La proyección resuelve las dos cosas: el JOIN trae al creador en la misma consulta, y lo que sale es un
// objeto plano, sin relaciones que cargar después.
// Ver la explicación general de las proyecciones en FilaGrupo.
public interface FilaExperimento {

	Integer getIdExperimento();

	String getNombre();

	// AAAA-MM-DD, ya como texto
	// Formateada en la base con to_char, no como LocalDate: la fecha llega lista para el JSON y no hay
	// riesgo de que una conversión de zona horaria la corra un día.
	String getFecha();

	// Opcional: puede venir null, porque la columna lo admite.
	String getNotas();

	// --- Los tres datos del creador, traídos por el JOIN con usuario ---

	// El identificador institucional (la boleta), que es la llave de la tabla usuario.
	String getCreadorId();

	// Nombre y apellidos van por separado, como en la tabla. Juntarlos para mostrarlos es cosa de quien
	// arma la respuesta: la base guarda los datos, no el formato.
	String getCreadorNombre();

	String getCreadorApellidos();

}
