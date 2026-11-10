// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Las filas de la hoja de segundos, que produce SegundosDeResultados.
import com.ipn.mx.fst.service.SegundosDeResultados.FilaSegundo;
// Los dos records anidados de TablaDeReporte que se usan aquí.
import com.ipn.mx.fst.service.TablaDeReporte.Estadistico;
import com.ipn.mx.fst.service.TablaDeReporte.FilaDeReporte;
// ByteArrayOutputStream: un "archivo en memoria" donde POI escribe el libro.
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
// ToDoubleFunction: permite pasar "qué campo leer".
import java.util.function.ToDoubleFunction;
// Las clases de Apache POI para escribir el .xlsx.
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// El Excel de todo el experimento, con dos hojas. «Resultados»: lo mismo que el XLSX de cada grupo (una tabla por grupo, con
// sus medias, desviaciones, varianzas y revisión), uno debajo de otro. «Segundo a segundo»: una fila por espécimen y segundo,
// con la conducta y una columna por conducta (1 o 0) y el origen crudo. Solo escribe lo que le dan; no calcula nada.
//
// Es el hermano mayor de ReporteXlsx: aquel exporta UN grupo, este el experimento entero y además añade
// la hoja de detalle. Las reglas (qué grupos entran, cuándo fallar) están en ReporteExcelExperimento;
// aquí solo se dibuja.
// La hoja «Segundo a segundo» con sus columnas de 1 y 0 tiene un destinatario claro: quien va a meter los
// datos en un programa de estadística. Ese formato, una columna por categoría con 1 o 0, es el que
// esperan SPSS, R o Prism para hacer sus propios análisis.
public final class ExcelDelExperimento {

	// Las cinco columnas de conducta de la hoja de segundos, en el orden que pidió el equipo.
	//
	// Ojo: NO es el mismo orden que TablaDeReporte.CONDUCTAS (allí escalamiento va antes que inmovilidad).
	// Son dos hojas distintas con dos ordenaciones pedidas por separado, y por eso esta lista existe
	// aparte en vez de reutilizar aquella. Incluye SIN_VER al final, sacado de TablaDeReporte para que el
	// texto sea el mismo en todas partes.
	static final List<String> CONDUCTAS_SEGUNDO = List.of("nado activo", "inmovilidad", "escalamiento", "conducta activa", TablaDeReporte.SIN_VER);
	// Los encabezados que ve el usuario. Los cinco del medio corresponden, en el mismo orden, a
	// CONDUCTAS_SEGUNDO: son las columnas de 1 y 0.
	static final List<String> ENCABEZADO_SEGUNDO = List.of("Grupo", "Tanda", "Espécimen", "Tiempo (s)", "Conducta",
			"Nado activo", "Inmovilidad", "Escalamiento", "Conducta activa", "Sin ver", "Origen");

	// Impide instanciarla: es una caja de métodos estáticos.
	private ExcelDelExperimento() {
	}

