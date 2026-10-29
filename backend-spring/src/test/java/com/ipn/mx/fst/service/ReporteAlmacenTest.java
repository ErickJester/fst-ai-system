package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ipn.mx.fst.domain.valores.FormatoReporte;
import org.junit.jupiter.api.Test;

// La ruta de cada reporte guardado (encargo 34): reportes/{idExperimento}/... según el formato y el alcance. Sin base de
// datos ni Docker.
class ReporteAlmacenTest {

	@Test
	void el_pdf_es_del_experimento() {
		assertEquals("reportes/5/experimento.pdf", ReporteAlmacen.rutaRelativa(5, FormatoReporte.PDF, "experimento"));
	}

	@Test
	void el_csv_y_el_xlsx_son_del_grupo_o_de_la_tanda() {
		assertEquals("reportes/5/grupo-3.csv", ReporteAlmacen.rutaRelativa(5, FormatoReporte.CSV, "grupo-3"));
		assertEquals("reportes/5/tanda-7.xlsx", ReporteAlmacen.rutaRelativa(5, FormatoReporte.XLSX, "tanda-7"));
	}

	@Test
	void el_pdf_no_puede_ser_de_grupo_ni_de_tanda() {
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.PDF, "grupo-3"));
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.PDF, "tanda-7"));
	}

	@Test
	void el_csv_y_el_xlsx_no_pueden_ser_del_experimento() {
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.CSV, "experimento"));
	}

	@Test
	void un_alcance_con_otro_texto_se_rechaza_y_no_puede_salir_de_la_carpeta() {
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.CSV, "../6"));
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.CSV, "grupo-3/x"));
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.CSV, null));
	}
}
