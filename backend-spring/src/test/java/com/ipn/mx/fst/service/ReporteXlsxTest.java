package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

// Encargo 39: el XLSX se abre con POI y se leen sus celdas, sin base de datos.
class ReporteXlsxTest {

	private static XSSFWorkbook abrir(TablaDeReporte tabla) throws IOException {
		return new XSSFWorkbook(new ByteArrayInputStream(ReporteXlsx.armar(tabla)));
	}

	private static Sheet hoja(XSSFWorkbook libro) {
		assertEquals(1, libro.getNumberOfSheets());
		Sheet hoja = libro.getSheet("Resultados");
		assertNotNull(hoja);
		return hoja;
	}

	@Test
	void tiene_una_hoja_Resultados_con_el_encabezado_de_D_99_en_negrita() throws IOException {
		try (XSSFWorkbook libro = abrir(ReporteCsvTest.tabla("Control", false, List.of()))) {
			Sheet hoja = hoja(libro);
			for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
				assertEquals(TablaDeReporte.ENCABEZADO.get(i), hoja.getRow(0).getCell(i).getStringCellValue());
				assertTrue(libro.getFontAt(hoja.getRow(0).getCell(i).getCellStyle().getFontIndex()).getBold());
			}
		}
	}

	@Test
	void las_filas_llevan_el_texto_como_texto_y_los_segundos_como_numeros() throws IOException {
		try (XSSFWorkbook libro = abrir(ReporteCsvTest.tabla("Control", false, List.of()))) {
			Sheet hoja = hoja(libro);
			assertEquals("Control", hoja.getRow(1).getCell(0).getStringCellValue());
			assertEquals("A", hoja.getRow(1).getCell(1).getStringCellValue());
			assertEquals("Espécimen 1", hoja.getRow(1).getCell(2).getStringCellValue());
			assertEquals("0:00-1:00", hoja.getRow(1).getCell(3).getStringCellValue());
			assertEquals(CellType.NUMERIC, hoja.getRow(1).getCell(4).getCellType());
			assertEquals(10.0, hoja.getRow(1).getCell(4).getNumericCellValue());
			assertEquals(30.0, hoja.getRow(1).getCell(6).getNumericCellValue());
			assertEquals(60.0, hoja.getRow(3).getCell(6).getNumericCellValue());
		}
	}

	@Test
	void las_filas_de_estadisticos_son_numeros_y_estan_en_gris_la_de_Varianza_incluida() throws IOException {
		try (XSSFWorkbook libro = abrir(ReporteCsvTest.tabla("Control", false, List.of()))) {
			Sheet hoja = hoja(libro);
			assertEquals("Media", hoja.getRow(4).getCell(2).getStringCellValue());
			assertEquals(35.5, hoja.getRow(4).getCell(4).getNumericCellValue());
			assertEquals("Desviación estándar", hoja.getRow(5).getCell(2).getStringCellValue());
			assertEquals(7.1, hoja.getRow(5).getCell(4).getNumericCellValue());
			assertEquals("Varianza", hoja.getRow(6).getCell(2).getStringCellValue());
			assertEquals(CellType.NUMERIC, hoja.getRow(6).getCell(4).getCellType());
			assertEquals(50.0, hoja.getRow(6).getCell(4).getNumericCellValue());
			assertEquals(0.5, hoja.getRow(6).getCell(7).getNumericCellValue());
			for (int fila = 4; fila <= 6; fila++) {
				assertEquals(FillPatternType.SOLID_FOREGROUND, hoja.getRow(fila).getCell(4).getCellStyle().getFillPattern());
			}
			// Las filas de datos no están en gris.
			assertEquals(FillPatternType.NO_FILL, hoja.getRow(1).getCell(4).getCellStyle().getFillPattern());
		}
	}

	@Test
	void la_revision_y_el_aviso_van_al_final_como_en_el_csv() throws IOException {
		try (XSSFWorkbook libro = abrir(ReporteCsvTest.tabla("Control", true, List.of(new TablaDeReporte.Revisado("A", 2))))) {
			Sheet hoja = hoja(libro);
			assertEquals("Revisión humana", hoja.getRow(7).getCell(2).getStringCellValue());
			assertEquals("Sí: Tanda A · Espécimen 2", hoja.getRow(7).getCell(3).getStringCellValue());
			assertEquals("Aviso", hoja.getRow(8).getCell(2).getStringCellValue());
			assertEquals(TablaDeReporte.TEXTO_AVISO, hoja.getRow(8).getCell(3).getStringCellValue());
			assertEquals(8, hoja.getLastRowNum());
		}
	}

	@Test
	void un_texto_que_parece_formula_se_guarda_como_texto() throws IOException {
		try (XSSFWorkbook libro = abrir(ReporteCsvTest.tabla("=SUM(A1:A3)", false, List.of()))) {
			assertEquals(CellType.STRING, hoja(libro).getRow(1).getCell(0).getCellType());
			assertEquals("=SUM(A1:A3)", hoja(libro).getRow(1).getCell(0).getStringCellValue());
		}
	}

}
