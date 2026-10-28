package com.ipn.mx.fst.service;

import com.ipn.mx.fst.service.TablaDeReporte.Estadistico;
import com.ipn.mx.fst.service.TablaDeReporte.FilaDeReporte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// El mismo contenido que ReporteCsv, en una hoja «Resultados»: encabezado en negrita, los segundos y los estadísticos como
// números, las filas de estadísticos en gris y el ancho de las columnas ajustado. Solo valores, sin fórmulas: nada que viene
// de la base se interpreta como una.
public final class ReporteXlsx {

	private ReporteXlsx() {
	}

	public static byte[] armar(TablaDeReporte tabla) throws IOException {
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			Sheet hoja = libro.createSheet("Resultados");

			Font negrita = libro.createFont();
			negrita.setBold(true);
			CellStyle encabezado = libro.createCellStyle();
			encabezado.setFont(negrita);
			encabezado.setBorderBottom(BorderStyle.THIN);

			CellStyle gris = libro.createCellStyle();
			gris.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			gris.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			CellStyle grisConDecimal = libro.createCellStyle();
			grisConDecimal.cloneStyleFrom(gris);
			grisConDecimal.setDataFormat(libro.createDataFormat().getFormat("0.0"));

			int numero = 0;
			Row filaEncabezado = hoja.createRow(numero++);
			for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
				filaEncabezado.createCell(i).setCellValue(TablaDeReporte.ENCABEZADO.get(i));
				filaEncabezado.getCell(i).setCellStyle(encabezado);
			}

			for (FilaDeReporte fila : tabla.filas()) {
				Row renglon = hoja.createRow(numero++);
				renglon.createCell(0).setCellValue(tabla.grupo());
				renglon.createCell(1).setCellValue(fila.tanda());
				renglon.createCell(2).setCellValue(fila.especimen());
				renglon.createCell(3).setCellValue(fila.tiempo());
				for (int i = 0; i < fila.segundos().size(); i++) {
					renglon.createCell(4 + i).setCellValue(fila.segundos().get(i));
				}
			}

			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_MEDIA, tabla.estadisticos(), Estadistico::media, gris, grisConDecimal);
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_DE, tabla.estadisticos(), Estadistico::de, gris, grisConDecimal);
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_VARIANZA, tabla.estadisticos(), Estadistico::varianza, gris, grisConDecimal);

			numero = textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_REVISION, tabla.textoDeRevision());
			if (tabla.nivelMixto()) {
				textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_AVISO, TablaDeReporte.TEXTO_AVISO);
			}

			// Solo las columnas de la tabla: el texto largo de la revisión no debe ensanchar la última.
			for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
				hoja.autoSizeColumn(i);
			}
			libro.write(salida);
			return salida.toByteArray();
		}
	}

	private static int estadistico(Sheet hoja, int numero, String etiqueta, List<Estadistico> estadisticos,
			ToDoubleFunction<Estadistico> valor, CellStyle gris, CellStyle grisConDecimal) {
		Row renglon = hoja.createRow(numero);
		for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
			renglon.createCell(i).setCellStyle(gris);
		}
		renglon.getCell(2).setCellValue(etiqueta);
		for (int i = 0; i < estadisticos.size(); i++) {
			renglon.getCell(4 + i).setCellValue(valor.applyAsDouble(estadisticos.get(i)));
			renglon.getCell(4 + i).setCellStyle(grisConDecimal);
		}
		return numero + 1;
	}

	private static int textoFinal(Sheet hoja, int numero, String etiqueta, String texto) {
		Row renglon = hoja.createRow(numero);
		renglon.createCell(2).setCellValue(etiqueta);
		renglon.createCell(3).setCellValue(texto);
		return numero + 1;
	}

}
