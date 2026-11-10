// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// La entidad que maneja este repositorio.
import com.ipn.mx.fst.domain.Tanda;
// Collection y List: el conjunto de ids que entra y la lista de filas que sale.
import java.util.Collection;
import java.util.List;
// JpaRepository: la interfaz de Spring Data que trae hechos los métodos de acceso a una tabla.
import org.springframework.data.jpa.repository.JpaRepository;
// @Query: para escribir la consulta a mano. @Param: enlaza el parámetro con el hueco :ids.
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Ver la explicación general de los repositorios en AdministradorRepository, y la de @Query y las
// proyecciones en GrupoRepository.
public interface TandaRepository extends JpaRepository<Tanda, Integer> {

	// Todas las tandas de varios experimentos, cada una con sus videos y el análisis de cada video, en UNA
	// sola consulta (la lista de experimentos la pide para los 20 de una página, sin una consulta por
	// experimento). Los LEFT JOIN dejan salir las tandas sin video. El orden es el del detalle: grupos por
	// id, tandas por ordinal y 'Dia 1' antes que 'Dia 2'. Los días desde el análisis se cuentan aquí, en la
	// base, para no depender de cómo entrega Hibernate la fecha con zona horaria.
	//
	// Es la consulta que recorre media jerarquía del sistema de una vez: tanda → grupo → video → análisis.
	// Recorrer eso con objetos en Java dispararía cientos de consultas (el problema N+1 que se explica en
	// GrupoRepository).
	@Query(value = """
			SELECT g.idexperimento                       AS "idExperimento",
			       g.idgrupo                             AS "idGrupo",
			       t.idtanda                             AS "idTanda",
			       t.ordinal                             AS "ordinal",
			       t.ncilindros                          AS "cilindros",
			       v.idvideo                             AS "idVideo",
			       v.sesion                              AS "sesion",
			       v.duracion                            AS "duracion",
			       v.nombreoriginal                      AS "nombreOriginal",
			       to_char(v.fechacarga AT TIME ZONE 'America/Mexico_City', 'YYYY-MM-DD') AS "fecha",
			       (v.archivo IS NOT NULL)               AS "videoDisponible",
			       a.idanalisis                          AS "idAnalisis",
			       a.estado                              AS "estadoAnalisis",
			       FLOOR(EXTRACT(EPOCH FROM (now() - a.fechaanalisis)) / 86400) AS "diasDesdeAnalisis"
			FROM tanda t
			JOIN grupo g          ON g.idgrupo = t.idgrupo
			LEFT JOIN video v     ON v.idtanda = t.idtanda
			LEFT JOIN analisis a  ON a.idvideo = v.idvideo
			WHERE g.idexperimento IN (:ids)
			ORDER BY g.idgrupo, t.ordinal, v.sesion
			""", nativeQuery = true)
	// Notas de la consulta de arriba, en las líneas que no son una simple copia de columna:
	//   to_char(v.fechacarga AT TIME ZONE 'America/Mexico_City', 'YYYY-MM-DD'):
	//     la fecha de carga se guarda con zona horaria (OffsetDateTime). Se convierte a la hora de México
	//     y se formatea como texto AQUÍ, en la base. Si se mandara el instante crudo, un video subido a
	//     las 22:00 del día 5 podría mostrarse como día 6, según la zona del servidor o del navegador.
	//   (v.archivo IS NOT NULL) AS "videoDisponible":
	//     un booleano calculado. Recuerda que Video.archivo queda en null cuando el video ya se borró por
	//     antigüedad: la fila sobrevive al archivo. Esto le dice a la pantalla si hay algo que reproducir.
	//   FLOOR(EXTRACT(EPOCH FROM (now() - a.fechaanalisis)) / 86400):
	//     los días transcurridos desde el análisis. now() - fecha da un intervalo; EXTRACT(EPOCH...) lo
	//     pasa a segundos; dividir entre 86400 (los segundos de un día) y truncar da los días enteros.
	//     Se calcula en la base a propósito, como dice el comentario de arriba: así el número no depende
	//     de cómo Hibernate convierta la fecha ni de la zona horaria de la JVM.
	//   JOIN grupo (sin LEFT): toda tanda tiene grupo por obligación, así que un JOIN normal basta.
	//   LEFT JOIN video y analisis: una tanda puede no tener video todavía, y un video puede no tener
	//     análisis. Con JOIN normales esas filas desaparecerían y la pantalla no mostraría las tandas a
	//     las que aún falta subirles la grabación.
	//   ORDER BY ... v.sesion: 'Dia 1' va antes que 'Dia 2' por orden alfabético, que aquí coincide con
	//     el cronológico. Es el motivo de que SesionVideo guarde esos textos y no, por ejemplo, "Día uno".
	//
	// Una fila por combinación de tanda y video: una tanda con dos videos sale dos veces, y el servicio
	// las vuelve a juntar al armar la respuesta.
	List<FilaTandaVideo> buscarTandasYVideos(@Param("ids") Collection<Integer> ids);

}
