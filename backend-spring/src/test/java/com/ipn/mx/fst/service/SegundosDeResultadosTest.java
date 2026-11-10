// Mismo paquete que la clase que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
// Los comparadores de Mockito: "cualquier objeto de este tipo" y "cualquier texto".
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ipn.mx.fst.utilerias.ErrorApi;
// ResultSet: la fila que devuelve una consulta JDBC. Aquí se simula.
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
//
// "Que pasan por el RowMapper real" es lo que da valor a esta prueba: no se simula la respuesta del
// servicio, se simula la BASE y se deja que el código de verdad convierta las filas y arme el resultado.
// Así se comprueba el agrupado por espécimen, el cálculo de la duración y la forma del JSON, sin
// PostgreSQL de por medio.
class SegundosDeResultadosTest {

	// El alcance que normalmente devolvería alcanceDeReporte: grupo 7 "Control", tanda 10, ordinal 1, letra A.
	private static final ResultadosService.AlcanceDeReporte ALCANCE =
			new ResultadosService.AlcanceDeReporte(7, "Control", 10, 1, "A");

	// Un record local con los campos de una fila, para escribir los datos de prueba de forma legible.
	private record Fila(int idGrupo, String grupo, int ordinal, int cilindro, int segundo, String conducta, String origen) {
	}

