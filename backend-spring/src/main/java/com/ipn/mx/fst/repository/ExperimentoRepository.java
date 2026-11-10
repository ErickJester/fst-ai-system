// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// La entidad que maneja este repositorio.
import com.ipn.mx.fst.domain.Experimento;
import java.util.List;
// Optional: una caja que puede venir vacía, para cuando el experimento no existe.
import java.util.Optional;
// JpaRepository: la interfaz de Spring Data que trae hechos los métodos de acceso a una tabla.
import org.springframework.data.jpa.repository.JpaRepository;
// @Query: para escribir la consulta a mano. @Param: enlaza el parámetro con el hueco :id.
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Además del CRUD que da JpaRepository, dos consultas que Spring arma a partir del nombre del método.
//
// En este archivo conviven las tres formas de consultar de Spring Data, y por eso es buen sitio para
// verlas juntas: el CRUD heredado (save, findById, delete...), dos consultas derivadas del nombre, y dos
// @Query con SQL escrito a mano. Ver la explicación general en AdministradorRepository.
public interface ExperimentoRepository extends JpaRepository<Experimento, Integer> {

	// ¿Ya hay un experimento con este nombre? (para el POST)
	//
	// El nombre del experimento es único en todo el sistema (RF-11, y un UNIQUE en la columna). Se
	// comprueba ANTES de insertar para poder responder un 409 con un mensaje claro, en vez de dejar que
	// reviente la restricción de PostgreSQL con un error que el usuario no entendería.
	boolean existsByNombre(String nombre);

	// ¿Hay OTRO experimento con este nombre? (para el PUT: el propio experimento no cuenta)
	//
	// El sufijo "Not" del nombre del método es lo que excluye al propio experimento. Sin él, guardar un
	// experimento sin cambiarle el nombre daría "ese nombre ya existe", señalándose a sí mismo.
	// Es el mismo patrón de existsByCorreoIgnoreCaseAndIdInstitucionalNot en UsuarioRepository.
	boolean existsByNombreAndIdExperimentoNot(String nombre, Integer idExperimento);

	// Todos los experimentos con su creador, de los más recientes a los más antiguos (fecha y luego id, los dos descendentes).
	// Nativa y con proyección (FilaExperimento) en lugar de la entidad, para traer al creador en la misma consulta sin una
	// relación LAZY. Es para la lista: el estado no se guarda, se calcula, y el resumen cuenta todos, así que el servicio
	// filtra y pagina en Java.
	//
	// Esa última frase explica una decisión poco habitual: normalmente se filtra y se pagina en SQL, que
	// es mucho más eficiente. Aquí no se puede, por dos motivos juntos. Primero, el estado del experimento
	// no es una columna: se deduce de los análisis de sus videos, así que filtrar por estado en SQL
	// exigiría recalcularlo dentro de la consulta. Y segundo, el resumen de la pantalla (cuántos hay en
	// cada estado, qué años, qué tratamientos) es de TODOS los experimentos, con filtros o sin ellos.
	// Es un compromiso consciente, viable porque el número de experimentos de un laboratorio es pequeño.
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
			""", nativeQuery = true)
	// Notas de la consulta:
	//   AS "nombre" con comillas dobles: PostgreSQL pasaría los alias a minúsculas sin ellas, y dejarían
	//     de casar con los getters de FilaExperimento.
	//   to_char(e.fecha, 'YYYY-MM-DD'): la fecha sale ya como texto, lista para el JSON.
	//   JOIN usuario (sin LEFT): todo experimento tiene creador por obligación.
	//   ORDER BY fecha DESC, idexperimento DESC: el segundo criterio desempata los del mismo día, dejando
	//     arriba el creado más tarde. Sin él, dos experimentos de la misma fecha podrían salir en
	//     cualquier orden y cambiar de sitio entre una carga y otra.
	List<FilaExperimento> buscarTodos();

	// Un experimento con su creador, para el detalle.
	//
	// Es la misma consulta con un WHERE. Devuelve Optional porque el id puede no existir, y así el
	// servicio responde 404 en vez de tropezar con un null.
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
	// :id es un hueco con nombre que rellena @Param("id"). Nunca se concatena el valor dentro del texto
	// del SQL: así el controlador lo manda como parámetro y no hay forma de inyectar SQL.
	Optional<FilaExperimento> buscarFila(@Param("id") Integer id);

}
