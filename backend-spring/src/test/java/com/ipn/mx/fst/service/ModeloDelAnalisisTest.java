// Mismo paquete que la clase que prueba, dentro de src/test. Importa aquí especialmente: ModeloDelAnalisis
// no es public (solo la ve su paquete), así que esta prueba solo puede llamarla por estar en el mismo.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

// Con qué modelo se hizo un análisis: el texto que llevan el PDF y los reportes en modo desarrollador. Sin base de datos.
//
// La clase es pura (ver ModeloDelAnalisis): recibe un mapa y devuelve un texto. Por eso la prueba puede
// escribir ese mapa a mano y no necesita ni worker, ni volumen, ni base.
class ModeloDelAnalisisTest {

	// El mapa de ejemplo, con los cinco campos llenos, tal como lo escribe el worker en tiempos.json.
	// Un método y no una constante: así cada prueba recibe un mapa nuevo y ninguna puede ensuciar a otra.
	private static Map<String, Object> modelo() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("archivo", "fst_cnn_seg.pt");
		m.put("version", "5.0.0");
		// Un SHA-256 completo, de 64 caracteres: hace falta entero para comprobar que se recorta a 16.
		m.put("sha256", "480c5803998562f59896c42925e965d3b87afbe5db888365988e20286ee769ae");
		m.put("tamanoMB", 119.6);
		// Una fecha con hora y zona, para comprobar que se queda solo el día.
		m.put("modificado", "2026-10-19T21:27:32-06:00");
		return m;
	}

	@Test
	void el_texto_dice_el_archivo_la_red_la_huella_el_tamano_y_la_fecha() {
		// El texto completo, con sus cinco partes y el separador "·". Comparar la línea entera, y no cada
		// parte por separado, fija también el ORDEN y el separador: si alguien los cambiara, esta prueba
		// lo diría, porque el PDF y los reportes esperan exactamente este formato.
		assertEquals("fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5 · 119.6 MB · archivo del 2026-10-19",
				ModeloDelAnalisis.texto(modelo()));
	}

	@Test
	void la_huella_son_los_primeros_16_caracteres_y_la_fecha_solo_el_dia() {
		// Los dos recortes, por separado: 64 caracteres a 16, y la fecha con hora a solo el día.
		assertEquals("480c5803998562f5", ModeloDelAnalisis.huella(modelo()));
		assertEquals("2026-10-19", ModeloDelAnalisis.fecha(modelo()));
	}

	@Test
	void un_analisis_de_antes_de_que_se_anotara_el_modelo_dice_sin_registro() {
		// El caso del null: los análisis viejos no traen el bloque. texto devuelve una frase legible (el
		// PDF la imprime tal cual) y los otros dos, null, porque no hay nada que recortar.
		assertEquals("sin registro", ModeloDelAnalisis.texto(null));
		assertNull(ModeloDelAnalisis.huella(null));
		assertNull(ModeloDelAnalisis.fecha(null));
	}

	@Test
	void lo_que_no_se_sabe_se_omite() {
		// Un mapa con el archivo y todo lo demás vacío o ausente. El "   " de sha256 comprueba que un
		// texto de solo espacios cuenta como ausente (es lo que hace isBlank).
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("archivo", "fst_cnn_seg.pt");
		m.put("version", null);
		m.put("sha256", "   ");
		m.put("tamanoMB", null);
		m.put("modificado", null);
		// Solo el archivo, sin separadores sueltos al final: es lo que comprueba String.join.
		assertEquals("fst_cnn_seg.pt", ModeloDelAnalisis.texto(m));
		// Y el caso extremo: un mapa vacío (que no es null) da el texto de respaldo.
		assertEquals("modelo desconocido", ModeloDelAnalisis.texto(new LinkedHashMap<>()));
	}

	@Test
	void una_huella_o_una_fecha_cortas_se_muestran_completas() {
		// El caso límite de los dos recortes: un valor MÁS CORTO que el tope. Sin la comprobación de
		// longitud, substring(0, 16) sobre un texto de 6 caracteres lanzaría una excepción. Esta prueba
		// es la que impide que alguien "simplifique" esos ternarios.
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("sha256", "abc123");
		m.put("modificado", "2026-10");
		assertEquals("abc123", ModeloDelAnalisis.huella(m));
		assertEquals("2026-10", ModeloDelAnalisis.fecha(m));
	}

}