	// Construye el servicio con un JdbcTemplate simulado que devuelve las filas que se le pasen.
	private static SegundosDeResultados servicio(ResultadosService resultados, List<Fila> filas) {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		// thenAnswer, y no thenReturn: hace falta EJECUTAR el RowMapper real que el servicio le pasa a
		// jdbc.query. Es lo que permite probar la conversión de fila a objeto sin base de datos.
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
			// getArgument(1) es el segundo argumento de la llamada: el RowMapper.
			RowMapper<?> mapper = inv.getArgument(1);
			List<Object> resultado = new ArrayList<>();
			for (int i = 0; i < filas.size(); i++) {
				// Se le da cada fila simulada, como haría el controlador de la base.
				resultado.add(mapper.mapRow(fila(filas.get(i)), i));
			}
			return resultado;
		});
		SegundosDeResultados segundos = new SegundosDeResultados();
		// Los dos campos @Autowired, puestos a mano: sin Spring, en milisegundos.
		ReflectionTestUtils.setField(segundos, "jdbc", jdbc);
		ReflectionTestUtils.setField(segundos, "resultados", resultados);
		return segundos;
	}

	// Un ResultSet simulado que responde a cada getXxx("columna") con el valor del record.
	// Es la pieza que engaña al RowMapper real: desde su punto de vista, está leyendo una fila de verdad.
	private static ResultSet fila(Fila f) {
		ResultSet rs = mock(ResultSet.class);
		try {
			// Los nombres de columna son los del SELECT de SegundosDeResultados. Si allí se renombrara una
			// columna sin tocar el RowMapper, esta prueba fallaría, que es justo lo que se quiere.
			Mockito.when(rs.getInt("idgrupo")).thenReturn(f.idGrupo());
			Mockito.when(rs.getString("etiqueta")).thenReturn(f.grupo());
			Mockito.when(rs.getInt("ordinal")).thenReturn(f.ordinal());
			Mockito.when(rs.getInt("numerocilindro")).thenReturn(f.cilindro());
			Mockito.when(rs.getInt("segundo")).thenReturn(f.segundo());
			// getString devuelve null cuando la conducta es null: así se reproduce el «sin ver».
			Mockito.when(rs.getString("conducta")).thenReturn(f.conducta());
			Mockito.when(rs.getString("origen")).thenReturn(f.origen());
		} catch (SQLException e) {
			// Los métodos de ResultSet declaran SQLException aunque el simulado no la lance nunca; hay que
			// atraparla para que compile.
			throw new IllegalStateException(e);
		}
		return rs;
	}

	// Un ResultadosService simulado que devuelve el alcance de arriba. Así esta prueba se centra en los
	// segundos y no vuelve a comprobar la validación del alcance, que tiene sus propias pruebas.
	private static ResultadosService resultadosQueDaElAlcance() {
		ResultadosService resultados = mock(ResultadosService.class);
		when(resultados.alcanceDeReporte(1, 7, "A")).thenReturn(ALCANCE);
		return resultados;
	}

	@Test
	void la_respuesta_trae_la_duracion_el_grupo_la_letra_y_los_segundos_de_cada_espécimen() {
		// Cuatro filas: dos especímenes con dos segundos cada uno, y uno de ellos sin conducta.
		List<Fila> filas = List.of(
				new Fila(7, "Control", 1, 1, 1, "nado activo", "maquina"),
				new Fila(7, "Control", 1, 1, 2, null, "dudosa"),
				new Fila(7, "Control", 1, 2, 1, "inmovilidad", "corregida"),
				new Fila(7, "Control", 1, 2, 2, "inmovilidad", "maquina"));
		Map<String, Object> datos = servicio(resultadosQueDaElAlcance(), filas).deUnaTanda(1, 7, "A");

		// La duración es el número de segundo más alto: 2.
		assertEquals(2, datos.get("duracionS"));
		assertEquals("A", datos.get("letra"));
		// El grupo va como objeto anidado, no como dos campos sueltos.
		assertEquals(Map.of("id", 7, "etiqueta", "Control"), datos.get("grupo"));
		List<Map<String, Object>> especimenes = castear(datos.get("especimenes"));
		// Las cuatro filas planas se agruparon en DOS especímenes: es lo que hace el computeIfAbsent.
		assertEquals(2, especimenes.size());
		assertEquals(1, especimenes.get(0).get("numeroCilindro"));
		List<Map<String, Object>> segundos = castear(especimenes.get(0).get("segundos"));
		assertEquals(1, segundos.get(0).get("s"));
		assertEquals("nado activo", segundos.get(0).get("conducta"));
		assertEquals("maquina", segundos.get(0).get("origen"));
		// «sin ver»: conducta null, con su origen crudo.
		// El null se conserva y no se traduce a texto, al revés que en el Excel: aquí el cliente es la
		// pantalla, que sabe distinguirlo.
		assertNull(segundos.get(1).get("conducta"));
		assertEquals("dudosa", segundos.get(1).get("origen"));
		// Y el segundo espécimen, para comprobar que el agrupado reparte bien las filas.
		List<Map<String, Object>> segundosDelDos = castear(especimenes.get(1).get("segundos"));
		assertEquals("corregida", segundosDelDos.get(0).get("origen"));
	}

	@Test
	void una_tanda_sin_analisis_completado_responde_404() {
		// Sin filas: la tanda existe (el alcance se resolvió) pero no tiene segundos.
		SegundosDeResultados servicio = servicio(resultadosQueDaElAlcance(), List.of());
		ErrorApi error = assertThrows(ErrorApi.class, () -> servicio.deUnaTanda(1, 7, "A"));
		assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
	}

	@Test
	void sin_letra_responde_400() {
		// Un espacio en blanco, que es lo que isBlank descarta. Es el 400 por el que el controller declara
		// el parámetro opcional, para que la falta llegue hasta aquí con su mensaje.
		SegundosDeResultados servicio = servicio(resultadosQueDaElAlcance(), List.of());
		ErrorApi error = assertThrows(ErrorApi.class, () -> servicio.deUnaTanda(1, 7, " "));
		assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
	}

	@Test
	void si_la_tanda_o_el_grupo_no_existen_el_error_de_alcance_sube_tal_cual() {
		// Se programa el simulado para que LANCE: comprueba que el error de alcanceDeReporte atraviesa el
		// método sin que este lo atrape ni lo convierta en otro código.
		ResultadosService resultados = mock(ResultadosService.class);
		when(resultados.alcanceDeReporte(1, 99, "A")).thenThrow(new ErrorApi(HttpStatus.NOT_FOUND, "No existe ese grupo"));
		SegundosDeResultados servicio = servicio(resultados, List.of());
		ErrorApi error = assertThrows(ErrorApi.class, () -> servicio.deUnaTanda(1, 99, "A"));
		assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
	}

	// Un atajo para convertir el Object del mapa al tipo que toca, sin escribir el cast y el
	// @SuppressWarnings en cada línea. Es seguro aquí porque la forma de la respuesta la fija el propio
	// servicio unas líneas más arriba.
	@SuppressWarnings("unchecked")
	private static <T> T castear(Object valor) {
		return (T) valor;
	}
}
