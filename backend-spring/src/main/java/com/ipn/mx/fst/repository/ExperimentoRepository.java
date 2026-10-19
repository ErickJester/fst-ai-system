package com.ipn.mx.fst.repository;

import com.ipn.mx.fst.domain.Experimento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Además del CRUD que da JpaRepository, dos consultas que Spring arma a partir del nombre del método.
public interface ExperimentoRepository extends JpaRepository<Experimento, Integer> {

	// ¿Ya hay un experimento con este nombre? (para el POST)
	boolean existsByNombre(String nombre);

	// ¿Hay OTRO experimento con este nombre? (para el PUT: el propio experimento no cuenta)
	boolean existsByNombreAndIdExperimentoNot(String nombre, Integer idExperimento);

	// Una página de la lista: el experimento y su creador, de los más recientes a los más antiguos (fecha y
	// luego id, los dos descendentes). Nativa y con proyección (FilaExperimento) en lugar de la entidad, para
	// traer al creador en la misma consulta sin una relación LAZY. LIMIT y OFFSET los pone el servicio.
	@Query(value = """
			SELECT e.idexperimento                       AS "idExperimento",
			       e.nombre                              AS "nombre",
			       to_char(e.fecha, 'YYYY-MM-DD')        AS "fecha",
			       e.notas                               AS "notas",
			       u.idinstitucional                     AS "creadorId",
			       u.nombre                              AS "creadorNombre",
			       u.apellidos                           AS "creadorApellidos"
			FROM experimento e
			JOIN usuario u ON u.idinstitucional = e.idinstitucional
			ORDER BY e.fecha DESC, e.idexperimento DESC
			LIMIT :limite OFFSET :salto
			""", nativeQuery = true)
	List<FilaExperimento> buscarPagina(@Param("limite") int limite, @Param("salto") long salto);

	// Un experimento con su creador, para el detalle.
	@Query(value = """
			SELECT e.idexperimento                       AS "idExperimento",
			       e.nombre                              AS "nombre",
			       to_char(e.fecha, 'YYYY-MM-DD')        AS "fecha",
			       e.notas                               AS "notas",
			       u.idinstitucional                     AS "creadorId",
			       u.nombre                              AS "creadorNombre",
			       u.apellidos                           AS "creadorApellidos"
			FROM experimento e
			JOIN usuario u ON u.idinstitucional = e.idinstitucional
			WHERE e.idexperimento = :id
			""", nativeQuery = true)
	Optional<FilaExperimento> buscarFila(@Param("id") Integer id);

}
