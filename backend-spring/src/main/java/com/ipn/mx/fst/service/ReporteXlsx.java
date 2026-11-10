// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Los dos records anidados de TablaDeReporte que se usan aquí.
import com.ipn.mx.fst.service.TablaDeReporte.Estadistico;
import com.ipn.mx.fst.service.TablaDeReporte.FilaDeReporte;
// ByteArrayOutputStream: un "archivo en memoria" donde POI escribe el libro.
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
// ToDoubleFunction: permite pasar "qué campo leer" en vez de repetir el método tres veces.
import java.util.function.ToDoubleFunction;
// Las clases de Apache POI, la biblioteca que escribe archivos de Excel:
// BorderStyle (bordes), CellStyle (el formato de una celda), FillPatternType (relleno),
// Font (tipografía), IndexedColors (la paleta clásica), Row (fila), Sheet (hoja).
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
// XSSFWorkbook: el libro en formato .xlsx (el moderno). El antiguo .xls sería HSSFWorkbook.
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// El mismo contenido que ReporteCsv, en una hoja «Resultados»: encabezado en negrita, los segundos y los estadísticos como
// números, las filas de estadísticos en gris y el ancho de las columnas ajustado. Solo valores, sin fórmulas: nada que viene
// de la base se interpreta como una.
//
// "El mismo contenido" es literal: los dos reciben la misma TablaDeReporte, con los mismos números y los
// mismos textos. Solo cambia cómo se escriben.
// La última frase es la contraparte del problema de inyección de fórmulas que explica ReporteCsv. Aquí no
// hace falta el apóstrofo: setCellValue(String) guarda la celda COMO TEXTO en el archivo, con su tipo
// marcado, así que Excel nunca la evalúa. El CSV no tiene tipos y por eso necesita el truco.
public final class ReporteXlsx {

	// Impide instanciarla: es una caja de métodos estáticos.
	private ReporteXlsx() {
	}

	public static byte[] armar(TablaDeReporte tabla) throws IOException {
		// try-con-recursos con dos recursos: los dos se cierran solos al salir, incluso si algo falla.
		// Cerrar el libro libera la memoria que POI reserva; es obligatorio.
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			Sheet hoja = libro.createSheet("Resultados");

			// --- Los estilos se crean UNA vez y se reutilizan en todas las celdas ---
			// No es cosmético: POI tiene un límite de unos 64 000 estilos por libro, y crear uno por celda
			// lo agotaría en una tabla grande además de inflar el archivo.
			Font negrita = libro.createFont();
			negrita.setBold(true);
			CellStyle encabezado = libro.createCellStyle();
			encabezado.setFont(negrita);
			// Una línea fina debajo del encabezado, para separarlo de los datos.
			encabezado.setBorderBottom(BorderStyle.THIN);

			// El gris de las filas de resumen. Hacen falta las dos líneas: el color solo se ve si además
			// se fija el patrón de relleno a SOLID_FOREGROUND.
			CellStyle gris = libro.createCellStyle();
			gris.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			gris.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			// El mismo gris, pero con formato de un decimal. cloneStyleFrom copia el anterior para no
			// repetir las dos líneas de color.
			CellStyle grisConDecimal = libro.createCellStyle();
			grisConDecimal.cloneStyleFrom(gris);
			// "0.0" es el formato numérico de Excel: siempre un decimal. Así, un 12 se ve como "12.0" y
			// toda la columna queda alineada. Va DESPUÉS de cloneStyleFrom: al revés, la copia del estilo
			// gris (que no tiene formato) borraría este.
			grisConDecimal.setDataFormat(libro.createDataFormat().getFormat("0.0"));

			// El contador de filas. numero++ usa el valor y luego lo incrementa, así que cada createRow
			// recibe la fila siguiente sin tener que sumar a mano.
			int numero = 0;
			Row filaEncabezado = hoja.createRow(numero++);
			// Los mismos textos que el CSV, de TablaDeReporte.ENCABEZADO.
			for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
				filaEncabezado.createCell(i).setCellValue(TablaDeReporte.ENCABEZADO.get(i));
				filaEncabezado.getCell(i).setCellStyle(encabezado);
			}

