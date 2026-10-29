package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// R-12 / D-53: la regla de los bloques de 5 s, sin base de datos. La ruta completa está en BloquesDeResultadosTest.
class BloquesDeCincoTest {

	private static final String N = "nado activo";
	private static final String I = "inmovilidad";
	private static final String E = "escalamiento";
	private static final String A = "conducta activa";

	private static BloquesDeCinco.Decision decidir(String... segundos) {
		return BloquesDeCinco.decidir(Arrays.asList(segundos));
	}

	@Test
	void gana_la_conducta_con_mas_segundos() {
		assertEquals(new BloquesDeCinco.Decision(N, false), decidir(N, N, N, I, I));
		assertEquals(new BloquesDeCinco.Decision(I, false), decidir(I, I, I, I, I));
		assertEquals(new BloquesDeCinco.Decision(E, false), decidir(E, E, E, N, I));
		// 2 contra 1 y 1: gana la de 2, aunque no tenga más de la mitad.
		assertEquals(new BloquesDeCinco.Decision(N, false), decidir(N, N, E, I, A));
	}

	@Test
	void empate_entre_nado_activo_y_escalamiento_es_conducta_activa() {
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(N, N, E, E, I));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(E, N, E, N, A));
	}

	@Test
	void empate_de_inmovilidad_con_una_conducta_activa_es_conducta_activa() {
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(I, I, N, N, E));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(I, I, E, E, N));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(I, I, A, A, N));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(I, N, I, N, E));
	}

	@Test
	void empate_entre_nado_o_escalamiento_y_la_conducta_activa_del_modelo_es_conducta_activa() {
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(N, N, A, A, I));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(E, E, A, A, I));
	}

	@Test
	void la_conducta_activa_del_modelo_no_se_suma_a_nado_ni_a_escalamiento() {
		// 2 de inmovilidad contra 1 de cada activa: gana inmovilidad; sumadas serían 3 activas.
		assertEquals(new BloquesDeCinco.Decision(I, false), decidir(I, I, N, E, A));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(A, A, A, I, I));
	}

	@Test
	void los_cinco_sin_ver_son_conducta_activa_con_matiz() {
		assertEquals(new BloquesDeCinco.Decision(A, true), decidir(null, null, null, null, null));
	}

	@Test
	void matiz_si_el_bloque_se_decidio_con_algun_segundo_sin_ver() {
		assertEquals(new BloquesDeCinco.Decision(N, true), decidir(N, N, N, N, null));
		assertEquals(new BloquesDeCinco.Decision(I, true), decidir(null, I, null, null, null));
		// Empate con un segundo sin ver: conducta activa y matiz.
		assertEquals(new BloquesDeCinco.Decision(A, true), decidir(I, I, N, N, null));
		assertEquals(new BloquesDeCinco.Decision(A, true), decidir(N, N, E, E, null));
	}

	@Test
	void sin_segundos_sin_ver_no_hay_matiz_aunque_haya_empate() {
		assertEquals(false, decidir(I, I, N, N, E).matiz());
	}

	@Test
	void una_conducta_que_no_es_del_dominio_es_un_error_de_programa() {
		assertThrows(IllegalArgumentException.class, () -> decidir(N, "nadar", N, N, N));
	}

	@Test
	void armar_parte_la_lista_de_segundos_en_bloques_que_empiezan_en_1_6_11() {
		List<String> segundos = Arrays.asList(N, N, N, N, N, I, I, I, I, I, E, E, E, E, null);
		List<Map<String, Object>> bloques = BloquesDeCinco.armar(segundos);

		assertEquals(3, bloques.size());
		assertEquals(1, bloques.get(0).get("inicio"));
		assertEquals(6, bloques.get(1).get("inicio"));
		assertEquals(11, bloques.get(2).get("inicio"));
		assertEquals(N, bloques.get(0).get("conducta"));
		assertEquals(false, bloques.get(0).get("matiz"));
		assertEquals(List.of(I, I, I, I, I), bloques.get(1).get("segundos"));
		assertEquals(Arrays.asList(E, E, E, E, null), bloques.get(2).get("segundos"));
		assertEquals(E, bloques.get(2).get("conducta"));
		assertEquals(true, bloques.get(2).get("matiz"));
	}

	@Test
	void el_ultimo_bloque_incompleto_trae_solo_los_segundos_que_existen_y_su_ausencia_no_da_matiz() {
		// 12 segundos: bloques 1, 6 y un último de 2 segundos.
		List<String> segundos = Arrays.asList(N, N, N, N, N, I, I, I, I, I, E, E);
		List<Map<String, Object>> bloques = BloquesDeCinco.armar(segundos);

		assertEquals(3, bloques.size());
		assertEquals(11, bloques.get(2).get("inicio"));
		assertEquals(List.of(E, E), bloques.get(2).get("segundos"));
		assertEquals(E, bloques.get(2).get("conducta"));
		assertEquals(false, bloques.get(2).get("matiz"));
	}

	@Test
	void un_especimen_sin_segundos_no_tiene_bloques() {
		assertEquals(0, BloquesDeCinco.armar(List.of()).size());
	}

}
