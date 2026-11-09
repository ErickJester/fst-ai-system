package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ipn.mx.fst.utilerias.ErrorApi;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

// GET /dev/modelo-actual sin base de datos: lo que el worker escribe en modelo-actual.json, leído y con su texto.
class ModeloActualDeDesarrolloTest {

	@TempDir
	Path raiz;

	private ModeloActualDeDesarrollo servicio(boolean modo) {
		ModeloActualDeDesarrollo servicio = new ModeloActualDeDesarrollo();
		ReflectionTestUtils.setField(servicio, "raizConfigurada", raiz.toString());
		ReflectionTestUtils.setField(servicio, "modoDesarrollador", modo);
		return servicio;
	}

	private void escribir(String contenido) throws IOException {
		Files.writeString(raiz.resolve(ModeloActualDeDesarrollo.ARCHIVO), contenido);
	}

	@Test
	void sin_modo_desarrollador_responde_404_como_la_salida_del_modelo() {
		ErrorApi error = assertThrows(ErrorApi.class, () -> servicio(false).modeloActual());
		assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
	}

	@Test
	void si_el_worker_no_informo_responde_archivo_null_con_el_motivo() {
		Map<String, Object> datos = servicio(true).modeloActual();
		assertNull(datos.get("archivo"));
		assertEquals(ModeloActualDeDesarrollo.MOTIVO_SIN_INFORME, datos.get("motivo"));
	}

	@Test
	void un_archivo_roto_tambien_dice_que_no_hubo_informe() throws IOException {
		escribir("{ no es json");
		Map<String, Object> datos = servicio(true).modeloActual();
		assertNull(datos.get("archivo"));
		assertEquals(ModeloActualDeDesarrollo.MOTIVO_SIN_INFORME, datos.get("motivo"));
	}

	@Test
	void con_el_modelo_informado_devuelve_su_identidad_y_su_texto() throws IOException {
		escribir("{\"archivo\":\"fst_cnn_seg.pt\",\"version\":\"5.0.0\",\"sha256\":\"480c5803998562f5aaaa\","
				+ "\"tamanoMB\":119.6,\"modificado\":\"2026-10-19T10:00:00-06:00\",\"actualizado\":\"2026-11-08T12:00:00-06:00\"}");
		Map<String, Object> datos = servicio(true).modeloActual();
		assertEquals("fst_cnn_seg.pt", datos.get("archivo"));
		assertEquals("480c5803998562f5aaaa", datos.get("sha256"));
		assertEquals("2026-11-08T12:00:00-06:00", datos.get("actualizado"));
		assertEquals("fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5 · 119.6 MB · archivo del 2026-10-19", datos.get("texto"));
	}

	@Test
	void si_el_modelo_no_existia_el_worker_lo_dice_con_la_ruta_y_sin_texto() throws IOException {
		escribir("{\"archivo\":null,\"ruta\":\"/data/modelos/fst_cnn_seg.pt\",\"actualizado\":\"2026-11-08T12:00:00-06:00\"}");
		Map<String, Object> datos = servicio(true).modeloActual();
		assertNull(datos.get("archivo"));
		assertEquals("/data/modelos/fst_cnn_seg.pt", datos.get("ruta"));
		assertTrue(!datos.containsKey("texto"));
	}
}
