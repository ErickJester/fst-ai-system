package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Presenta;
import com.ipn.mx.fst.domain.PresentaId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PresentaRepository extends JpaRepository<Presenta, PresentaId> {

	// Resultados de un experimento para el reporte: recorre la jerarquía de abajo hacia arriba
	// (presenta, intervalo, observacion, especimen, tanda, grupo) y se queda con los del experimento.
	// nativeQuery = true: es SQL de PostgreSQL tal cual, no JPQL. Ordenado como se imprime en el PDF.
	@Query(value = """
			SELECT g.etiqueta        AS grupo,
			       t.ordinal         AS ordinal,
			       e.numerocilindro  AS cilindro,
			       i.minuto          AS minuto,
			       p.conducta        AS conducta,
			       p.segundos        AS segundos
			FROM presenta p
			JOIN intervalo   i ON i.idintervalo   = p.idintervalo
			JOIN observacion o ON o.idobservacion = i.idobservacion
			JOIN especimen   e ON e.idespecimen   = o.idespecimen
			JOIN tanda       t ON t.idtanda       = e.idtanda
			JOIN grupo       g ON g.idgrupo       = t.idgrupo
			WHERE g.idexperimento = :idExperimento
			ORDER BY g.etiqueta, t.ordinal, e.numerocilindro, i.minuto
			""", nativeQuery = true)
	List<FilaResultado> buscarResultados(@Param("idExperimento") Integer idExperimento);

}
