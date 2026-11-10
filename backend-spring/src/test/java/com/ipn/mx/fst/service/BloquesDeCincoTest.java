// Mismo paquete que la clase que prueba, dentro de src/test. Hace falta porque BloquesDeCinco no es
// public: solo la ve su paquete.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Arrays.asList y no List.of: la primera ADMITE nulos y la segunda no. Aquí es imprescindible, porque el
// null es un valor legítimo (el segundo en que no se ve al espécimen).
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// R-12 / D-53: la regla de los bloques de 5 s, sin base de datos. La ruta completa está en BloquesDeResultadosTest.
//
// Es la prueba de la regla con la que se puntúa la prueba de nado forzado a mano, así que conviene que
// esté cubierta caso por caso: un error aquí cambiaría los resultados publicados.
// El reparto es el de siempre: aquí la regla pura, y en BloquesDeResultadosTest el camino completo desde
// la base hasta el endpoint.
class BloquesDeCincoTest {

	// Abreviaturas de las cuatro conductas. Con los nombres completos, una línea como
	// decidir(N, N, E, I, A) ocuparía media pantalla y la prueba dejaría de leerse de un vistazo.
	private static final String N = "nado activo";
	private static final String I = "inmovilidad";
	private static final String E = "escalamiento";
	private static final String A = "conducta activa";

	// Un atajo con número variable de argumentos (String...): permite escribir decidir(N, N, I, I, E) en
	// vez de construir la lista a mano en cada prueba.
	private static BloquesDeCinco.Decision decidir(String... segundos) {
		return BloquesDeCinco.decidir(Arrays.asList(segundos));
	}

	@Test
	void gana_la_conducta_con_mas_segundos() {
		// La regla principal, con tres casos claros. Se compara el record Decision entero (conducta y
		// matiz a la vez): un record trae equals generado, así que la comparación es directa.
		assertEquals(new BloquesDeCinco.Decision(N, false), decidir(N, N, N, I, I));
		assertEquals(new BloquesDeCinco.Decision(I, false), decidir(I, I, I, I, I));
		assertEquals(new BloquesDeCinco.Decision(E, false), decidir(E, E, E, N, I));
		// 2 contra 1 y 1: gana la de 2, aunque no tenga más de la mitad.
		// Es una aclaración importante: la regla es MAYORÍA SIMPLE, no mayoría absoluta.
		assertEquals(new BloquesDeCinco.Decision(N, false), decidir(N, N, E, I, A));
	}