	// tablas: una por grupo con análisis completado, en el orden de los grupos. segundos: todas las filas de la hoja de
	// segundos, ya ordenadas.
	//
	// "Ya ordenadas" es importante: este método no ordena nada, confía en el ORDER BY de la consulta de
	// SegundosDeResultados. Ordenar aquí sería repetir un trabajo que la base ya hizo mejor.
	public static byte[] armar(List<TablaDeReporte> tablas, List<FilaSegundo> segundos) throws IOException {
		// try-con-recursos: los dos se cierran solos al salir, incluso si algo falla.
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			// Los estilos se crean una vez para todo el libro (ver la clase Estilos al final).
			Estilos estilos = new Estilos(libro);
			// El orden de las dos llamadas fija el orden de las pestañas en Excel.
			hojaResultados(libro, estilos, tablas);
			hojaSegundos(libro, estilos, segundos);
			libro.write(salida);
			return salida.toByteArray();
		}
	}

	// La primera hoja: las tablas de todos los grupos, una debajo de otra.
	private static void hojaResultados(XSSFWorkbook libro, Estilos estilos, List<TablaDeReporte> tablas) {
		Sheet hoja = libro.createSheet("Resultados");
		// El contador de filas, compartido por todas las tablas: cada una sigue donde terminó la anterior.
		int numero = 0;
		// UN solo encabezado arriba, no uno por grupo: la columna "Grupo" ya dice de cuál es cada fila.
		Row encabezado = hoja.createRow(numero++);
		for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
			encabezado.createCell(i).setCellValue(TablaDeReporte.ENCABEZADO.get(i));
			encabezado.getCell(i).setCellStyle(estilos.encabezado);
		}
		for (TablaDeReporte tabla : tablas) {
			// Las filas de datos del grupo: una por espécimen y minuto.
			for (FilaDeReporte fila : tabla.filas()) {
				Row renglon = hoja.createRow(numero++);
				// Las cuatro primeras, texto; las cinco siguientes, números (ver ReporteXlsx).
				renglon.createCell(0).setCellValue(tabla.grupo());
				renglon.createCell(1).setCellValue(fila.tanda());
				renglon.createCell(2).setCellValue(fila.especimen());
				renglon.createCell(3).setCellValue(fila.tiempo());
				for (int i = 0; i < fila.segundos().size(); i++) {
					renglon.createCell(4 + i).setCellValue(fila.segundos().get(i));
				}
			}
			// Las mismas filas de resumen que el XLSX de un grupo, con las mismas etiquetas.
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_MEDIA, tabla.estadisticos(), Estadistico::media, estilos);
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_DE, tabla.estadisticos(), Estadistico::de, estilos);
			numero = estadistico(hoja, numero, TablaDeReporte.ETIQUETA_VARIANZA, tabla.estadisticos(), Estadistico::varianza, estilos);
			numero = textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_REVISION, tabla.textoDeRevision());
			if (tabla.nivelMixto()) {
				numero = textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_AVISO, TablaDeReporte.TEXTO_AVISO);
			}
			// Modo desarrollador: la lista viene vacía fuera de él, así que el bucle no da vueltas.
			for (String modelo : tabla.modelos()) {
				numero = textoFinal(hoja, numero, TablaDeReporte.ETIQUETA_MODELO, modelo);
			}
			numero++; // una fila en blanco entre grupos
			// (incrementar el contador sin crear la fila deja ese renglón vacío: en Excel, una fila que
			// no se crea simplemente no existe y se ve en blanco)
		}
		// Se ajustan solo las columnas de la tabla, por lo mismo que en ReporteXlsx: el texto largo de la
		// revisión no debe ensanchar su columna.
		for (int i = 0; i < TablaDeReporte.ENCABEZADO.size(); i++) {
			hoja.autoSizeColumn(i);
		}
	}

	// La segunda hoja: el detalle segundo a segundo, en el formato que esperan los programas de estadística.
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
			// Aquí sí se traduce el null a texto: en una hoja de cálculo, una celda vacía se confunde con
			// un dato que falta. "sin ver" afirma que el segundo se midió y no se veía al espécimen.
			String conducta = f.conducta() == null ? TablaDeReporte.SIN_VER : f.conducta();
			renglon.createCell(0).setCellValue(f.grupo());
			renglon.createCell(1).setCellValue(f.letra());
			// El número de cilindro es lo único que identifica al espécimen (ver la entidad Especimen).
			renglon.createCell(2).setCellValue("Espécimen " + f.cilindro());
			// El segundo, como número: así se puede filtrar y ordenar en Excel.
			renglon.createCell(3).setCellValue(f.s());
			renglon.createCell(4).setCellValue(conducta);
			// Las cinco columnas de 1 y 0: exactamente una vale 1 en cada fila, la que coincide con la
			// conducta. Es el formato que esperan SPSS, R o Prism para sus propios análisis.
			for (int i = 0; i < CONDUCTAS_SEGUNDO.size(); i++) {
				renglon.createCell(5 + i).setCellValue(CONDUCTAS_SEGUNDO.get(i).equals(conducta) ? 1 : 0);
			}
			// La columna 10 es "Origen": de dónde salió el dato (maquina, confirmada, corregida, dudosa).
			// Es la que permite separar lo medido automáticamente de lo revisado a mano.
			// El null se escribe como vacío, aunque la columna origen no admite nulos en la base: es
			// prudencia, para no tropezar si alguna vez llegara uno.
			renglon.createCell(10).setCellValue(f.origen() == null ? "" : f.origen());
		}
		for (int i = 0; i < ENCABEZADO_SEGUNDO.size(); i++) {
			hoja.autoSizeColumn(i);
		}
	}

	// Una fila de estadístico, igual que en ReporteXlsx: toda gris, la etiqueta en la columna Espécimen y
	// un número con un decimal por conducta. Devuelve el número de la fila siguiente.
	private static int estadistico(Sheet hoja, int numero, String etiqueta, List<Estadistico> estadisticos,
			ToDoubleFunction<Estadistico> valor, Estilos estilos) {
		Row renglon = hoja.createRow(numero);
		// Primero todas las celdas con el gris, incluidas las vacías: una celda que no existe no se puede
		// pintar y el fondo se cortaría a media fila.
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

	// Una fila de texto del final: la etiqueta en la columna Espécimen y su texto en la de Tiempo.
	private static int textoFinal(Sheet hoja, int numero, String etiqueta, String texto) {
		Row renglon = hoja.createRow(numero);
		renglon.createCell(2).setCellValue(etiqueta);
		renglon.createCell(3).setCellValue(texto);
		return numero + 1;
	}

	// Estilos de la hoja: encabezado en negrita con borde, y las filas de estadísticos en gris con un decimal.
	//
	// Agrupar los tres estilos en una clase, en vez de pasarlos sueltos, mantiene corta la firma de los
	// métodos de arriba. Se crean UNA vez por libro: POI tiene un límite de unos 64 000 estilos, y crear
	// uno por celda lo agotaría además de inflar el archivo.
	// private static final: solo la usa esta clase, no necesita instancia de fuera y nadie la hereda.
	private static final class Estilos {
		// Los tres campos son final: se fijan en el constructor y no cambian.
		final CellStyle encabezado;
		final CellStyle gris;
		final CellStyle grisConDecimal;

		Estilos(XSSFWorkbook libro) {
			Font negrita = libro.createFont();
			negrita.setBold(true);
			encabezado = libro.createCellStyle();
			encabezado.setFont(negrita);
			// Una línea fina para separar el encabezado de los datos.
			encabezado.setBorderBottom(BorderStyle.THIN);

			gris = libro.createCellStyle();
			// Aquí los nombres van con su ruta completa en vez de importarse: son dos usos sueltos y se
			// evitan dos imports más. Es cuestión de gusto, no de comportamiento.
			gris.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
			// El color solo se ve si además se fija el patrón de relleno.
			gris.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
			grisConDecimal = libro.createCellStyle();
			// El orden importa: primero se copia el gris y DESPUÉS se añade el formato. Al revés, la
			// copia borraría el formato recién puesto.
			grisConDecimal.cloneStyleFrom(gris);
			// "0.0" es el formato numérico de Excel: siempre un decimal.
			grisConDecimal.setDataFormat(libro.createDataFormat().getFormat("0.0"));
		}
	}
}
