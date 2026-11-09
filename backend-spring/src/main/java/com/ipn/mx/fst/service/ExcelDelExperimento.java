package com.ipn.mx.fst.service;

import com.ipn.mx.fst.service.SegundosDeResultados.FilaSegundo;
import com.ipn.mx.fst.service.TablaDeReporte.Estadistico;
import com.ipn.mx.fst.service.TablaDeReporte.FilaDeReporte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// El Excel de todo el experimento, con dos hojas. «Resultados»: lo mismo que el XLSX de cada grupo (una tabla por grupo, con
// sus medias, desviaciones, varianzas y revisión), uno debajo de otro. «Segundo a segundo»: una fila por espécimen y segundo,
// con la conducta y una columna por conducta (1 o 0) y el origen crudo. Solo escribe lo que le dan; no calcula nada.
public final class ExcelDelExperimento {

	// Las cinco columnas de conducta de la hoja de segundos, en el orden que pidió el equipo.
	static final List<String> CONDUCTAS_SEGUNDO = List.of("nado activo", "inmovilidad", "escalamiento", "conducta activa", TablaDeReporte.SIN_VER);
	static final List<String> ENCABEZADO_SEGUNDO = List.of("Grupo", "Tanda", "Espécimen", "Tiempo (s)", "Conducta",
			"Nado activo", "Inmovilidad", "Escalamiento", "Conducta activa", "Sin ver", "Origen");

	private ExcelDelExperimento() {
	}

	// tablas: una por grupo con análisis completado, en el orden de los grupos. segundos: todas las filas de la hoja de
	// segundos, ya ordenadas.
	public static byte[] armar(List<TablaDeReporte> tablas, List<FilaSegundo> segundos) throws IOException {
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			Estilos estilos = new Estilos(libro);
			hojaResultados(libro, estilos, tablas);
			hojaSegundos(libro, estilos, segundos);
			libro.write(salida);
			return salida.toByteArray();
		}
	}

	private static void hojaResultados(XSSFWorkbook libro, Estilos estilos, List<TablaDeReporte> tablas) {
		Sheet hoja = libro.createSheet("Resultados");
		int numero = 0;
		Row encabezado = hoja.createRow(numero++);
		for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
			encabezado.createCell(i).setCellValue(TablaDeReporte.ENCABEZADO.get(i));
			encabezado.getCell(i).setCellStyle(estilos.encabezado);
		}
		for (TablaDeReporte tabla : tablas) {
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
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_MEDIA, tabla.estadisticos(), Estadistico::media, estilos);
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_DE, tabla.estadisticos(), Estadistico::de, estilos);
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_VARIANZA, tabla.estadisticos(), Estadistico::varianza, estilos);
			numero = textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_REVISION, tabla.textoDeRevision());
			if (tabla.nivelMixto()) {
				numero = textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_AVISO, TablaDeReporte.TEXTO_AVISO);
			}
			for (String modelo : tabla.modelos()) {
				numero = textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_MODELO, modelo);
			}
			numero++; // una fila en blanco entre grupos
		}
		for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
			hoja.autoSizeColumn(i);
		}
	}

	private static void hojaSegundos(XSSFWorkbook libro, Estilos estilos, List<FilaSegundo> segundos) {
		Sheet hoja = libro.createSheet("Segundo a segundo");
		int numero = 0;
		Row encabezado = hoja.createRow(numero++);
		for (int i = 0; i < ENCABEZADO_SEGUNDO.size(); i++) {
			encabezado.createCell(i).setCellValue(ENCABEZADO_SEGUNDO.get(i));
			encabezado.getCell(i).setCellStyle(estilos.encabezado);
		}
		for (FilaSegundo f : segundos) {
			Row renglon = hoja.createRow(numero++);
			String conducta = f.conducta() == null ? TablaDeReporte.SIN_VER : f.conducta();
			renglon.createCell(0).setCellValue(f.grupo());
			renglon.createCell(1).setCellValue(f.letra());
			renglon.createCell(2).setCellValue("Espécimen " + f.cilindro());
			renglon.createCell(3).setCellValue(f.s());
			renglon.createCell(4).setCellValue(conducta);
			for (int i = 0; i < CONDUCTAS_SEGUNDO.size(); i++) {
				renglon.createCell(5 + i).setCellValue(CONDUCTAS_SEGUNDO.get(i).equals(conducta) ? 1 : 0);
			}
			renglon.createCell(10).setCellValue(f.origen() == null ? "" : f.origen());
		}
		for (int i = 0; i < ENCABEZADO_SEGUNDO.size(); i++) {
			hoja.autoSizeColumn(i);
		}
	}

	private static int estadistico(Sheet hoja, int numero, String etiqueta, List<Estadistico> estadisticos,
			ToDoubleFunction<Estadistico> valor, Estilos estilos) {
		Row renglon = hoja.createRow(numero);
		for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
			renglon.createCell(i).setCellStyle(estilos.gris);
		}
		renglon.getCell(2).setCellValue(etiqueta);
		for (int i = 0; i < estadisticos.size(); i++) {
			renglon.getCell(4 + i).setCellValue(valor.applyAsDouble(estadisticos.get(i)));
			renglon.getCell(4 + i).setCellStyle(estilos.grisConDecimal);
		}
		return numero + 1;
	}

	private static int textoFinal(Sheet hoja, int numero, String etiqueta, String texto) {
		Row renglon = hoja.createRow(numero);
		renglon.createCell(2).setCellValue(etiqueta);
		renglon.createCell(3).setCellValue(texto);
		return numero + 1;
	}

	// Estilos de la hoja: encabezado en negrita con borde, y las filas de estadísticos en gris con un decimal.
	private static final class Estilos {
		final CellStyle encabezado;
		final CellStyle gris;
		final CellStyle grisConDecimal;

		Estilos(XSSFWorkbook libro) {
			Font negrita = libro.createFont();
			negrita.setBold(true);
			encabezado = libro.createCellStyle();
			encabezado.setFont(negrita);
			encabezado.setBorderBottom(BorderStyle.THIN);

			gris = libro.createCellStyle();
			gris.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
			gris.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
			grisConDecimal = libro.createCellStyle();
			grisConDecimal.cloneStyleFrom(gris);
			grisConDecimal.setDataFormat(libro.createDataFormat().getFormat("0.0"));
		}
	}
}
