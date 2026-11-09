package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ipn.mx.fst.service.SegundosDeResultados.FilaSegundo;
import com.ipn.mx.fst.service.TablaDeReporte.Estadistico;
import com.ipn.mx.fst.service.TablaDeReporte.FilaDeReporte;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

// El Excel de todo el experimento, leído de vuelta: las dos hojas, sus encabezados, las columnas 1 o 0 de cada segundo, el
// «sin ver» y el origen. Sin base de datos.
class ExcelDelExperimentoTest {

	private static final TablaDeReporte TABLA = new TablaDeReporte("Control",
			List.of(new FilaDeReporte("A", 1, 1, List.of(40L, 10L, 5L, 0L, 5L))),
			List.of(new Estadistico(40, 0, 0), new Estadistico(10, 0, 0), new Estadistico(5, 0, 0),
					new Estadistico(0, 0, 0), new Estadistico(5, 0, 0)),
			false, List.of());

	@Test
	void el_libro_tiene_las_hojas_resultados_y_segundo_a_segundo() throws IOException {
		try (XSSFWorkbook libro = abrir(ExcelDelExperimento.armar(List.of(TABLA), List.of()))) {
			assertEquals("Resultados", libro.getSheetAt(0).getSheetName());
			assertEquals("Segundo a segundo", libro.getSheetAt(1).getSheetName());
		}
	}

	@Test
	void la_hoja_de_segundos_tiene_una_columna_por_conducta_con_uno_o_cero_y_el_origen() throws IOException {
		List<FilaSegundo> segundos = List.of(
				new FilaSegundo(1, "Control", "A", 1, 1, "nado activo", "maquina"),
				new FilaSegundo(1, "Control", "A", 1, 2, null, "dudosa"),
				new FilaSegundo(1, "Control", "A", 1, 3, "conducta activa", "corregida"));
		try (XSSFWorkbook libro = abrir(ExcelDelExperimento.armar(List.of(TABLA), segundos))) {
			Sheet hoja = libro.getSheet("Segundo a segundo");
			Row encabezado = hoja.getRow(0);
			assertEquals("Grupo", encabezado.getCell(0).getStringCellValue());
			assertEquals("Tiempo (s)", encabezado.getCell(3).getStringCellValue());
			assertEquals("Conducta", encabezado.getCell(4).getStringCellValue());
			assertEquals("Nado activo", encabezado.getCell(5).getStringCellValue());
			assertEquals("Sin ver", encabezado.getCell(9).getStringCellValue());
			assertEquals("Origen", encabezado.getCell(10).getStringCellValue());

			// Segundo 1: nado activo → 1 en la primera columna de conducta, 0 en las demás.
			Row nado = hoja.getRow(1);
			assertEquals("Espécimen 1", nado.getCell(2).getStringCellValue());
			assertEquals(1.0, nado.getCell(3).getNumericCellValue());
			assertEquals("nado activo", nado.getCell(4).getStringCellValue());
			assertEquals(1.0, nado.getCell(5).getNumericCellValue());
			assertEquals(0.0, nado.getCell(9).getNumericCellValue());
			assertEquals("maquina", nado.getCell(10).getStringCellValue());

			// Segundo 2: sin conducta (null) → «sin ver» en la conducta y 1 en su columna; el origen dudosa.
			Row sinVer = hoja.getRow(2);
			assertEquals("sin ver", sinVer.getCell(4).getStringCellValue());
			assertEquals(1.0, sinVer.getCell(9).getNumericCellValue());
			assertEquals(0.0, sinVer.getCell(5).getNumericCellValue());
			assertEquals("dudosa", sinVer.getCell(10).getStringCellValue());

			// Segundo 3: conducta activa, origen corregida.
			Row activa = hoja.getRow(3);
			assertEquals(1.0, activa.getCell(8).getNumericCellValue());
			assertEquals("corregida", activa.getCell(10).getStringCellValue());
		}
	}

	@Test
	void la_hoja_de_resultados_trae_la_tabla_del_grupo_con_sus_estadisticos() throws IOException {
		try (XSSFWorkbook libro = abrir(ExcelDelExperimento.armar(List.of(TABLA), List.of()))) {
			Sheet hoja = libro.getSheet("Resultados");
			Row primera = hoja.getRow(1);
			assertEquals("Control", primera.getCell(0).getStringCellValue());
			assertEquals("A", primera.getCell(1).getStringCellValue());
			assertEquals("Espécimen 1", primera.getCell(2).getStringCellValue());
			assertEquals("0:00-1:00", primera.getCell(3).getStringCellValue());
			assertEquals(40.0, primera.getCell(4).getNumericCellValue());
			// Después de la fila del minuto vienen Media, Desviación estándar y Varianza, y la revisión humana.
			assertEquals("Media", hoja.getRow(2).getCell(2).getStringCellValue());
			assertEquals("Desviación estándar", hoja.getRow(3).getCell(2).getStringCellValue());
			assertEquals("Varianza", hoja.getRow(4).getCell(2).getStringCellValue());
			assertEquals("Revisión humana", hoja.getRow(5).getCell(2).getStringCellValue());
			assertEquals("No", hoja.getRow(5).getCell(3).getStringCellValue());
		}
	}

	@Test
	void sin_segundos_la_hoja_de_segundos_solo_trae_el_encabezado() throws IOException {
		try (XSSFWorkbook libro = abrir(ExcelDelExperimento.armar(List.of(TABLA), List.of()))) {
			Sheet hoja = libro.getSheet("Segundo a segundo");
			assertEquals("Origen", hoja.getRow(0).getCell(10).getStringCellValue());
			assertNull(hoja.getRow(1));
		}
	}

	private static XSSFWorkbook abrir(byte[] bytes) throws IOException {
		return new XSSFWorkbook(new ByteArrayInputStream(bytes));
	}
}
