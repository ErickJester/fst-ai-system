package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

// Con qué modelo se hizo un análisis: el texto que llevan el PDF y los reportes en modo desarrollador. Sin base de datos.
class ModeloDelAnalisisTest {

	private static Map<String, Object> modelo() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("archivo", "fst_cnn_seg.pt");
		m.put("version", "5.0.0");
		m.put("sha256", "480c5803998562f59896c42925e965d3b87afbe5db888365988e20286ee769ae");
		m.put("tamanoMB", 119.6);
		m.put("modificado", "2026-10-19T21:27:32-06:00");
		return m;
	}

	@Test
	void el_texto_dice_el_archivo_la_red_la_huella_el_tamano_y_la_fecha() {
		assertEquals("fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5 · 119.6 MB · archivo del 2026-10-19",
				ModeloDelAnalisis.texto(modelo()));
	}

	@Test
	void la_huella_son_los_primeros_16_caracteres_y_la_fecha_solo_el_dia() {
		assertEquals("480c5803998562f5", ModeloDelAnalisis.huella(modelo()));
		assertEquals("2026-10-19", ModeloDelAnalisis.fecha(modelo()));
	}

	@Test
	void un_analisis_de_antes_de_que_se_anotara_el_modelo_dice_sin_registro() {
		assertEquals("sin registro", ModeloDelAnalisis.texto(null));
		assertNull(ModeloDelAnalisis.huella(null));
		assertNull(ModeloDelAnalisis.fecha(null));
	}

	@Test
	void lo_que_no_se_sabe_se_omite() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("archivo", "fst_cnn_seg.pt");
		m.put("version", null);
		m.put("sha256", "   ");
		m.put("tamanoMB", null);
		m.put("modificado", null);
		assertEquals("fst_cnn_seg.pt", ModeloDelAnalisis.texto(m));
		assertEquals("modelo desconocido", ModeloDelAnalisis.texto(new LinkedHashMap<>()));
	}

	@Test
	void una_huella_o_una_fecha_cortas_se_muestran_completas() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("sha256", "abc123");
		m.put("modificado", "2026-10");
		assertEquals("abc123", ModeloDelAnalisis.huella(m));
		assertEquals("2026-10", ModeloDelAnalisis.fecha(m));
	}

}
