package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// Datos de las gráficas (M1A/M1B), sin base de datos: el cálculo puro de GraficasDeResultados con números fijos.
class GraficasDeResultadosTest {

	private static final double T = 1e-9;
	private static final String NADO = "nado activo";
	private static final String INM = "inmovilidad";
	private static final String ESC = "escalamiento";
	private static final String ACT = "conducta activa";
	private static final String SIN = "sin ver";

	@Test
	void las_cinco_categorias_y_los_cinco_minutos_van_siempre() {
		Map<String, Object> datos = GraficasDeResultados.armar(List.of(), List.of());
		assertEquals(List.of(NADO, INM, ESC, ACT, SIN), datos.get("conductas"));
		assertEquals(List.of(1, 2, 3, 4, 5), datos.get("minutos"));
		assertEquals(List.of(), datos.get("grupos"));
	}

	@Test
	void los_grupos_salen_control_luego_referencia_y_luego_experimentales() {
		Map<String, Object> datos = GraficasDeResultados.armar(
				List.of(grupo(3, "Dosis 2", "tratamiento experimental"), grupo(1, "Control", "control"),
						grupo(2, "Fluoxetina", "referencia"), grupo(4, "Dosis 1", "tratamiento experimental")),
				List.of());
		assertEquals(List.of(1, 2, 3, 4), ids(datos));
	}

