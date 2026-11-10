// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// La entidad que maneja este repositorio.
import com.ipn.mx.fst.domain.Grupo;
// Collection: cualquier conjunto de elementos (lista, conjunto...). Se pide la interfaz más general para
// que quien llame pueda pasar lo que tenga a mano.
import java.util.Collection;
import java.util.List;
// JpaRepository: la interfaz de Spring Data que trae hechos los métodos de acceso a una tabla.
import org.springframework.data.jpa.repository.JpaRepository;
// @Query: para escribir la consulta a mano cuando el nombre del método no alcanza.
import org.springframework.data.jpa.repository.Query;
// @Param: enlaza un parámetro del método con un hueco :nombre de la consulta.
import org.springframework.data.repository.query.Param;

// Ver la explicación general de los repositorios en AdministradorRepository.
//
// Aquí aparece la tercera capacidad de Spring Data, después de los métodos ya hechos y las consultas
// derivadas del nombre: @Query con SQL escrito a mano. Se usa cuando la consulta tiene agregados, JOIN o
// cálculos que un nombre de método no puede expresar.
public interface GrupoRepository extends JpaRepository<Grupo, Integer> {

	// Los grupos de varios experimentos, en una sola consulta, cada uno con el ordinal de su siguiente tanda
	// (max(ordinal) + 1, o 1 si no tiene tandas). Sirve a la lista (nGrupos y tratamientos), al detalle y a
	// la búsqueda de grupos. Ordenados por id.
	//
	// "En una sola consulta" es el punto entero de este método. La alternativa natural sería recorrer los
	// 20 experimentos de la página pidiendo los grupos de cada uno: 20 consultas en vez de 1. Ese defecto
	// tiene nombre propio, el problema N+1, y es la causa más común de que una pantalla vaya lenta con JPA.
	@Query(value = """
			SELECT g.idexperimento                       AS "idExperimento",
			       g.idgrupo                             AS "idGrupo",
			       g.etiqueta                            AS "etiqueta",
			       g.tipo                                AS "tipo",
			       g.tratamiento                         AS "tratamiento",
			       COALESCE(MAX(t.ordinal), 0) + 1       AS "siguienteOrdinal"
			FROM grupo g
			LEFT JOIN tanda t ON t.idgrupo = g.idgrupo
			WHERE g.idexperimento IN (:ids)
			GROUP BY g.idexperimento, g.idgrupo, g.etiqueta, g.tipo, g.tratamiento
			ORDER BY g.idgrupo
			""", nativeQuery = true)
	// Notas de la consulta de arriba, línea por línea:
	//   """ ... """  es un bloque de texto de Java: el SQL se escribe en varias líneas sin concatenar nada.
	//   AS "idGrupo" con COMILLAS DOBLES: PostgreSQL pasa a minúsculas los nombres sin comillas, y Spring
	//     necesita que el alias case exactamente con el método del record FilaGrupo. Sin las comillas
	//     llegaría "idgrupo" y no enlazaría.
	//   LEFT JOIN tanda: con un JOIN normal, un grupo recién creado (sin tandas todavía) desaparecería de
	//     la lista. El LEFT lo conserva, con las columnas de tanda en null.
	//   COALESCE(MAX(t.ordinal), 0) + 1: el número que le tocaría a la próxima tanda. MAX da null cuando no
	//     hay tandas, y COALESCE lo convierte en 0, así que el resultado es 1. Calcularlo aquí evita tener
	//     que traerse las tandas solo para contarlas.
	//   IN (:ids): el hueco se rellena con la colección del parámetro; Spring expande la lista solo.
	//   GROUP BY con las cinco columnas: al usar MAX hay que agrupar por todo lo demás que se selecciona.
	//   nativeQuery = true: es SQL de PostgreSQL, no JPQL. Da acceso a COALESCE y a los nombres reales de
	//     las tablas, a cambio de atarse a este motor de base de datos.
	//
	// Devuelve List<FilaGrupo>, una proyección: no son entidades Grupo, sino filas con justo estas seis
	// columnas (una de ellas, siguienteOrdinal, que no existe en ninguna tabla).
	List<FilaGrupo> buscarGrupos(@Param("ids") Collection<Integer> ids);

}
