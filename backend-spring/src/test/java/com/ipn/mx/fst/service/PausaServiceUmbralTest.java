// Prueba unitaria de la regla «recuadro casi igual» (encargo 33). Sin base de datos ni Docker:
// ./mvnw test -Dtest=PausaServiceUmbralTest
//
// El comentario de la cabecera trae el comando exacto para correrla sola, que es lo que se quiere al
// trabajar sobre esta regla: -Dtest=NombreDeLaClase ejecuta solo esa.
// Mismo paquete que PausaServiceImpl: por eso puede llamar a movimientoMayor y recuadroDe, que son
// métodos de paquete (sin public) hechos static justamente para poder probarlos sin levantar Spring.
package com.ipn.mx.fst.service;

// Los métodos de comprobación de JUnit: iguales, falso, nulo, no nulo y verdadero.
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Jackson 2, el mismo que usa PausaServiceImpl para leer pausa.json.
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PausaServiceUmbralTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final int UMBRAL = 3;   // el valor por omisión de app.pausa.umbral-recuadro-px

	// Un pausa.json mínimo, escrito a mano: un solo cilindro con su recuadro automático.
	// Es lo que permite probar sin volumen ni worker: el método solo necesita el árbol JSON.
	private static JsonNode pausa() throws Exception {
		return JSON.readTree("{\"recuadros\":[{\"cilindro\":1,\"x0\":100,\"y0\":200,\"x1\":300,\"y1\":400}]}");
	}

	// La decisión de ajustar: 422 si el mayor movimiento es <= umbral.
	// Se reproduce aquí la comparación que hace el servicio, para que las pruebas lean como la regla.
	private static boolean casiIgual(JsonNode automatico, int x0, int y0, int x1, int y1) {
		return PausaServiceImpl.movimientoMayor(automatico, x0, y0, x1, y1) <= UMBRAL;
	}

	@Test
	void tresPxEsCasiIgualYSeRechaza() throws Exception {
		JsonNode auto = PausaServiceImpl.recuadroDe(pausa(), 1);
		// x0 pasa de 100 a 103: tres píxeles, justo el límite. Se comprueban las dos cosas, el número y
		// la decisión, para saber cuál de las dos falla si la prueba se rompe.
		assertEquals(3, PausaServiceImpl.movimientoMayor(auto, 103, 200, 300, 400));
		assertTrue(casiIgual(auto, 103, 200, 300, 400));
	}

	@Test
	void cuatroPxYaEsAjuste() throws Exception {
		JsonNode auto = PausaServiceImpl.recuadroDe(pausa(), 1);
		// Un píxel más que el límite. Junto con la prueba anterior, fija la frontera exacta: tres se
		// rechaza, cuatro se acepta. Es la forma estándar de probar un umbral, por los dos lados.
		assertEquals(4, PausaServiceImpl.movimientoMayor(auto, 104, 200, 300, 400));
		assertFalse(casiIgual(auto, 104, 200, 300, 400));
	}

	@Test
	void unSoloBordeMovidoMasDeTresPxBastaParaAceptar() throws Exception {
		JsonNode auto = PausaServiceImpl.recuadroDe(pausa(), 1);
		// solo y1 se movió 10 px; los demás bordes quedaron igual
		// Comprueba que se mira el borde que MÁS se movió y no un promedio: con un promedio, mover un solo
		// borde 10 píxeles daría 2.5 y se rechazaría, que no es lo que se quiere.
		assertEquals(10, PausaServiceImpl.movimientoMayor(auto, 100, 200, 300, 410));
		assertFalse(casiIgual(auto, 100, 200, 300, 410));
	}

	@Test
	void sinEseCilindroNoHayRecuadroQueComparar() throws Exception {
		// El caso de un cilindro que no está en la pausa: devuelve null, y el servicio responde 400.
		assertNull(PausaServiceImpl.recuadroDe(pausa(), 2));
		// Y la comprobación contraria, para asegurarse de que el null de arriba no es por un fallo del
		// método: con el cilindro que sí existe, devuelve algo.
		assertNotNull(PausaServiceImpl.recuadroDe(pausa(), 1));
	}
}
