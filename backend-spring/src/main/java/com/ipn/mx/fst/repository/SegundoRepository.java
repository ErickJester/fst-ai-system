package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Segundo;
import com.ipn.mx.fst.domain.SegundoId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SegundoRepository extends JpaRepository<Segundo, SegundoId> {

	// Resultados de un experimento para el reporte. Los totales por minuto no se guardan (D-52): se cuentan
	// aquí desde SEGUNDO. El minuto sale del número de segundo (1-60 es el minuto 1, 61-120 el 2, ...). Los segundos sin
	// conducta (nula: no se ve o no se decidió) vienen con conducta null y el PDF los suma como «Sin ver» (R49), así que
	// un minuto suma 60 cuando todo el minuto tiene segundos. nativeQuery = true: es SQL de PostgreSQL tal cual, no JPQL.
	@Query(value = """
			SELECT g.etiqueta                      AS grupo,
			       t.ordinal                       AS ordinal,
			       e.numerocilindro                AS cilindro,
			       ((s.segundo - 1) / 60) + 1      AS minuto,
			       s.conducta                      AS conducta,
			       CAST(COUNT(*) AS numeric)       AS segundos
			FROM segundo s
			JOIN observacion o ON o.idobservacion = s.idobservacion
			JOIN especimen   e ON e.idespecimen   = o.idespecimen
			JOIN tanda       t ON t.idtanda       = e.idtanda
			JOIN grupo       g ON g.idgrupo       = t.idgrupo
			WHERE g.idexperimento = :idExperimento
			GROUP BY g.etiqueta, t.ordinal, e.numerocilindro, ((s.segundo - 1) / 60) + 1, s.conducta
			ORDER BY g.etiqueta, t.ordinal, e.numerocilindro, minuto
			""", nativeQuery = true)
	List<FilaResultado> buscarResultados(@Param("idExperimento") Integer idExperimento);

	// Los especímenes del experimento con algún segundo que no es de la máquina (corregido, confirmado o dudoso): los que tuvieron
	// revisión humana. Cuenta también los segundos sin conducta (los marcados dudosos).
	@Query(value = """
			SELECT g.etiqueta        AS grupo,
			       t.ordinal         AS ordinal,
			       e.numerocilindro  AS cilindro
			FROM segundo s
			JOIN observacion o ON o.idobservacion = s.idobservacion
			JOIN especimen   e ON e.idespecimen   = o.idespecimen
			JOIN tanda       t ON t.idtanda       = e.idtanda
			JOIN grupo       g ON g.idgrupo       = t.idgrupo
			WHERE g.idexperimento = :idExperimento
			  AND s.origen <> 'maquina'
			GROUP BY g.etiqueta, t.ordinal, e.numerocilindro
			ORDER BY g.etiqueta, t.ordinal, e.numerocilindro
			""", nativeQuery = true)
	List<FilaEspecimenRevisado> buscarRevisados(@Param("idExperimento") Integer idExperimento);

}
