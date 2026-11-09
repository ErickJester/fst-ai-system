package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ipn.mx.fst.utilerias.ErrorApi;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

// GET .../results/seconds sin base de datos: la consulta se simula con sus filas, que pasan por el RowMapper real. Se
// comprueba la forma de la respuesta, «sin ver» como conducta null, el origen crudo y los errores.
class SegundosDeResultadosTest {

	private static final ResultadosService.AlcanceDeReporte ALCANCE =
			new ResultadosService.AlcanceDeReporte(7, "Control", 10, 1, "A");

	private record Fila(int idGrupo, String grupo, int ordinal, int cilindro, int segundo, String conducta, String origen) {
	}

	private static SegundosDeResultados servicio(ResultadosService resultados, List<Fila> filas) {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
			RowMapper<?> mapper = inv.getArgument(1);
			List<Object> resultado = new ArrayList<>();
			for (int i = 0; i < filas.size(); i++) {
				resultado.add(mapper.mapRow(fila(filas.get(i)), i));
			}
			return resultado;
		});
		SegundosDeResultados segundos = new SegundosDeResultados();
		ReflectionTestUtils.setField(segundos, "jdbc", jdbc);
		ReflectionTestUtils.setField(segundos, "resultados", resultados);
		return segundos;
	}

	private static ResultSet fila(Fila f) {
		ResultSet rs = mock(ResultSet.class);
		try {
			Mockito.when(rs.getInt("idgrupo")).thenReturn(f.idGrupo());
			Mockito.when(rs.getString("etiqueta")).thenReturn(f.grupo());
			Mockito.when(rs.getInt("ordinal")).thenReturn(f.ordinal());
			Mockito.when(rs.getInt("numerocilindro")).thenReturn(f.cilindro());
			Mockito.when(rs.getInt("segundo")).thenReturn(f.segundo());
			Mockito.when(rs.getString("conducta")).thenReturn(f.conducta());
			Mockito.when(rs.getString("origen")).thenReturn(f.origen());
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
		return rs;
	}

	private static ResultadosService resultadosQueDaElAlcance() {
		ResultadosService resultados = mock(ResultadosService.class);
		when(resultados.alcanceDeReporte(1, 7, "A")).thenReturn(ALCANCE);
		return resultados;
	}

	@Test
	void la_respuesta_trae_la_duracion_el_grupo_la_letra_y_los_segundos_de_cada_espécimen() {
		List<Fila> filas = List.of(
				new Fila(7, "Control", 1, 1, 1, "nado activo", "maquina"),
				new Fila(7, "Control", 1, 1, 2, null, "dudosa"),
				new Fila(7, "Control", 1, 2, 1, "inmovilidad", "corregida"),
				new Fila(7, "Control", 1, 2, 2, "inmovilidad", "maquina"));
		Map<String, Object> datos = servicio(resultadosQueDaElAlcance(), filas).deUnaTanda(1, 7, "A");

		assertEquals(2, datos.get("duracionS"));
		assertEquals("A", datos.get("letra"));
		assertEquals(Map.of("id", 7, "etiqueta", "Control"), datos.get("grupo"));
		List<Map<String, Object>> especimenes = castear(datos.get("especimenes"));
		assertEquals(2, especimenes.size());
		assertEquals(1, especimenes.get(0).get("numeroCilindro"));
		List<Map<String, Object>> segundos = castear(especimenes.get(0).get("segundos"));
		assertEquals(1, segundos.get(0).get("s"));
		assertEquals("nado activo", segundos.get(0).get("conducta"));
		assertEquals("maquina", segundos.get(0).get("origen"));
		// «sin ver»: conducta null, con su origen crudo.
		assertNull(segundos.get(1).get("conducta"));
		assertEquals("dudosa", segundos.get(1).get("origen"));
		List<Map<String, Object>> segundosDelDos = castear(especimenes.get(1).get("segundos"));
		assertEquals("corregida", segundosDelDos.get(0).get("origen"));
	}

	@Test
	void una_tanda_sin_analisis_completado_responde_404() {
		SegundosDeResultados servicio = servicio(resultadosQueDaElAlcance(), List.of());
		ErrorApi error = assertThrows(ErrorApi.class, () -> servicio.deUnaTanda(1, 7, "A"));
		assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
	}

	@Test
	void sin_letra_responde_400() {
		SegundosDeResultados servicio = servicio(resultadosQueDaElAlcance(), List.of());
		ErrorApi error = assertThrows(ErrorApi.class, () -> servicio.deUnaTanda(1, 7, " "));
		assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
	}

	@Test
	void si_la_tanda_o_el_grupo_no_existen_el_error_de_alcance_sube_tal_cual() {
		ResultadosService resultados = mock(ResultadosService.class);
		when(resultados.alcanceDeReporte(1, 99, "A")).thenThrow(new ErrorApi(HttpStatus.NOT_FOUND, "No existe ese grupo"));
		SegundosDeResultados servicio = servicio(resultados, List.of());
		ErrorApi error = assertThrows(ErrorApi.class, () -> servicio.deUnaTanda(1, 99, "A"));
		assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
	}

	@SuppressWarnings("unchecked")
	private static <T> T castear(Object valor) {
		return (T) valor;
	}
}