	@Test
	void empate_entre_nado_activo_y_escalamiento_es_conducta_activa() {
		// La regla del empate: ante la duda, lo único que consta es que el animal se movía.
		// Dos casos con el mismo empate escrito en distinto orden, para comprobar que el orden de los
		// segundos dentro del bloque no influye.
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(N, N, E, E, I));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(E, N, E, N, A));
	}

	@Test
	void empate_de_inmovilidad_con_una_conducta_activa_es_conducta_activa() {
		// El empate más delicado: inmovilidad contra una conducta activa. Resolverlo a favor de la
		// inmovilidad sesgaría el resultado hacia el efecto que mide la prueba, así que se resuelve a
		// "conducta activa", que es lo que no afirma de más.
		// Los cuatro casos cubren el empate con cada una de las tres activas y con el orden alterno.
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(I, I, N, N, E));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(I, I, E, E, N));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(I, I, A, A, N));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(I, N, I, N, E));
	}

	@Test
	void empate_entre_nado_o_escalamiento_y_la_conducta_activa_del_modelo_es_conducta_activa() {
		// Aquí el resultado coincide con una de las empatadas, pero por la regla del empate, no por
		// haber ganado. Se prueba igual para dejar fijado el caso.
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(N, N, A, A, I));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(E, E, A, A, I));
	}

	@Test
	void la_conducta_activa_del_modelo_no_se_suma_a_nado_ni_a_escalamiento() {
		// 2 de inmovilidad contra 1 de cada activa: gana inmovilidad; sumadas serían 3 activas.
		//
		// Es LA prueba que fija esa decisión. "Conducta activa" es una categoría propia (el nivel
		// agrupado), no un paraguas que englobe a nado y escalamiento. Si alguien las sumara, este caso
		// daría "conducta activa" en vez de "inmovilidad" y la prueba lo detendría.
		assertEquals(new BloquesDeCinco.Decision(I, false), decidir(I, I, N, E, A));
		assertEquals(new BloquesDeCinco.Decision(A, false), decidir(A, A, A, I, I));
	}

	@Test
	void los_cinco_sin_ver_son_conducta_activa_con_matiz() {
		// El caso extremo: no se vio nada. El matiz en true es lo que avisa de que el bloque no es
		// comparable con los demás.
		assertEquals(new BloquesDeCinco.Decision(A, true), decidir(null, null, null, null, null));
	}

	@Test
	void matiz_si_el_bloque_se_decidio_con_algun_segundo_sin_ver() {
		// Basta UN segundo sin ver para marcar el bloque, aunque la conducta esté clarísima.
		assertEquals(new BloquesDeCinco.Decision(N, true), decidir(N, N, N, N, null));
		// Y al revés: un solo segundo visible decide el bloque, pero con matiz.
		assertEquals(new BloquesDeCinco.Decision(I, true), decidir(null, I, null, null, null));
		// Empate con un segundo sin ver: conducta activa y matiz.
		// Comprueba que las dos reglas (empate y matiz) se aplican a la vez.
		assertEquals(new BloquesDeCinco.Decision(A, true), decidir(I, I, N, N, null));
		assertEquals(new BloquesDeCinco.Decision(A, true), decidir(N, N, E, E, null));
	}

	@Test
	void sin_segundos_sin_ver_no_hay_matiz_aunque_haya_empate() {
		// La comprobación contraria de la anterior: el matiz habla de segundos sin ver, NO de un empate.
		// Son dos cosas distintas y no deben confundirse.
		assertEquals(false, decidir(I, I, N, N, E).matiz());
	}

	@Test
	void una_conducta_que_no_es_del_dominio_es_un_error_de_programa() {
		// Un texto que no está en el dominio significa que la base y el código ya no coinciden. Se lanza
		// en vez de ignorarlo: contarlo como nada falsearía el resultado en silencio.
		assertThrows(IllegalArgumentException.class, () -> decidir(N, "nadar", N, N, N));
	}

	@Test
	void armar_parte_la_lista_de_segundos_en_bloques_que_empiezan_en_1_6_11() {
		// Quince segundos: tres bloques completos. El nombre de la prueba ya dice lo que se comprueba,
		// que los bloques empiezan en 1, 6 y 11 (y no en 0, 5 y 10).
		List<String> segundos = Arrays.asList(N, N, N, N, N, I, I, I, I, I, E, E, E, E, null);
		List<Map<String, Object>> bloques = BloquesDeCinco.armar(segundos);

		assertEquals(3, bloques.size());
		assertEquals(1, bloques.get(0).get("inicio"));
		assertEquals(6, bloques.get(1).get("inicio"));
		assertEquals(11, bloques.get(2).get("inicio"));
		assertEquals(N, bloques.get(0).get("conducta"));
		assertEquals(false, bloques.get(0).get("matiz"));
		// Los segundos originales viajan dentro del bloque: aquí se comprueba que son los que tocan.
		assertEquals(List.of(I, I, I, I, I), bloques.get(1).get("segundos"));
		// Arrays.asList otra vez, porque esta lista lleva un null.
		assertEquals(Arrays.asList(E, E, E, E, null), bloques.get(2).get("segundos"));
		// El tercer bloque tiene cuatro escalamientos y un sin ver: gana E, con matiz.
		assertEquals(E, bloques.get(2).get("conducta"));
		assertEquals(true, bloques.get(2).get("matiz"));
	}

	@Test
	void el_ultimo_bloque_incompleto_trae_solo_los_segundos_que_existen_y_su_ausencia_no_da_matiz() {
		// 12 segundos: bloques 1, 6 y un último de 2 segundos.
		//
		// Es la distinción fina de BloquesDeCinco: un segundo que NO EXISTE (el video se acabó) no es lo
		// mismo que uno en el que no se vio al espécimen. Solo el segundo caso da matiz.
		// El matiz en false del último bloque es lo que fija esa diferencia.
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
		// El caso vacío: una lista vacía, no un bloque de ceros ni una excepción.
		assertEquals(0, BloquesDeCinco.armar(List.of()).size());
	}

}
