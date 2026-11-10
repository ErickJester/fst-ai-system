// Mismo paquete que la clase que prueba, dentro de src/test.
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
// @TempDir: JUnit crea una carpeta temporal y la borra al terminar la prueba.
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
// ReflectionTestUtils: pone un valor en un campo privado sin necesidad de un setter.
import org.springframework.test.util.ReflectionTestUtils;

// GET /dev/modelo-actual sin base de datos: lo que el worker escribe en modelo-actual.json, leído y con su texto.
//
// Esta prueba sí toca el DISCO, pero solo una carpeta temporal: no necesita base ni Docker, y JUnit la
// limpia sola. Es el punto medio entre una prueba pura y una de integración.
class ModeloActualDeDesarrolloTest {

	// JUnit rellena este campo con una carpeta nueva por cada prueba, y la borra al terminar. Que sea
	// nueva en cada una es lo que evita que una prueba vea el archivo que escribió otra.
	@TempDir
	Path raiz;

	// Construye el servicio a mano, sin Spring: se crea con new y se le ponen los dos campos @Value con
	// ReflectionTestUtils. Levantar el contexto entero de Spring para esto tardaría segundos; así son
	// milisegundos.
	private ModeloActualDeDesarrollo servicio(boolean modo) {
		ModeloActualDeDesarrollo servicio = new ModeloActualDeDesarrollo();
		ReflectionTestUtils.setField(servicio, "raizConfigurada", raiz.toString());
		ReflectionTestUtils.setField(servicio, "modoDesarrollador", modo);
		return servicio;
	}

	// Escribe el archivo que normalmente deja el worker. Usa la constante ARCHIVO de la clase probada, no
	// el nombre escrito a mano: si el nombre cambiara, la prueba lo seguiría.
	private void escribir(String contenido) throws IOException {
		Files.writeString(raiz.resolve(ModeloActualDeDesarrollo.ARCHIVO), contenido);
	}

	@Test
	void sin_modo_desarrollador_responde_404_como_la_salida_del_modelo() {
		// assertThrows DEVUELVE la excepción, lo que permite comprobar además su código. Aquí importa:
		// tiene que ser 404 y no 403, para que la ruta no delate que existe.
		ErrorApi error = assertThrows(ErrorApi.class, () -> servicio(false).modeloActual());
		assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
	}

	@Test
	void si_el_worker_no_informo_responde_archivo_null_con_el_motivo() {
		// Sin escribir nada: la carpeta temporal está vacía, que es lo que pasa antes de que el worker
		// arranque por primera vez. No es un error, es una respuesta con su explicación.
		Map<String, Object> datos = servicio(true).modeloActual();
		assertNull(datos.get("archivo"));
		assertEquals(ModeloActualDeDesarrollo.MOTIVO_SIN_INFORME, datos.get("motivo"));
	}

	@Test
	void un_archivo_roto_tambien_dice_que_no_hubo_informe() throws IOException {
		// JSON inválido a propósito: es lo que se vería si se leyera el archivo mientras el worker lo
		// está escribiendo. La respuesta es la misma que si no existiera, y no un 500.
		escribir("{ no es json");
		Map<String, Object> datos = servicio(true).modeloActual();
		assertNull(datos.get("archivo"));
		assertEquals(ModeloActualDeDesarrollo.MOTIVO_SIN_INFORME, datos.get("motivo"));
	}

	@Test
	void con_el_modelo_informado_devuelve_su_identidad_y_su_texto() throws IOException {
		// El caso bueno, con el archivo tal como lo escribe el worker.
		escribir("{\"archivo\":\"fst_cnn_seg.pt\",\"version\":\"5.0.0\",\"sha256\":\"480c5803998562f5aaaa\","
				+ "\"tamanoMB\":119.6,\"modificado\":\"2026-10-19T10:00:00-06:00\",\"actualizado\":\"2026-11-08T12:00:00-06:00\"}");
		Map<String, Object> datos = servicio(true).modeloActual();
		assertEquals("fst_cnn_seg.pt", datos.get("archivo"));
		assertEquals("480c5803998562f5aaaa", datos.get("sha256"));
		// "actualizado" no lo usa el servicio para nada, y aun así sale: comprueba que los campos del
		// archivo se copian TODOS, no solo los conocidos. Así, un campo nuevo del worker llega solo.
		assertEquals("2026-11-08T12:00:00-06:00", datos.get("actualizado"));
		// Y el texto ya redactado, con el mismo formato que el PDF y los reportes (ver ModeloDelAnalisis).
		assertEquals("fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5 · 119.6 MB · archivo del 2026-10-19", datos.get("texto"));
	}

	@Test
	void si_el_modelo_no_existia_el_worker_lo_dice_con_la_ruta_y_sin_texto() throws IOException {
		// El worker informó, pero para decir que no encontró el archivo del modelo: archivo en null y la
		// ruta donde lo buscó. Es información útil para quien configura el sistema.
		escribir("{\"archivo\":null,\"ruta\":\"/data/modelos/fst_cnn_seg.pt\",\"actualizado\":\"2026-11-08T12:00:00-06:00\"}");
		Map<String, Object> datos = servicio(true).modeloActual();
		assertNull(datos.get("archivo"));
		assertEquals("/data/modelos/fst_cnn_seg.pt", datos.get("ruta"));
		// La clave "texto" NO debe existir: sin archivo no hay línea que redactar, y el servicio solo la
		// añade cuando hay nombre. containsKey distingue "la clave no está" de "está y vale null", que
		// aquí son cosas distintas.
		assertTrue(!datos.containsKey("texto"));
	}
}
