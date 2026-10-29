package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

// Encargo 39: media, varianza y desviación estándar muestrales (n − 1), sin base de datos.
class EstadisticosTest {

	private static final double TOLERANCIA = 1e-9;

	@Test
	void sin_especimenes_los_tres_valores_son_null() {
		assertNull(Estadisticos.media(List.of()));
		assertNull(Estadisticos.varianza(List.of()));
		assertNull(Estadisticos.desviacion(List.of()));
	}

	@Test
	void con_un_solo_especimen_la_media_es_su_valor_y_la_varianza_y_la_desviacion_son_cero() {
		assertEquals(42.0, Estadisticos.media(List.of(42L)), TOLERANCIA);
		assertEquals(0.0, Estadisticos.varianza(List.of(42L)), TOLERANCIA);
		assertEquals(0.0, Estadisticos.desviacion(List.of(42L)), TOLERANCIA);
	}

	@Test
	void con_dos_especimenes_se_divide_entre_n_menos_1() {
		// 10 y 20: media 15, suma de cuadrados 50, varianza 50 / (2 - 1) = 50, desviación raíz de 50.
		assertEquals(15.0, Estadisticos.media(List.of(10L, 20L)), TOLERANCIA);
		assertEquals(50.0, Estadisticos.varianza(List.of(10L, 20L)), TOLERANCIA);
		assertEquals(Math.sqrt(50.0), Estadisticos.desviacion(List.of(10L, 20L)), TOLERANCIA);
	}

	@Test
	void valores_conocidos() {
		// 2, 4, 4, 4, 5, 5, 7, 9: media 5, suma de cuadrados 32, varianza muestral 32 / 7.
		List<Long> valores = List.of(2L, 4L, 4L, 4L, 5L, 5L, 7L, 9L);
		assertEquals(5.0, Estadisticos.media(valores), TOLERANCIA);
		assertEquals(32.0 / 7.0, Estadisticos.varianza(valores), TOLERANCIA);
		assertEquals(Math.sqrt(32.0 / 7.0), Estadisticos.desviacion(valores), TOLERANCIA);
	}

	@Test
	void valores_iguales_no_tienen_dispersion() {
		assertEquals(0.0, Estadisticos.varianza(List.of(7L, 7L, 7L)), TOLERANCIA);
	}

	@Test
	void redondea_a_un_decimal_y_respeta_null() {
		assertEquals(4.6, Estadisticos.redondear(4.5714), TOLERANCIA);
		assertEquals(0.0, Estadisticos.redondear(0.04), TOLERANCIA);
		assertNull(Estadisticos.redondear((Double) null));
	}

}