	@Test
	void varianza_y_desviacion_muestrales_con_dos_especimenes_y_coeficiente_de_variacion() {
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "nivel", Map.of(NADO, 10L), Map.of()),
				espec(1, 1, 2, "nivel", Map.of(NADO, 20L), Map.of()))), 1);
		Map<String, Object> nado = conducta(g, NADO);
		assertEquals(2, ((Number) nado.get("n")).intValue());
		assertEquals(15.0, (Double) nado.get("media"), T);
		assertEquals(50.0, (Double) nado.get("varianza"), T);
		assertEquals(7.1, (Double) nado.get("de"), T);
		// 7,0710678 ÷ 15 = 0,4714 → 0,471
		assertEquals(0.471, (Double) nado.get("cv"), T);
		assertEquals(List.of(10L, 20L), nado.get("valoresS"));
	}

	@Test
	void con_un_solo_especimen_la_media_existe_y_la_dispersion_no_aplica() {
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "nivel", Map.of(NADO, 42L), Map.of()))), 1);
		Map<String, Object> nado = conducta(g, NADO);
		assertEquals(42.0, (Double) nado.get("media"), T);
		assertNull(nado.get("de"));
		assertNull(nado.get("varianza"));
		assertNull(nado.get("cv"));
	}

	@Test
	void con_media_cero_el_coeficiente_de_variacion_no_existe_pero_la_desviacion_si() {
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "nivel", Map.of(ESC, 0L), Map.of()),
				espec(1, 1, 2, "nivel", Map.of(ESC, 0L), Map.of()))), 1);
		Map<String, Object> esc = conducta(g, ESC);
		assertEquals(0.0, (Double) esc.get("media"), T);
		assertEquals(0.0, (Double) esc.get("de"), T);
		assertNull(esc.get("cv"));
	}

	@Test
	void un_grupo_sin_datos_va_con_n_cero_y_todo_null() {
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of()), 1);
		assertEquals(0, ((Number) g.get("n")).intValue());
		assertNull(g.get("duracionMediaS"));
		Map<String, Object> nado = conducta(g, NADO);
		assertNull(nado.get("media"));
		assertNull(nado.get("de"));
		assertEquals(List.of(), nado.get("valoresS"));
	}

	@Test
	void la_conducta_activa_y_sin_ver_vienen_con_sus_valores_por_especimen() {
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "nivel", Map.of(ACT, 5L, SIN, 7L), Map.of()),
				espec(1, 1, 2, "nivel", Map.of(ACT, 9L), Map.of()))), 1);
		assertEquals(List.of(5L, 9L), conducta(g, ACT).get("valoresS"));
		assertEquals(List.of(7L, 0L), conducta(g, SIN).get("valoresS"));
		assertEquals(14.0 / 2, (Double) conducta(g, ACT).get("media"), T);
	}

	@Test
	void duracion_media_es_la_suma_real_de_las_cinco_categorias() {
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "nivel", Map.of(NADO, 200L, SIN, 40L), Map.of()),
				espec(1, 1, 2, "nivel", Map.of(NADO, 100L), Map.of()))), 1);
		// Duraciones reales: 240 y 100 → media 170.
		assertEquals(170.0, (Double) g.get("duracionMediaS"), T);
	}

	@Test
	void un_minuto_incompleto_se_marca_y_solo_cuentan_los_especimenes_con_segundos_en_el_minuto() {
		// A: 5 minutos, el quinto con 30 s. B: video de 2 minutos completos.
		Map<Integer, Map<String, Long>> a = Map.of(1, Map.of(NADO, 60L), 2, Map.of(NADO, 60L), 3, Map.of(NADO, 60L),
				4, Map.of(NADO, 60L), 5, Map.of(NADO, 30L));
		Map<Integer, Map<String, Long>> b = Map.of(1, Map.of(NADO, 50L, SIN, 10L), 2, Map.of(NADO, 60L));
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "nivel", Map.of(NADO, 270L), a),
				espec(1, 1, 2, "nivel", Map.of(NADO, 110L, SIN, 10L), b))), 1);

		List<Map<String, Object>> minutos = minutosDe(g);
		Map<String, Object> primero = minutos.get(0);
		assertEquals(2, ((Number) primero.get("n")).intValue());
		assertFalse((Boolean) primero.get("incompleto"));
		assertEquals(60L, ((Number) primero.get("segundosMin")).longValue());

		Map<String, Object> quinto = minutos.get(4);
		assertEquals(1, ((Number) quinto.get("n")).intValue());
		assertTrue((Boolean) quinto.get("incompleto"));
		assertEquals(30L, ((Number) quinto.get("segundosMin")).longValue());
		assertEquals(30L, ((Number) quinto.get("segundosMax")).longValue());

		Map<String, Object> tercero = minutos.get(2);
		assertEquals(1, ((Number) tercero.get("n")).intValue());
		assertEquals(60.0, (Double) ((Map<?, ?>) ((Map<?, ?>) tercero.get("porConducta")).get(NADO)).get("media"), T);
	}

	@Test
	void con_niveles_mezclados_el_grupo_no_se_promedia_y_se_da_por_tanda() {
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "preciso", Map.of(NADO, 10L), Map.of()),
				espec(1, 2, 1, "agrupado", Map.of(NADO, 30L), Map.of()))), 1);
		assertTrue((Boolean) g.get("nivelMixto"));
		assertNull(g.get("porConducta"));
		assertNull(g.get("porMinuto"));
		assertNull(g.get("duracionMediaS"));
		List<?> porNivel = (List<?>) g.get("porNivel");
		assertEquals(2, porNivel.size());
		assertEquals("B", ((Map<?, ?>) porNivel.get(1)).get("letra"));
		assertEquals("agrupado", ((Map<?, ?>) porNivel.get(1)).get("nivelClasif"));
	}

	// ----------------------------------------------------------------- apoyo

	private static GraficasDeResultados.Grupo grupo(int id, String etiqueta, String tipo) {
		return new GraficasDeResultados.Grupo(id, etiqueta, tipo, "agua", 0, List.of());
	}

	private static GraficasDeResultados.Especimen espec(int idGrupo, int ordinal, int cilindro, String nivel,
			Map<String, Long> total, Map<Integer, Map<String, Long>> porMinuto) {
		long duracion = total.values().stream().mapToLong(Long::longValue).sum();
		return new GraficasDeResultados.Especimen(idGrupo, ordinal, String.valueOf((char) ('A' + ordinal - 1)), cilindro, nivel,
				duracion, total, porMinuto);
	}

	@SuppressWarnings("unchecked")
	private static List<Integer> ids(Map<String, Object> datos) {
		return ((List<Map<String, Object>>) datos.get("grupos")).stream().map(g -> (Integer) g.get("idGrupo")).toList();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> grupoDe(Map<String, Object> datos, int id) {
		return ((List<Map<String, Object>>) datos.get("grupos")).stream().filter(g -> g.get("idGrupo").equals(id)).findFirst().orElseThrow();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> conducta(Map<String, Object> grupo, String conducta) {
		return ((Map<String, Map<String, Object>>) grupo.get("porConducta")).get(conducta);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> minutosDe(Map<String, Object> grupo) {
		return (List<Map<String, Object>>) grupo.get("porMinuto");
	}

}
