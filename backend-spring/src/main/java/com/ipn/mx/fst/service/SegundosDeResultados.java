// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// ErrorApi: la excepción con el código HTTP dentro.
import com.ipn.mx.fst.utilerias.ErrorApi;
import java.util.ArrayList;
// LinkedHashMap: conserva el orden de inserción, que aquí importa dos veces (ver abajo).
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
// JdbcTemplate: SQL directo. Hace falta porque la consulta se arma por partes.
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Los segundos de cada espécimen, uno por uno, desde la tabla SEGUNDO (ya con las correcciones guardadas). Solo cuentan los
// análisis completados. La conducta va cruda, con el nombre de la base; «sin ver» es conducta null, y el origen también va
// crudo (maquina, confirmada, corregida o dudosa). Lo usan GET .../results/seconds y el Excel del experimento.
//
// Es el nivel de detalle más fino que publica el sistema: el dato tal cual, sin agregar ni promediar.
// Compárese con ResultadosServiceImpl, que suma y calcula estadísticos: aquí no se interpreta nada.
// "Ya con las correcciones guardadas" lo distingue de la salida cruda del modelo (/dev/salida-modelo):
// esto SÍ lleva lo que corrigió una persona en la revisión. Los dos existen y no son lo mismo; confundirlos
// daría conclusiones falsas (ver la advertencia en ResultadosController).
// Que la conducta y el origen vayan "crudos" significa que son los textos de los dominios de la base (ver
// TipoConducta y OrigenSegundo): el cliente los traduce para mostrarlos.
@Service
public class SegundosDeResultados {

	// Una fila de la consulta: un segundo de un espécimen de una tanda.
	// Un record de solo datos. Es público porque ExcelDelExperimento lo recibe para escribir su hoja.
	public record FilaSegundo(int idGrupo, String grupo, String letra, int cilindro, int s, String conducta, String origen) {
	}

	// El SQL base, sin el WHERE variable ni el ORDER BY: los añade consulta() según el caso.
	// Los seis JOIN suben desde el análisis hasta el segundo, pasando por toda la jerarquía. Fíjate en el
	// de observacion: lleva DOS condiciones (idespecimen Y idvideo), porque un espécimen puede aparecer en
	// más de un video y la observación es justo el cruce de los dos (ver la entidad Observacion).
	// "a.estado = 'completado'" es lo que descarta los análisis a medias: sus segundos existirían pero
	// estarían incompletos, y publicarlos daría resultados falsos.
	private static final String CONSULTA = """
			SELECT g.idgrupo, g.etiqueta, t.ordinal, e.numerocilindro, s.segundo, s.conducta, s.origen
			FROM analisis a
			JOIN video v       ON v.idvideo = a.idvideo
			JOIN tanda t       ON t.idtanda = v.idtanda
			JOIN grupo g       ON g.idgrupo = t.idgrupo
			JOIN especimen e   ON e.idtanda = t.idtanda
			JOIN observacion o ON o.idespecimen = e.idespecimen AND o.idvideo = v.idvideo
			JOIN segundo s     ON s.idobservacion = o.idobservacion
			WHERE g.idexperimento = ? AND a.estado = 'completado'
			""";

	@Autowired
	private JdbcTemplate jdbc;

	// Se reutiliza para validar el alcance (grupo y tanda) con las mismas reglas y los mismos códigos que
	// los reportes, en vez de repetir aquí esas comprobaciones.
	@Autowired
	private ResultadosService resultados;

