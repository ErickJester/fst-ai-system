// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// La entidad y su llave primaria compuesta.
import com.ipn.mx.fst.domain.Segundo;
import com.ipn.mx.fst.domain.SegundoId;
import java.util.List;
// JpaRepository: la interfaz de Spring Data que trae hechos los métodos de acceso a una tabla.
import org.springframework.data.jpa.repository.JpaRepository;
// @Query: para escribir la consulta a mano. @Param: enlaza el parámetro con el hueco con nombre.
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Ver la explicación general de los repositorios en AdministradorRepository y la de @Query y las
// proyecciones en GrupoRepository.
//
// Fíjate en el segundo tipo: JpaRepository<Segundo, SegundoId>. Donde los demás repositorios ponen
// Integer o String, aquí va una clase entera, porque la llave de segundo es compuesta (ver SegundoId).
// Así, findById recibe un new SegundoId(idObservacion, segundo) en lugar de un número.
//
// Este es el repositorio de la tabla más grande del sistema: 300 filas por espécimen y por video. Por eso
// las dos consultas de abajo agregan con GROUP BY y devuelven resúmenes: traerse las filas una por una
// para sumarlas en Java sería inviable.
public interface SegundoRepository extends JpaRepository<Segundo, SegundoId> {

	// Resultados de un experimento para el reporte. Los totales por minuto no se guardan (D-52): se cuentan
	// aquí desde SEGUNDO. El minuto sale del número de segundo (1-60 es el minuto 1, 61-120 el 2, ...). Los segundos sin
	// conducta (nula: no se ve o no se decidió) vienen con conducta null y el PDF los suma como «Sin ver» (R49), así que
	// un minuto suma 60 cuando todo el minuto tiene segundos. nativeQuery = true: es SQL de PostgreSQL tal cual, no JPQL.
	//
	// La decisión D-52 (no guardar los totales por minuto) es la que mantiene sano el sistema: si los
	// totales estuvieran guardados, cada corrección manual en la pantalla de revisión obligaría a
	// recalcularlos y a mantenerlos sincronizados. Calculándolos siempre desde segundo, el dato nunca
	// puede quedar desfasado. El precio es esta consulta, que es la más cara del backend.
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
	// Notas de la consulta:
	//   ((s.segundo - 1) / 60) + 1  convierte el número de segundo en el minuto al que pertenece. El -1 y
	//     el +1 están para que la cuenta empiece en 1 y no en 0: el segundo 60 cae en el minuto 1 y el 61
	//     en el 2. Sin el -1, el segundo 60 se iría al minuto 2. Es división entera, que en SQL trunca.
	//   Los cuatro JOIN suben la jerarquía entera: segundo → observación → espécimen → tanda → grupo. Es
	//     el único camino desde el dato medido hasta el experimento al que pertenece.
	//   Aquí los alias NO llevan comillas, al revés que en las otras consultas: están escritos en
	//     minúsculas, así que PostgreSQL no los cambia y casan igual con los getters de FilaResultado.
	//   s.conducta puede ser NULL, y GROUP BY junta todos esos nulos en un grupo propio: así los segundos
	//     «sin ver» se cuentan aparte en vez de perderse.
	//   CAST(COUNT(*) AS numeric): el conteo es entero, pero sale como BigDecimal para que los promedios
	//     y porcentajes posteriores no tengan que convertir de tipo ni pierdan decimales.
	//   El ORDER BY deja las filas en el orden del reporte, así que quien las recorre no tiene que ordenar.
	List<FilaResultado> buscarResultados(@Param("idExperimento") Integer idExperimento);

	// Los especímenes del experimento con algún segundo que no es de la máquina (corregido, confirmado o dudoso): los que tuvieron
	// revisión humana. Cuenta también los segundos sin conducta (los marcados dudosos).
	//
	// La condición entera está en s.origen <> 'maquina': de los cuatro valores de OrigenSegundo, 'maquina'
	// es el único que escribe el worker; los otros tres los deja una persona al revisar. Por eso basta con
	// excluir ese uno para quedarse con todo lo que tocó un humano, incluidos los segundos confirmados
	// (donde la conducta no cambió, pero alguien la avaló).
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
	// El GROUP BY aquí no suma nada: sirve para quitar repetidos. Un espécimen con 40 segundos corregidos
	// saldría 40 veces sin él, y lo que interesa es la lista de especímenes, no cuántos segundos tiene
	// cada uno. Hace el mismo trabajo que un SELECT DISTINCT.
	// El texto 'maquina' va sin acento porque así está en el dominio origen_segundo de la base.
	List<FilaEspecimenRevisado> buscarRevisados(@Param("idExperimento") Integer idExperimento);

}
