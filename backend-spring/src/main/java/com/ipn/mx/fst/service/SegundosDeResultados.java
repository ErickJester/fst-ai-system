package com.ipn.mx.fst.service;

import com.ipn.mx.fst.utilerias.ErrorApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Los segundos de cada espécimen, uno por uno, desde la tabla SEGUNDO (ya con las correcciones guardadas). Solo cuentan los
// análisis completados. La conducta va cruda, con el nombre de la base; «sin ver» es conducta null, y el origen también va
// crudo (maquina, confirmada, corregida o dudosa). Lo usan GET .../results/seconds y el Excel del experimento.
@Service
public class SegundosDeResultados {

	// Una fila de la consulta: un segundo de un espécimen de una tanda.
	public record FilaSegundo(int idGrupo, String grupo, String letra, int cilindro, int s, String conducta, String origen) {
	}

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

	@Autowired
	private ResultadosService resultados;

	// GET .../results/seconds: una tanda. 400 si no trae letra; 400/404 los que ya da alcanceDeReporte (letra no válida, grupo
	// o tanda que no existen en el experimento); 404 si la tanda existe pero todavía no tiene análisis completado.
	@Transactional(readOnly = true)
	public Map<String, Object> deUnaTanda(Integer idExperimento, Integer idGrupo, String letra) {
		if (letra == null || letra.isBlank()) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "Falta la tanda: una letra de la A a la Z.");
		}
		ResultadosService.AlcanceDeReporte alcance = resultados.alcanceDeReporte(idExperimento, idGrupo, letra);
		List<FilaSegundo> filas = consulta(idExperimento, alcance.idGrupo(), alcance.ordinal());
		if (filas.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "La tanda " + letra + " todavía no tiene análisis completado.");
		}

		Map<Integer, List<Map<String, Object>>> porCilindro = new LinkedHashMap<>();
		int duracion = 0;
		for (FilaSegundo f : filas) {
			porCilindro.computeIfAbsent(f.cilindro(), k -> new ArrayList<>()).add(segundo(f));
			duracion = Math.max(duracion, f.s());
		}
		List<Map<String, Object>> especimenes = new ArrayList<>();
		for (Map.Entry<Integer, List<Map<String, Object>>> e : porCilindro.entrySet()) {
			Map<String, Object> especimen = new LinkedHashMap<>();
			especimen.put("numeroCilindro", e.getKey());
			especimen.put("segundos", e.getValue());
			especimenes.add(especimen);
		}

		Map<String, Object> grupo = new LinkedHashMap<>();
		grupo.put("id", alcance.idGrupo());
		grupo.put("etiqueta", alcance.etiqueta());
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("duracionS", duracion);
		datos.put("grupo", grupo);
		datos.put("letra", alcance.letra());
		datos.put("especimenes", especimenes);
		return datos;
	}

	// Todos los segundos de todos los análisis completados del experimento, en el orden del Excel: grupo, tanda, espécimen y
	// segundo. Vacío si no hay ninguno.
	@Transactional(readOnly = true)
	public List<FilaSegundo> delExperimento(Integer idExperimento) {
		return consulta(idExperimento, null, null);
	}

	// Un segundo como lo recibe el cliente: conducta null para «sin ver», origen tal cual.
	private static Map<String, Object> segundo(FilaSegundo f) {
		Map<String, Object> segundo = new LinkedHashMap<>();
		segundo.put("s", f.s());
		segundo.put("conducta", f.conducta());
		segundo.put("origen", f.origen());
		return segundo;
	}

	// Una consulta para todo el experimento o para una tanda (idGrupo y ordinal juntos); los dos son enteros de la base.
	private List<FilaSegundo> consulta(Integer idExperimento, Integer idGrupo, Integer ordinal) {
		String sql = CONSULTA;
		List<Object> parametros = new ArrayList<>();
		parametros.add(idExperimento);
		if (idGrupo != null && ordinal != null) {
			sql += " AND g.idgrupo = ? AND t.ordinal = ?";
			parametros.add(idGrupo);
			parametros.add(ordinal);
		}
		sql += " ORDER BY g.idgrupo, t.ordinal, e.numerocilindro, s.segundo";
		return jdbc.query(sql, (rs, fila) -> new FilaSegundo(rs.getInt("idgrupo"), rs.getString("etiqueta"),
				letra(rs.getInt("ordinal")), rs.getInt("numerocilindro"), rs.getInt("segundo"), rs.getString("conducta"),
				rs.getString("origen")), parametros.toArray());
	}

	// 1 = A, 2 = B, ...
	private static String letra(int ordinal) {
		return String.valueOf((char) ('A' + ordinal - 1));
	}
}