	// GET .../results/seconds: una tanda. 400 si no trae letra; 400/404 los que ya da alcanceDeReporte (letra no válida, grupo
	// o tanda que no existen en el experimento); 404 si la tanda existe pero todavía no tiene análisis completado.
	@Transactional(readOnly = true)
	public Map<String, Object> deUnaTanda(Integer idExperimento, Integer idGrupo, String letra) {
		// Este es el 400 que menciona SegundosController: el parámetro se declara opcional en el
		// controller justo para que la falta llegue hasta aquí y salga con este mensaje redactado, en
		// vez de con el 500 genérico de un @RequestParam obligatorio que falta.
		if (letra == null || letra.isBlank()) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "Falta la tanda: una letra de la A a la Z.");
		}
		// Valida y traduce: de la letra (A) al ordinal (1), comprobando de paso que el grupo y la tanda
		// existan en ese experimento. De aquí salen el 400 por letra inválida y el 404.
		ResultadosService.AlcanceDeReporte alcance = resultados.alcanceDeReporte(idExperimento, idGrupo, letra);
		List<FilaSegundo> filas = consulta(idExperimento, alcance.idGrupo(), alcance.ordinal());
		// La tanda existe (si no, alcanceDeReporte ya habría respondido 404), pero no tiene segundos:
		// su análisis no está completado. Es un 404 distinto, con su propio mensaje.
		if (filas.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "La tanda " + letra + " todavía no tiene análisis completado.");
		}

		// Las filas vienen planas, una por segundo, y hay que agruparlas por espécimen.
		// LinkedHashMap conserva el orden de llegada, que es el del ORDER BY de la consulta: así los
		// cilindros salen 1, 2, 3, 4 sin tener que ordenarlos otra vez.
		Map<Integer, List<Map<String, Object>>> porCilindro = new LinkedHashMap<>();
		int duracion = 0;
		for (FilaSegundo f : filas) {
			// computeIfAbsent crea la lista la primera vez que aparece ese cilindro y la reutiliza
			// después. Ahorra el típico "si no existe, créala" de dos líneas.
			porCilindro.computeIfAbsent(f.cilindro(), k -> new ArrayList<>()).add(segundo(f));
			// La duración es el número de segundo más alto que se vio. Se calcula sobre la marcha, en el
			// mismo recorrido, en vez de con una consulta aparte.
			duracion = Math.max(duracion, f.s());
		}
		// El mapa se convierte en una lista de objetos, que es la forma que espera el cliente.
		List<Map<String, Object>> especimenes = new ArrayList<>();
		for (Map.Entry<Integer, List<Map<String, Object>>> e : porCilindro.entrySet()) {
			Map<String, Object> especimen = new LinkedHashMap<>();
			especimen.put("numeroCilindro", e.getKey());
			especimen.put("segundos", e.getValue());
			especimenes.add(especimen);
		}

		// El grupo va como objeto anidado {id, etiqueta}, no como dos campos sueltos.
		Map<String, Object> grupo = new LinkedHashMap<>();
		grupo.put("id", alcance.idGrupo());
		grupo.put("etiqueta", alcance.etiqueta());
		// La respuesta final. El LinkedHashMap fija el orden de las claves en el JSON.
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("duracionS", duracion);
		datos.put("grupo", grupo);
		datos.put("letra", alcance.letra());
		datos.put("especimenes", especimenes);
		return datos;
	}

	// Todos los segundos de todos los análisis completados del experimento, en el orden del Excel: grupo, tanda, espécimen y
	// segundo. Vacío si no hay ninguno.
	//
	// Es la otra mitad del Excel del experimento (ver ReporteExcelExperimento). Aquí no se agrupa nada:
	// se devuelven las filas planas, que es justo lo que necesita una hoja de cálculo.
	// Y no lanza 404 si no hay nada: una lista vacía es una respuesta válida, y quien llama decide. De
	// hecho, el 409 de "no hay análisis completados" lo pone ReporteExcelExperimento, no esto.
	@Transactional(readOnly = true)
	public List<FilaSegundo> delExperimento(Integer idExperimento) {
		// Los dos null dicen "sin filtrar por grupo ni tanda": el experimento entero.
		return consulta(idExperimento, null, null);
	}

	// Un segundo como lo recibe el cliente: conducta null para «sin ver», origen tal cual.
	// El null se deja pasar a propósito: en el JSON sale como null, que es lo que significa "no se vio al
	// espécimen". Sustituirlo por un texto obligaría al cliente a distinguirlo de una conducta de verdad.
	private static Map<String, Object> segundo(FilaSegundo f) {
		Map<String, Object> segundo = new LinkedHashMap<>();
		segundo.put("s", f.s());
		segundo.put("conducta", f.conducta());
		segundo.put("origen", f.origen());
		return segundo;
	}

	// Una consulta para todo el experimento o para una tanda (idGrupo y ordinal juntos); los dos son enteros de la base.
	//
	// El SQL se arma por partes, pero fíjate en QUÉ se concatena: solo texto fijo escrito aquí. Los
	// valores van siempre como parámetros (?), nunca pegados dentro del texto. Esa es la diferencia entre
	// armar una consulta y abrir un agujero de inyección de SQL.
	private List<FilaSegundo> consulta(Integer idExperimento, Integer idGrupo, Integer ordinal) {
		String sql = CONSULTA;
		// Los parámetros se van juntando en el mismo orden que los ? del SQL.
		List<Object> parametros = new ArrayList<>();
		parametros.add(idExperimento);
		// Los dos juntos o ninguno: una tanda se identifica por grupo Y ordinal, el ordinal solo no basta
		// porque se repite en cada grupo.
		if (idGrupo != null && ordinal != null) {
			sql += " AND g.idgrupo = ? AND t.ordinal = ?";
			parametros.add(idGrupo);
			parametros.add(ordinal);
		}
		// El ORDER BY va al final, después del WHERE variable. Es el orden del Excel y el que permite
		// agrupar por cilindro más arriba sin reordenar nada.
		sql += " ORDER BY g.idgrupo, t.ordinal, e.numerocilindro, s.segundo";
		// La lambda es el RowMapper: convierte cada fila en un FilaSegundo.
		// getString devuelve null cuando la columna es NULL, que es justo lo que se quiere para conducta.
		return jdbc.query(sql, (rs, fila) -> new FilaSegundo(rs.getInt("idgrupo"), rs.getString("etiqueta"),
				letra(rs.getInt("ordinal")), rs.getInt("numerocilindro"), rs.getInt("segundo"), rs.getString("conducta"),
				rs.getString("origen")), parametros.toArray());
	}

	// 1 = A, 2 = B, ...
	//
	// Aritmética de caracteres: en Java un char es un número, así que 'A' + 0 es 'A', 'A' + 1 es 'B', y
	// así. El -1 ajusta porque los ordinales empiezan en 1 y el desplazamiento tiene que empezar en 0.
	// La conversión a char es necesaria: la suma da un int, y sin ella saldría el número 65 en vez de "A".
	private static String letra(int ordinal) {
		return String.valueOf((char) ('A' + ordinal - 1));
	}
}
