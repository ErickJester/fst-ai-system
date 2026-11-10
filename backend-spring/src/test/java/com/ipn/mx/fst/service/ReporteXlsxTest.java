// Mismo paquete que la clase que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
// CellType: el TIPO de una celda (texto, número, fórmula). Es lo que se comprueba abajo.
import org.apache.poi.ss.usermodel.CellType;
// FillPatternType: el relleno de una celda, para comprobar el gris de las filas de resumen.
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

// Encargo 39: el XLSX se abre con POI y se leen sus celdas, sin base de datos.
//
// Comparte los datos de prueba con ReporteCsvTest (el método tabla() de allí): es la garantía de que los
// dos formatos se comprueban con EXACTAMENTE los mismos números, que es lo que se espera de ellos.
class ReporteXlsxTest {

	// Genera el XLSX y lo vuelve a abrir para poder leer sus celdas.
	private static XSSFWorkbook abrir(TablaDeReporte tabla) throws IOException {
		return new XSSFWorkbook(new ByteArrayInputStream(ReporteXlsx.armar(tabla)));
	}

	// La única hoja del libro, con dos comprobaciones de paso: que no haya más de una y que exista.
	// Ponerlas aquí evita repetirlas en cada prueba.
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
			// Se recorre TablaDeReporte.ENCABEZADO en vez de escribir los títulos a mano: así la prueba
			// comprueba que el XLSX usa la lista compartida, y no una copia que podría desviarse.
			for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
				assertEquals(TablaDeReporte.ENCABEZADO.get(i), hoja.getRow(0).getCell(i).getStringCellValue());
				// Y que esté en negrita: se saca el índice de la tipografía de la celda y se consulta en
				// el libro. Es el formato lo que distingue el encabezado de los datos al abrir el archivo.
				assertTrue(libro.getFontAt(hoja.getRow(0).getCell(i).getCellStyle().getFontIndex()).getBold());
			}
		}
	}

	@Test
	void las_filas_llevan_el_texto_como_texto_y_los_segundos_como_numeros() throws IOException {
		try (XSSFWorkbook libro = abrir(ReporteCsvTest.tabla("Control", false, List.of()))) {
			Sheet hoja = hoja(libro);
			// Las cuatro primeras columnas, como texto.
			assertEquals("Control", hoja.getRow(1).getCell(0).getStringCellValue());
			assertEquals("A", hoja.getRow(1).getCell(1).getStringCellValue());
			assertEquals("Espécimen 1", hoja.getRow(1).getCell(2).getStringCellValue());
			assertEquals("0:00-1:00", hoja.getRow(1).getCell(3).getStringCellValue());
			// Y la quinta como NÚMERO. Se comprueba el tipo además del valor: si se hubiera guardado como
			// texto, el valor sería el mismo al leerlo pero Excel no lo podría sumar.
			assertEquals(CellType.NUMERIC, hoja.getRow(1).getCell(4).getCellType());
			assertEquals(10.0, hoja.getRow(1).getCell(4).getNumericCellValue());
			assertEquals(30.0, hoja.getRow(1).getCell(6).getNumericCellValue());
			// Otra fila, para comprobar que no solo la primera está bien.
			assertEquals(60.0, hoja.getRow(3).getCell(6).getNumericCellValue());
		}
	}

	@Test
	void las_filas_de_estadisticos_son_numeros_y_estan_en_gris_la_de_Varianza_incluida() throws IOException {
		try (XSSFWorkbook libro = abrir(ReporteCsvTest.tabla("Control", false, List.of()))) {
			Sheet hoja = hoja(libro);
			// Las tres filas de resumen, con su etiqueta y su valor.
			assertEquals("Media", hoja.getRow(4).getCell(2).getStringCellValue());
			assertEquals(35.5, hoja.getRow(4).getCell(4).getNumericCellValue());
			assertEquals("Desviación estándar", hoja.getRow(5).getCell(2).getStringCellValue());
			assertEquals(7.1, hoja.getRow(5).getCell(4).getNumericCellValue());
			assertEquals("Varianza", hoja.getRow(6).getCell(2).getStringCellValue());
			// El tipo se comprueba expresamente en la varianza: es la que lleva el formato de un decimal,
			// y un formato mal aplicado podría convertirla en texto.
			assertEquals(CellType.NUMERIC, hoja.getRow(6).getCell(4).getCellType());
			assertEquals(50.0, hoja.getRow(6).getCell(4).getNumericCellValue());
			assertEquals(0.5, hoja.getRow(6).getCell(7).getNumericCellValue());
			// «Sin ver», la quinta columna de segundos, con sus segundos y su media, DE y varianza.
			// Es la columna que más fácil se olvida, por eso tiene sus propias comprobaciones.
			assertEquals("Sin ver", hoja.getRow(0).getCell(8).getStringCellValue());
			assertEquals(14.0, hoja.getRow(2).getCell(8).getNumericCellValue());
			assertEquals(4.7, hoja.getRow(4).getCell(8).getNumericCellValue());
			assertEquals(8.1, hoja.getRow(5).getCell(8).getNumericCellValue());
			assertEquals(65.3, hoja.getRow(6).getCell(8).getNumericCellValue());
			// Las tres filas de resumen tienen que estar rellenas de gris.
			for (int fila = 4; fila <= 6; fila++) {
				assertEquals(FillPatternType.SOLID_FOREGROUND, hoja.getRow(fila).getCell(4).getCellStyle().getFillPattern());
			}
			// Las filas de datos no están en gris.
			// La comprobación contraria: sin ella, un estilo gris aplicado a TODA la hoja pasaría la
			// prueba anterior sin que el resumen se distinguiera de nada.
			assertEquals(FillPatternType.NO_FILL, hoja.getRow(1).getCell(4).getCellStyle().getFillPattern());
		}
	}

	@Test
	void la_revision_y_el_aviso_van_al_final_como_en_el_csv() throws IOException {
		// Con nivelMixto en true y un espécimen revisado: salen las dos filas del final.
		try (XSSFWorkbook libro = abrir(ReporteCsvTest.tabla("Control", true, List.of(new TablaDeReporte.Revisado("A", 2))))) {
			Sheet hoja = hoja(libro);
			assertEquals("Revisión humana", hoja.getRow(7).getCell(2).getStringCellValue());
			// El texto lo arma TablaDeReporte.textoDeRevision, el mismo que usa el CSV.
			assertEquals("Sí: Tanda A · Espécimen 2", hoja.getRow(7).getCell(3).getStringCellValue());
			assertEquals("Aviso", hoja.getRow(8).getCell(2).getStringCellValue());
			assertEquals(TablaDeReporte.TEXTO_AVISO, hoja.getRow(8).getCell(3).getStringCellValue());
			// getLastRowNum fija dónde TERMINA la hoja: así se comprueba que no sobra ninguna fila.
			assertEquals(8, hoja.getLastRowNum());
		}
	}

	@Test
	void un_texto_que_parece_formula_se_guarda_como_texto() throws IOException {
		// Es la contraparte de la inyección de fórmulas que explica ReporteCsv. En XLSX no hace falta el
		// apóstrofo, porque la celda lleva su tipo marcado; esta prueba comprueba justamente eso.
		// Si alguien usara setCellFormula por error, el tipo sería FORMULA y la prueba lo detendría.
		try (XSSFWorkbook libro = abrir(ReporteCsvTest.tabla("=SUM(A1:A3)", false, List.of()))) {
			assertEquals(CellType.STRING, hoja(libro).getRow(1).getCell(0).getCellType());
			// Y el texto se conserva TAL CUAL, sin apóstrofo delante: en XLSX no hace falta.
			assertEquals("=SUM(A1:A3)", hoja(libro).getRow(1).getCell(0).getStringCellValue());
		}
	}

	@Test
	void sin_modelos_la_hoja_termina_en_la_revision() throws IOException {
		// Fuera del modo desarrollador no hay filas de modelo: la hoja acaba una fila antes.
		try (XSSFWorkbook libro = abrir(ReporteCsvTest.tabla("Control", false, List.of()))) {
			assertEquals(7, hoja(libro).getLastRowNum());
		}
	}

	@Test
	void en_modo_desarrollador_los_modelos_van_despues_de_la_revision_y_del_aviso() throws IOException {
		// Se parte de la tabla compartida y se reconstruye con la lista de modelos: así solo cambia eso.
		TablaDeReporte base = ReporteCsvTest.tabla("Control", true, List.of());
		TablaDeReporte tabla = new TablaDeReporte(base.grupo(), base.filas(), base.estadisticos(), true, base.revisados(),
				List.of("Tanda A: fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5", "Tanda B: sin registro"));
		try (XSSFWorkbook libro = abrir(tabla)) {
			Sheet hoja = hoja(libro);
			// El ORDEN del final: revisión, aviso y después una fila por modelo.
			assertEquals("Aviso", hoja.getRow(8).getCell(2).getStringCellValue());
			assertEquals("Modelo", hoja.getRow(9).getCell(2).getStringCellValue());
			assertEquals("Tanda A: fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5", hoja.getRow(9).getCell(3).getStringCellValue());
			// Una fila por análisis, incluido el que no tiene registro del modelo.
			assertEquals("Modelo", hoja.getRow(10).getCell(2).getStringCellValue());
			assertEquals("Tanda B: sin registro", hoja.getRow(10).getCell(3).getStringCellValue());
			assertEquals(10, hoja.getLastRowNum());
		}
	}

}
