// Prueba unitaria de la regla «recuadro casi igual» (encargo 33). Sin base de datos ni Docker:
// ./mvnw test -Dtest=PausaServiceUmbralTest
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PausaServiceUmbralTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final int UMBRAL = 3;   // el valor por omisión de app.pausa.umbral-recuadro-px

	private static JsonNode pausa() throws Exception {
		return JSON.readTree("{\"recuadros\":[{\"cilindro\":1,\"x0\":100,\"y0\":200,\"x1\":300,\"y1\":400}]}");
	}

	// La decisión de ajustar: 422 si el mayor movimiento es <= umbral.
	private static boolean casiIgual(JsonNode automatico, int x0, int y0, int x1, int y1) {
		return PausaServiceImpl.movimientoMayor(automatico, x0, y0, x1, y1) <= UMBRAL;
	}

	@Test
	void tresPxEsCasiIgualYSeRechaza() throws Exception {
		JsonNode auto = PausaServiceImpl.recuadroDe(pausa(), 1);
		assertEquals(3, PausaServiceImpl.movimientoMayor(auto, 103, 200, 300, 400));
		assertTrue(casiIgual(auto, 103, 200, 300, 400));
	}

	@Test
	void cuatroPxYaEsAjuste() throws Exception {
		JsonNode auto = PausaServiceImpl.recuadroDe(pausa(), 1);
		assertEquals(4, PausaServiceImpl.movimientoMayor(auto, 104, 200, 300, 400));
		assertFalse(casiIgual(auto, 104, 200, 300, 400));
	}

	@Test
	void unSoloBordeMovidoMasDeTresPxBastaParaAceptar() throws Exception {
		JsonNode auto = PausaServiceImpl.recuadroDe(pausa(), 1);
		// solo y1 se movió 10 px; los demás bordes quedaron igual
		assertEquals(10, PausaServiceImpl.movimientoMayor(auto, 100, 200, 300, 410));
		assertFalse(casiIgual(auto, 100, 200, 300, 410));
	}

	@Test
	void sinEseCilindroNoHayRecuadroQueComparar() throws Exception {
		assertNull(PausaServiceImpl.recuadroDe(pausa(), 2));
		assertNotNull(PausaServiceImpl.recuadroDe(pausa(), 1));
	}
}
