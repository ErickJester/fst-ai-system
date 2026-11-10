// Mismo paquete que la clase que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// Datos de las gráficas (M1A/M1B), sin base de datos: el cálculo puro de GraficasDeResultados con números fijos.
//
// Son las cifras que acaban en las figuras del artículo, así que cada regla estadística tiene su prueba.
// La clase es pura (recibe listas, devuelve un mapa), de modo que los datos se escriben a mano y el
// resultado se comprueba con números calculados aparte.
class GraficasDeResultadosTest {

	// La tolerancia al comparar decimales: los double no son exactos (ver EstadisticosTest).
	private static final double T = 1e-9;
	// Abreviaturas de las cinco categorías, para que las líneas de datos se lean.
	private static final String NADO = "nado activo";
	private static final String INM = "inmovilidad";
	private static final String ESC = "escalamiento";
	private static final String ACT = "conducta activa";
	private static final String SIN = "sin ver";

	@Test
	void las_cinco_categorias_y_los_cinco_minutos_van_siempre() {
		// Incluso sin datos, la respuesta trae las categorías y los minutos: la pantalla y el PDF los
		// usan para armar sus ejes sin suponerlos.
		Map<String, Object> datos = GraficasDeResultados.armar(List.of(), List.of());
		// El ORDEN importa: es el de las columnas de todas las figuras, con «sin ver» al final.
		assertEquals(List.of(NADO, INM, ESC, ACT, SIN), datos.get("conductas"));
		assertEquals(List.of(1, 2, 3, 4, 5), datos.get("minutos"));
		assertEquals(List.of(), datos.get("grupos"));
	}

	@Test
	void los_grupos_salen_control_luego_referencia_y_luego_experimentales() {
		// Se pasan DESORDENADOS a propósito (3, 1, 2, 4) para comprobar que el método los ordena.
		Map<String, Object> datos = GraficasDeResultados.armar(
				List.of(grupo(3, "Dosis 2", "tratamiento experimental"), grupo(1, "Control", "control"),
						grupo(2, "Fluoxetina", "referencia"), grupo(4, "Dosis 1", "tratamiento experimental")),
				List.of());
		// El resultado comprueba los DOS criterios a la vez: por tipo (control, referencia, experimentales)
		// y, dentro de los experimentales, por id (3 antes que 4).
		assertEquals(List.of(1, 2, 3, 4), ids(datos));
	}

