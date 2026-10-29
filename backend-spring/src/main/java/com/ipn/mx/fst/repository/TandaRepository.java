package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Tanda;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TandaRepository extends JpaRepository<Tanda, Integer> {

	// Todas las tandas de varios experimentos, cada una con sus videos y el análisis de cada video, en UNA
	// sola consulta (la lista de experimentos la pide para los 20 de una página, sin una consulta por
	// experimento). Los LEFT JOIN dejan salir las tandas sin video. El orden es el del detalle: grupos por
	// id, tandas por ordinal y 'Dia 1' antes que 'Dia 2'. Los días desde el análisis se cuentan aquí, en la
	// base, para no depender de cómo entrega Hibernate la fecha con zona horaria.
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
	List<FilaTandaVideo> buscarTandasYVideos(@Param("ids") Collection<Integer> ids);

}
