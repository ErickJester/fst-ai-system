// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// BigDecimal: número decimal exacto. La consulta convierte el conteo a numeric con CAST, ver abajo.
import java.math.BigDecimal;

// Una fila de la consulta de resultados de SegundoRepository: los segundos de una conducta, en un minuto,
// de un espécimen. Spring llena cada método con la columna del mismo nombre (el alias "AS" del SQL).
//
// Es la proyección que lleva el dato científico del sistema. Cada fila responde a una pregunta muy
// concreta: "del espécimen del cilindro 3 de la tanda B del grupo Control, en el minuto 2, ¿cuántos
// segundos estuvo inmóvil?". Con todas esas filas se arman las tablas, las medias y las gráficas.
// Es el resultado de agregar la tabla segundo, que puede tener decenas de miles de filas por experimento:
// la suma la hace PostgreSQL con GROUP BY y a Java solo llega el resumen.
// Ver la explicación general de las proyecciones en FilaGrupo.
public interface FilaResultado {

	// La etiqueta del grupo, no su id: la consulta agrupa por nombre porque es lo que se muestra.
	String getGrupo();

	// ordinal de la tanda: 1 = A, 2 = B, ...
	Number getOrdinal();

	// La posición del espécimen en el cuadro, de 1 a 4. Junto con grupo y ordinal identifica al espécimen
	// (recuerda que un espécimen no tiene nombre propio: ver la entidad Especimen).
	Number getCilindro();

	// El minuto, de 1 a 5. No es una columna: lo calcula la consulta a partir del número de segundo con
	// ((segundo - 1) / 60) + 1, porque los totales por minuto no se guardan (decisión D-52).
	Number getMinuto();

	// El texto crudo del dominio tipo_conducta ('nado activo', 'inmovilidad'...), o NULL.
	// El null es un valor esperado y frecuente: son los segundos en que no se ve al espécimen o quedaron
	// dudosos. En el PDF se suman aparte, bajo la etiqueta «Sin ver». Quien recorra estas filas tiene que
	// contemplarlo; tratarlo como un descuido sería perder datos reales.
	String getConducta();

	// Cuántos segundos de ese minuto tuvieron esa conducta. Es un COUNT(*), un número entero, pero la
	// consulta lo convierte con CAST(... AS numeric) para que llegue como BigDecimal: así no hay que
	// convertir de tipo al promediar y dividir más adelante, donde sí hacen falta decimales exactos.
	BigDecimal getSegundos();

}