	@Test
	void varianza_y_desviacion_muestrales_con_dos_especimenes_y_coeficiente_de_variacion() {
		// Dos especímenes con 10 y 20 segundos: los mismos números de EstadisticosTest, para poder
		// comprobar la cuenta a mano.
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "nivel", Map.of(NADO, 10L), Map.of()),
				espec(1, 1, 2, "nivel", Map.of(NADO, 20L), Map.of()))), 1);
		Map<String, Object> nado = conducta(g, NADO);
		assertEquals(2, ((Number) nado.get("n")).intValue());
		assertEquals(15.0, (Double) nado.get("media"), T);
		// 50 y no 25: la varianza MUESTRAL divide entre n − 1.
		assertEquals(50.0, (Double) nado.get("varianza"), T);
		// La raíz de 50 es 7,0710..., redondeada a un decimal.
		assertEquals(7.1, (Double) nado.get("de"), T);
		// 7,0710678 ÷ 15 = 0,4714 → 0,471
		// El CV lleva TRES decimales, no uno: es una fracción pequeña y con un decimal se perdería casi
		// toda la información. El comentario desarrolla la cuenta para poder verificarla sin el código.
		assertEquals(0.471, (Double) nado.get("cv"), T);
		// Los valores individuales viajan también, para dibujar los puntos encima de la barra.
		assertEquals(List.of(10L, 20L), nado.get("valoresS"));
	}

	@Test
	void con_un_solo_especimen_la_media_existe_y_la_dispersion_no_aplica() {
		// La regla de honestidad estadística: con un individuo hay promedio pero no hay dispersión que
		// estimar. Los tres null (y no ceros) son lo que se comprueba: un 0 afirmaría "no hay
		// variabilidad", que es una conclusión que estos datos no sostienen.
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
		// El caso que dividiría entre cero. Fíjate en la distinción: la DE sí se puede calcular (y vale
		// 0, porque los dos valores son iguales), pero el CV no, porque es DE ÷ media.
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
		// Un grupo declarado pero sin especímenes analizados: sale en la respuesta con sus claves, en
		// null. Omitirlo lo haría desaparecer de la figura sin explicación.
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of()), 1);
		assertEquals(0, ((Number) g.get("n")).intValue());
		assertNull(g.get("duracionMediaS"));
		Map<String, Object> nado = conducta(g, NADO);
		assertNull(nado.get("media"));
		assertNull(nado.get("de"));
		// La lista de valores está VACÍA, no en null: así se puede recorrer sin comprobar nada.
		assertEquals(List.of(), nado.get("valoresS"));
	}

	@Test
	void la_conducta_activa_y_sin_ver_vienen_con_sus_valores_por_especimen() {
		// El segundo espécimen no tiene «sin ver» en su mapa.
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "nivel", Map.of(ACT, 5L, SIN, 7L), Map.of()),
				espec(1, 1, 2, "nivel", Map.of(ACT, 9L), Map.of()))), 1);
		assertEquals(List.of(5L, 9L), conducta(g, ACT).get("valoresS"));
		// Y aun así sale con 0, no se omite: aquí el 0 SÍ es correcto, porque "no tuvo segundos sin ver"
		// es un dato real. Omitirlo lo sacaría del promedio como si no se hubiera medido.
		assertEquals(List.of(7L, 0L), conducta(g, SIN).get("valoresS"));
		assertEquals(14.0 / 2, (Double) conducta(g, ACT).get("media"), T);
	}

	@Test
	void duracion_media_es_la_suma_real_de_las_cinco_categorias() {
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "nivel", Map.of(NADO, 200L, SIN, 40L), Map.of()),
				espec(1, 1, 2, "nivel", Map.of(NADO, 100L), Map.of()))), 1);
		// Duraciones reales: 240 y 100 → media 170.
		// Lo importante es que los 40 segundos «sin ver» CUENTAN en la duración: se midieron, aunque no
		// se viera al espécimen. Si se descartaran, la duración del primero sería 200 y la media 150.
		assertEquals(170.0, (Double) g.get("duracionMediaS"), T);
	}

	@Test
	void un_minuto_incompleto_se_marca_y_solo_cuentan_los_especimenes_con_segundos_en_el_minuto() {
		// A: 5 minutos, el quinto con 30 s. B: video de 2 minutos completos.
		// Es el caso real de dos videos de distinta duración en el mismo grupo.
		Map<Integer, Map<String, Long>> a = Map.of(1, Map.of(NADO, 60L), 2, Map.of(NADO, 60L), 3, Map.of(NADO, 60L),
				4, Map.of(NADO, 60L), 5, Map.of(NADO, 30L));
		Map<Integer, Map<String, Long>> b = Map.of(1, Map.of(NADO, 50L, SIN, 10L), 2, Map.of(NADO, 60L));
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "nivel", Map.of(NADO, 270L), a),
				espec(1, 1, 2, "nivel", Map.of(NADO, 110L, SIN, 10L), b))), 1);

		List<Map<String, Object>> minutos = minutosDe(g);
		// Minuto 1: los dos especímenes tienen 60 segundos, así que n = 2 y no hay nada que marcar.
		// Fíjate en que el segundo tiene 50 de nado y 10 de «sin ver»: los 60 salen de sumar las cinco
		// categorías, no solo las conductas.
		Map<String, Object> primero = minutos.get(0);
		assertEquals(2, ((Number) primero.get("n")).intValue());
		assertFalse((Boolean) primero.get("incompleto"));
		assertEquals(60L, ((Number) primero.get("segundosMin")).longValue());

		// Minuto 5: solo A llega hasta ahí, y con 30 segundos.
		Map<String, Object> quinto = minutos.get(4);
		// n = 1: B queda FUERA del promedio en vez de contar como 0. Meterlo con ceros hundiría la media
		// de ese minuto, que es el error que esta prueba impide.
		assertEquals(1, ((Number) quinto.get("n")).intValue());
		// Y se marca como incompleto, porque 30 < 60.
		assertTrue((Boolean) quinto.get("incompleto"));
		// Los segundos son los REALES, no completados a 60.
		assertEquals(30L, ((Number) quinto.get("segundosMin")).longValue());
		assertEquals(30L, ((Number) quinto.get("segundosMax")).longValue());

		// Minuto 3: solo A, con sus 60 segundos completos. La media es 60 y no 30, que es lo que saldría
		// si B contara como 0.
		Map<String, Object> tercero = minutos.get(2);
		assertEquals(1, ((Number) tercero.get("n")).intValue());
		assertEquals(60.0, (Double) ((Map<?, ?>) ((Map<?, ?>) tercero.get("porConducta")).get(NADO)).get("media"), T);
	}

	@Test
	void con_niveles_mezclados_el_grupo_no_se_promedia_y_se_da_por_tanda() {
		// Dos tandas del mismo grupo con niveles distintos: una "preciso" y otra "agrupado". Promediarlas
		// juntas daría un número sin sentido (ver NivelClasifAnalisis).
		Map<String, Object> g = grupoDe(GraficasDeResultados.armar(List.of(grupo(1, "Control", "control")), List.of(
				espec(1, 1, 1, "preciso", Map.of(NADO, 10L), Map.of()),
				espec(1, 2, 1, "agrupado", Map.of(NADO, 30L), Map.of()))), 1);
		assertTrue((Boolean) g.get("nivelMixto"));
		// Los tres agregados van en null...
		assertNull(g.get("porConducta"));
		assertNull(g.get("porMinuto"));
		assertNull(g.get("duracionMediaS"));
		// ...y en su lugar, una fila por tanda, que sí es homogénea.
		List<?> porNivel = (List<?>) g.get("porNivel");
		assertEquals(2, porNivel.size());
		// La segunda tanda (ordinal 2) sale como "B" y con su nivel: comprueba de paso que el TreeMap las
		// ordena por ordinal.
		assertEquals("B", ((Map<?, ?>) porNivel.get(1)).get("letra"));
		assertEquals("agrupado", ((Map<?, ?>) porNivel.get(1)).get("nivelClasif"));
	}

	// ----------------------------------------------------------------- apoyo
	//
	// Los métodos de abajo son andamiaje: construyen los datos de prueba y leen el resultado. Separarlos
	// deja las pruebas de arriba legibles, que es lo que importa cuando una falla.

	// Un grupo con lo mínimo: nTotal en 0 y sin faltantes, porque estas pruebas no los comprueban.
	private static GraficasDeResultados.Grupo grupo(int id, String etiqueta, String tipo) {
		return new GraficasDeResultados.Grupo(id, etiqueta, tipo, "agua", 0, List.of());
	}

	// Un espécimen. La duración se calcula sumando el mapa de totales, igual que hace el servicio real:
	// así los datos de prueba no pueden quedar incoherentes (una duración que no cuadre con sus segundos).
	private static GraficasDeResultados.Especimen espec(int idGrupo, int ordinal, int cilindro, String nivel,
			Map<String, Long> total, Map<Integer, Map<String, Long>> porMinuto) {
		long duracion = total.values().stream().mapToLong(Long::longValue).sum();
		// La letra se calcula del ordinal con la misma aritmética de caracteres del resto del sistema.
		return new GraficasDeResultados.Especimen(idGrupo, ordinal, String.valueOf((char) ('A' + ordinal - 1)), cilindro, nivel,
				duracion, total, porMinuto);
	}

	// Los ids de los grupos, en el orden en que salieron: es lo que comprueba la prueba del orden.
	@SuppressWarnings("unchecked")
	private static List<Integer> ids(Map<String, Object> datos) {
		return ((List<Map<String, Object>>) datos.get("grupos")).stream().map(g -> (Integer) g.get("idGrupo")).toList();
	}

	// Busca un grupo por su id. orElseThrow hace que la prueba falle con claridad si no está, en vez de
	// seguir con un null y reventar más adelante con un mensaje que no dice nada.
	@SuppressWarnings("unchecked")
	private static Map<String, Object> grupoDe(Map<String, Object> datos, int id) {
		return ((List<Map<String, Object>>) datos.get("grupos")).stream().filter(g -> g.get("idGrupo").equals(id)).findFirst().orElseThrow();
	}

	// Los estadísticos de una conducta dentro de un grupo, para no escribir el doble cast en cada línea.
	@SuppressWarnings("unchecked")
	private static Map<String, Object> conducta(Map<String, Object> grupo, String conducta) {
		return ((Map<String, Map<String, Object>>) grupo.get("porConducta")).get(conducta);
	}

	// La lista de minutos de un grupo.
	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> minutosDe(Map<String, Object> grupo) {
		return (List<Map<String, Object>>) grupo.get("porMinuto");
	}

}
