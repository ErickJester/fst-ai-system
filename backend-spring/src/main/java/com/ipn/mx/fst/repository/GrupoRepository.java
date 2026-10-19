package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Grupo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrupoRepository extends JpaRepository<Grupo, Integer> {

	// ¿El experimento ya tiene grupos? Mientras el CRUD no sepa borrar grupos, tandas y videos, un
	// experimento con grupos no se puede eliminar.
	boolean existsByExperimentoIdExperimento(Integer idExperimento);

	// Los grupos de varios experimentos, en una sola consulta, cada uno con el ordinal de su siguiente tanda
	// (max(ordinal) + 1, o 1 si no tiene tandas). Sirve a la lista (nGrupos y tratamientos), al detalle y a
	// la búsqueda de grupos. Ordenados por id.
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
	List<FilaGrupo> buscarGrupos(@Param("ids") Collection<Integer> ids);

}