			// Una fila por espécimen y minuto.
			for (FilaDeReporte fila : tabla.filas()) {
				Row renglon = hoja.createRow(numero++);
				// Las cuatro primeras columnas, como TEXTO.
				renglon.createCell(0).setCellValue(tabla.grupo());
				renglon.createCell(1).setCellValue(fila.tanda());
				renglon.createCell(2).setCellValue(fila.especimen());
				renglon.createCell(3).setCellValue(fila.tiempo());
				// Los segundos, como NÚMEROS: setCellValue recibe un long y Excel los podrá sumar.
				// El 4 + i los coloca a partir de la quinta columna, detrás de las cuatro de texto.
				for (int i = 0; i < fila.segundos().size(); i++) {
					renglon.createCell(4 + i).setCellValue(fila.segundos().get(i));
				}
			}

			// Las tres filas de resumen. Cada llamada devuelve el número de la fila siguiente, de ahí la
			// reasignación. Estadistico::media es una referencia a método: dice qué campo leer.
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_MEDIA, tabla.estadisticos(), Estadistico::media, gris, grisConDecimal);
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_DE, tabla.estadisticos(), Estadistico::de, gris, grisConDecimal);
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_VARIANZA, tabla.estadisticos(), Estadistico::varianza, gris, grisConDecimal);

			// Las filas de texto del final, iguales que en el CSV.
			numero = textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_REVISION, tabla.textoDeRevision());
			if (tabla.nivelMixto()) {
				numero = textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_AVISO, TablaDeReporte.TEXTO_AVISO);
			}
			// Modo desarrollador: con qué modelo se hizo cada análisis (la lista viene vacía fuera de él).
			for (String modelo : tabla.modelos()) {
				numero = textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_MODELO, modelo);
			}

			// Solo las columnas de la tabla: el texto largo de la revisión no debe ensanchar la última.
			//
			// autoSizeColumn ajusta la columna a su contenido más ancho. El texto de la revisión puede
			// medir cientos de caracteres y, si se ajustara su columna, la hoja saldría deformada.
			// Al limitar el bucle al número de columnas del encabezado, ese texto se desborda visualmente
			// sobre las celdas vacías de al lado, que es justo lo que se quiere.
			for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
				hoja.autoSizeColumn(i);
			}
			// Se vuelca el libro al "archivo en memoria" y se devuelven sus bytes.
			libro.write(salida);
			return salida.toByteArray();
		}
	}

	// Una fila de estadístico: toda gris, la etiqueta en la columna Espécimen y un número por conducta.
	// Devuelve el número de la fila siguiente, para que quien llama lleve la cuenta.
	private static int estadistico(Sheet hoja, int numero, String etiqueta, List<Estadistico> estadisticos,
			ToDoubleFunction<Estadistico> valor, CellStyle gris, CellStyle grisConDecimal) {
		Row renglon = hoja.createRow(numero);
		// Primero se crean TODAS las celdas con el gris, incluidas las vacías: en Excel, una celda que no
		// existe no se puede pintar, así que el fondo gris se cortaría a media fila.
		for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
			renglon.createCell(i).setCellStyle(gris);
		}
		// getCell y no createCell: la celda ya existe del bucle de arriba.
		// La 2 es la columna "Espécimen", donde cae la etiqueta, igual que en el CSV.
		renglon.getCell(2).setCellValue(etiqueta);
		for (int i = 0; i < estadisticos.size(); i++) {
			renglon.getCell(4 + i).setCellValue(valor.applyAsDouble(estadisticos.get(i)));
			// Se pisa el gris liso con el gris que además formatea a un decimal.
			renglon.getCell(4 + i).setCellStyle(grisConDecimal);
		}
		return numero + 1;
	}

	// Una fila de texto del final: la etiqueta en la columna Espécimen y su texto en la de Tiempo.
	// Sin gris y sin crear las celdas vacías, porque no hay fondo que pintar.
	private static int textoFinal(Sheet hoja, int numero, String etiqueta, String texto) {
		Row renglon = hoja.createRow(numero);
		renglon.createCell(2).setCellValue(etiqueta);
		renglon.createCell(3).setCellValue(texto);
		return numero + 1;
	}

}
