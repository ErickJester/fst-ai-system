package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ipn.mx.fst.service.TablaDeReporte.Estadistico;
import com.ipn.mx.fst.service.TablaDeReporte.FilaDeReporte;
import com.ipn.mx.fst.service.TablaDeReporte.Revisado;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// Encargo 39: el CSV (D-99) armado desde una TablaDeReporte, sin base de datos.
class ReporteCsvTest {

	private static final String ENCABEZADO = "Grupo,Tanda,Espécimen,Tiempo (min:seg),Nado,Escalamiento,Inmovilidad,Conducta activa";

	static TablaDeReporte tabla(String grupo, boolean mixto, List<Revisado> revisados) {
		return new TablaDeReporte(grupo,
				List.of(new FilaDeReporte("A", 1, 1, List.of(10L, 20L, 30L, 0L)),
						new FilaDeReporte("A", 1, 2, List.of(5L, 0L, 40L, 1L)),
						new FilaDeReporte("B", 2, 1, List.of(0L, 0L, 60L, 0L))),
				List.of(new Estadistico(35.5, 7.1, 50.0), new Estadistico(0.0, 0.0, 0.0), new Estadistico(12.3, 3.0, 9.0),
						new Estadistico(0.5, 0.7, 0.5)),
				mixto, revisados);
	}

	// Las líneas del CSV sin el BOM; el último elemento es el vacío que queda tras el \r\n final.
	private static List<String> lineas(byte[] csv) {
		String texto = new String(csv, StandardCharsets.UTF_8);
		return Arrays.asList(texto.substring(1).split("\r\n", -1));
	}

	@Test
	void empieza_con_el_BOM_de_UTF8_y_las_lineas_terminan_en_CRLF() {
		byte[] csv = ReporteCsv.armar(tabla("Control", false, List.of()));
		assertEquals((byte) 0xEF, csv[0]);
		assertEquals((byte) 0xBB, csv[1]);
		assertEquals((byte) 0xBF, csv[2]);
		String texto = new String(csv, StandardCharsets.UTF_8);
		assertTrue(texto.endsWith("\r\n"));
		assertFalse(texto.replace("\r\n", "").contains("\n"));
		// Los acentos del encabezado salen en UTF-8, no como signos de interrogación.
		assertTrue(texto.contains("Espécimen"));
	}

	@Test
	void el_encabezado_es_exactamente_el_de_D_99() {
		assertEquals(ENCABEZADO, lineas(ReporteCsv.armar(tabla("Control", false, List.of()))).get(0));
	}

	@Test
	void una_fila_por_especimen_y_minuto_con_segundos_enteros() {
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", false, List.of())));
		assertEquals("Control,A,Espécimen 1,0:00-1:00,10,20,30,0", lineas.get(1));
		assertEquals("Control,A,Espécimen 1,1:00-2:00,5,0,40,1", lineas.get(2));
		assertEquals("Control,B,Espécimen 2,0:00-1:00,0,0,60,0", lineas.get(3));
	}

	@Test
	void despues_de_las_filas_van_media_desviacion_y_varianza_con_un_decimal_y_punto() {
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", false, List.of())));
		assertEquals(",,Media,,35.5,0.0,12.3,0.5", lineas.get(4));
		assertEquals(",,Desviación estándar,,7.1,0.0,3.0,0.7", lineas.get(5));
		assertEquals(",,Varianza,,50.0,0.0,9.0,0.5", lineas.get(6));
	}

	@Test
	void sin_revision_la_ultima_fila_dice_No_y_sin_mezcla_no_hay_aviso() {
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", false, List.of())));
		assertEquals(",,Revisión humana,No", lineas.get(7));
		assertEquals(9, lineas.size());      // encabezado + 3 filas + 3 estadísticos + revisión + el vacío final
		assertEquals("", lineas.get(8));
	}

	@Test
	void con_revision_lista_los_especimenes_revisados() {
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", false,
				List.of(new Revisado("A", 2), new Revisado("B", 1)))));
		assertEquals(",,Revisión humana,Sí: Tanda A · Espécimen 2; Tanda B · Espécimen 1", lineas.get(7));
	}

	@Test
	void con_niveles_mezclados_agrega_la_fila_de_aviso() {
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", true, List.of())));
		assertEquals(",,Aviso,Mezcla tandas de clasificación precisa y agrupada", lineas.get(8));
		assertEquals(10, lineas.size());
	}

	@Test
	void entrecomilla_y_escapa_segun_RFC_4180() {
		assertEquals("normal", ReporteCsv.celda("normal"));
		assertEquals("\"con, coma\"", ReporteCsv.celda("con, coma"));
		assertEquals("\"dice \"\"hola\"\"\"", ReporteCsv.celda("dice \"hola\""));
		assertEquals("\"dos\nlineas\"", ReporteCsv.celda("dos\nlineas"));

		List<String> lineas = lineas(ReporteCsv.armar(tabla("Grupo, \"uno\"", false, List.of())));
		assertEquals("\"Grupo, \"\"uno\"\"\",A,Espécimen 1,0:00-1:00,10,20,30,0", lineas.get(1));
	}

}
