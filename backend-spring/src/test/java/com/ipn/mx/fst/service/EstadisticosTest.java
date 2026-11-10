// Mismo paquete que la clase que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

// assertEquals para comparar y assertNull para comprobar que algo no existe.
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

// Encargo 39: media, varianza y desviación estándar muestrales (n − 1), sin base de datos.
//
// Son las cuentas que acaban en el artículo, así que conviene que estén probadas con valores conocidos.
// La clase es pura (ver Estadisticos), de modo que la prueba es directa: entran listas, salen números.
class EstadisticosTest {

	// La tolerancia al comparar decimales.
	//
	// Es imprescindible: los double no son exactos, y 32.0/7.0 calculado de dos maneras distintas puede
	// diferir en el último bit. Comparar con == fallaría por un error de 1e-16 que no significa nada.
	// 1e-9 es holgado para detectar un error de verdad (una fórmula mal) y estrecho para ignorar el ruido
	// del punto flotante.
	private static final double TOLERANCIA = 1e-9;

	@Test
	void sin_especimenes_los_tres_valores_son_null() {
		// La regla de no inventar un 0 donde no hay datos: 0 sería una afirmación sobre los datos, null
		// dice "no se puede calcular" (ver Estadisticos).
		assertNull(Estadisticos.media(List.of()));
		assertNull(Estadisticos.varianza(List.of()));
		assertNull(Estadisticos.desviacion(List.of()));
	}

	@Test
	void con_un_solo_especimen_la_media_es_su_valor_y_la_varianza_y_la_desviacion_son_cero() {
		// El caso que dividiría entre cero con la fórmula de n − 1. Aquí el 0 sí es correcto: un único
		// dato no se aleja de sí mismo.
		// El tercer argumento de assertEquals es la tolerancia.
		assertEquals(42.0, Estadisticos.media(List.of(42L)), TOLERANCIA);
		assertEquals(0.0, Estadisticos.varianza(List.of(42L)), TOLERANCIA);
		assertEquals(0.0, Estadisticos.desviacion(List.of(42L)), TOLERANCIA);
	}

	@Test
	void con_dos_especimenes_se_divide_entre_n_menos_1() {
		// 10 y 20: media 15, suma de cuadrados 50, varianza 50 / (2 - 1) = 50, desviación raíz de 50.
		//
		// Es LA prueba de la corrección de Bessel, y por eso el comentario desarrolla la cuenta: dividir
		// entre n daría 25 en vez de 50, así que esta prueba distingue la varianza muestral de la
		// poblacional. Si alguien "simplificara" el n − 1, aquí se vería.
		assertEquals(15.0, Estadisticos.media(List.of(10L, 20L)), TOLERANCIA);
		assertEquals(50.0, Estadisticos.varianza(List.of(10L, 20L)), TOLERANCIA);
		// Se compara contra Math.sqrt(50.0) y no contra un decimal escrito a mano: así no se introduce un
		// error de redondeo en la propia prueba.
		assertEquals(Math.sqrt(50.0), Estadisticos.desviacion(List.of(10L, 20L)), TOLERANCIA);
	}

	@Test
	void valores_conocidos() {
		// 2, 4, 4, 4, 5, 5, 7, 9: media 5, suma de cuadrados 32, varianza muestral 32 / 7.
		//
		// Es el conjunto de ejemplo clásico de los libros de estadística para la desviación estándar, lo
		// que permite comprobar el resultado contra una fuente externa y no contra lo que da el código.
		List<Long> valores = List.of(2L, 4L, 4L, 4L, 5L, 5L, 7L, 9L);
		assertEquals(5.0, Estadisticos.media(valores), TOLERANCIA);
		assertEquals(32.0 / 7.0, Estadisticos.varianza(valores), TOLERANCIA);
		assertEquals(Math.sqrt(32.0 / 7.0), Estadisticos.desviacion(valores), TOLERANCIA);
	}

	@Test
	void valores_iguales_no_tienen_dispersion() {
		// Un caso límite sencillo: sin variación, la varianza es exactamente 0.
		assertEquals(0.0, Estadisticos.varianza(List.of(7L, 7L, 7L)), TOLERANCIA);
	}

	@Test
	void redondea_a_un_decimal_y_respeta_null() {
		// 4.5714 sube a 4.6: comprueba que redondea y no trunca (truncar daría 4.5).
		assertEquals(4.6, Estadisticos.redondear(4.5714), TOLERANCIA);
		// 0.04 baja a 0.0: el caso contrario.
		assertEquals(0.0, Estadisticos.redondear(0.04), TOLERANCIA);
		// El (Double) es necesario para elegir la versión sobrecargada que acepta null: sin él, Java no
		// sabría cuál de las dos se quiere y no compilaría.
		assertNull(Estadisticos.redondear((Double) null));
	}

}
