// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

// Las clases de iText: color, el documento, su excepción, la tipografía y su fábrica, párrafos, frases,
// el rectángulo (aquí solo por su constante NO_BORDER), la fuente de bajo nivel, el lienzo de dibujo
// directo, el marcador, la celda, la tabla y el escritor.
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfOutline;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

// La sección «Gráficas» del PDF: una por cada gráfica que aplica, en el orden fijo de GRAFICAS. Cada una lleva su título,
// su leyenda, el dibujo, sus notas (las de la gráfica, las de cada grupo, ± DE y origen) y su marcador hijo. Una gráfica que
// no aplica se omite junto con su marcador; si ninguna aplica, no hay sección ni marcador.
//
// Es el COORDINADOR de las gráficas: decide cuáles salen, cuánto espacio ocupan, si caben en la página y
// dónde se dibujan. Las gráficas solo saben dibujarse en un lienzo abstracto (ver GraficaPdf).
// El problema de fondo que resuelve: iText coloca párrafos y tablas solo, pero una gráfica se dibuja con
// coordenadas absolutas sobre la página. Hay que reservarle el hueco Y saber en qué coordenadas quedó.
public final class SeccionGraficasPdf {

	// Las seis gráficas, en el orden de la pantalla. G3 a G6 todavía no aplican (ver cada clase).
	// Una lista inmutable con una instancia de cada una: no tienen estado, así que una basta para todo.
	// Registrar una gráfica nueva es añadirla aquí, y nada más.
	public static final List<GraficaPdf> GRAFICAS = List.of(
			new G1BarrasGruposPdf(), new G2CurvaPorMinutoPdf(), new G3BarrasPorMinutoPdf(),
			new G4ApiladaPdf(), new G5VariabilidadPdf(), new G6MapaPdf());

	// Las medidas del maquetado, en puntos (1/72 de pulgada). En constantes porque se usan dos veces cada
	// una: al CALCULAR si cabe y al DIBUJAR. Si no coincidieran, el cálculo mentiría.
	private static final float ALTO_FILA_LEYENDA = 12f;
	private static final float ALTO_NOTA = 9.5f;
	private static final float ALTO_TITULO = 44f;
	private static final float TAMANO_LEYENDA = 7.5f;
	// El aire que se deja debajo del subtítulo: sin él la leyenda empieza pegada a la letra del subtítulo y se encima.
	private static final float ESPACIO_SUBTITULO = 8f;

	private SeccionGraficasPdf() {
	}

	// Escribe la sección y sus marcadores. Devuelve false si ninguna gráfica aplica (no se escribe nada).
	public static boolean escribir(Document documento, PdfWriter writer, Map<String, Object> datos, MarcadoresPdf marcadores)
			throws DocumentException, IOException {
		// Primero se filtra: solo las que tienen algo que dibujar con estos datos.
		List<GraficaPdf> aplican = GRAFICAS.stream().filter(g -> g.aplica(datos)).toList();
		// Sin ninguna, ni siquiera se escribe el título de la sección: un apartado «Gráficas» vacío sería
		// peor que no tenerlo.
		if (aplican.isEmpty()) {
			return false;
		}
		// BaseFont es la tipografía de bajo nivel, la que entiende el lienzo de dibujo directo (las
		// Font normales solo valen para párrafos).
		// NOT_EMBEDDED: no se incrusta la tipografía en el PDF, porque Helvetica es una de las catorce que
		// todo lector trae de serie. Así el archivo pesa menos.
		BaseFont fuente = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
		BaseFont negrita = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

		// El marcador padre, del que colgarán los de cada gráfica.
		PdfOutline seccion = marcadores.seccion("Gráficas", MarcadoresPdf.GRAFICAS);
		documento.add(MarcadoresPdf.encabezado("Gráficas", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12), MarcadoresPdf.GRAFICAS));

		for (GraficaPdf grafica : aplican) {
			// El ancho útil: la página menos los dos márgenes.
			float ancho = documento.getPageSize().getWidth() - documento.leftMargin() - documento.rightMargin();
			// La leyenda ocupa tantas filas como quepan sus muestras; el +4 es un respiro debajo.
			float alturaLeyenda = filasDeLeyenda(fuente, grafica.leyenda(datos), ancho) * ALTO_FILA_LEYENDA + 4;
			// La altura de TODO el bloque: título, leyenda, dibujo, notas y un margen.
			float alturaTotal = ALTO_TITULO + ESPACIO_SUBTITULO + alturaLeyenda + grafica.alto() + grafica.notas(datos).size() * ALTO_NOTA + 14;
			// Si el bloque entero no cabe en lo que queda de página, se salta a la siguiente. Sin esto,
			// una gráfica podría partirse entre dos páginas, con el título arriba y el dibujo abajo.
			// getVerticalPosition da la altura actual del cursor; en un PDF el eje Y crece hacia arriba,
			// así que restar es bajar, y bottomMargin es el suelo.
			if (writer.getVerticalPosition(false) - alturaTotal < documento.bottomMargin()) {
				documento.newPage();
			}

			// El marcador y el destino van en el título, así que el título y el dibujo quedan en la misma página.
			// Por eso el salto de página va ANTES: si se creara el destino y luego se saltara, el marcador
			// apuntaría a la página anterior, donde ya no está la gráfica.
			MarcadoresPdf.hijo(seccion, grafica.titulo(), "grafica-" + grafica.id());
			Paragraph titulo = MarcadoresPdf.encabezado(grafica.titulo(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), "grafica-" + grafica.id());
			titulo.setSpacingBefore(8);
			documento.add(titulo);
			// El subtítulo en cursiva gris, como las notas: se lee como una aclaración.
			Paragraph subtitulo = new Paragraph(grafica.subtitulo(), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, BaseColor.DARK_GRAY));
			subtitulo.setSpacingAfter(ESPACIO_SUBTITULO);
			documento.add(subtitulo);
			dibujarBloque(documento, writer, fuente, negrita, grafica, datos, alturaLeyenda);
		}
		return true;
	}

	// Leyenda, dibujo y notas de una gráfica. El dibujo va en un hueco de altura fija que empieza en la posición actual de
	// la página; las coordenadas se toman de ahí.
	//
	// Aquí está el truco central del archivo. iText no sabe "reservar espacio para un dibujo", así que se
	// añade una TABLA INVISIBLE de una celda con la altura exacta: eso empuja el contenido siguiente hacia
	// abajo, como si el dibujo ocupara su sitio. Y el dibujo se pinta aparte, con coordenadas absolutas,
	// justo encima de ese hueco.
	private static void dibujarBloque(Document documento, PdfWriter writer, BaseFont fuente, BaseFont negrita,
			GraficaPdf grafica, Map<String, Object> datos, float alturaLeyenda) throws DocumentException {
		float ancho = documento.getPageSize().getWidth() - documento.leftMargin() - documento.rightMargin();
		// La altura actual del cursor: de aquí para abajo irá el dibujo.
		float yInicio = writer.getVerticalPosition(false);
		float alturaDibujo = alturaLeyenda + grafica.alto();

		// La celda invisible: un espacio con letra de 2 puntos (el contenido no importa, solo que exista),
		// altura fija, sin borde y sin relleno.
		PdfPCell hueco = new PdfPCell(new Phrase(" ", FontFactory.getFont(FontFactory.HELVETICA, 2)));
		hueco.setFixedHeight(alturaDibujo);
		hueco.setBorder(Rectangle.NO_BORDER);
		hueco.setPadding(0);
		PdfPTable tabla = new PdfPTable(1);
		tabla.setWidthPercentage(100);
		// Sin espaciado: el hueco tiene que medir exactamente lo calculado, o el dibujo no cuadraría.
		tabla.setSpacingBefore(0);
		tabla.setSpacingAfter(0);
		tabla.addCell(hueco);

		// getDirectContent es el lienzo de dibujo directo: permite pintar líneas y formas con coordenadas
		// absolutas, por debajo del flujo normal de párrafos y tablas.
		PdfContentByte cb = writer.getDirectContent();
		float x0 = documento.leftMargin();
		// Dos dibujantes con orígenes distintos: uno para la leyenda, arriba, y otro para la gráfica,
		// justo debajo. Cada uno traduce las coordenadas locales de quien dibuja a las de la página.
		dibujarLeyenda(new DibujanteGraficas(cb, fuente, negrita, x0, yInicio, ancho, alturaLeyenda), fuente, grafica.leyenda(datos), ancho);
		// Aquí la gráfica dibuja lo suyo, sin saber dónde está.
		grafica.dibujar(new DibujanteGraficas(cb, fuente, negrita, x0, yInicio - alturaLeyenda, ancho, grafica.alto()), datos);
		// La tabla invisible se añade DESPUÉS de dibujar, para empujar hacia abajo lo que venga luego.
		documento.add(tabla);

		// Las notas, con viñeta, en gris y pequeñas.
		for (String nota : grafica.notas(datos)) {
			Paragraph p = new Paragraph("• " + nota, FontFactory.getFont(FontFactory.HELVETICA, TAMANO_LEYENDA, BaseColor.DARK_GRAY));
			p.setSpacingBefore(1);
			documento.add(p);
		}
		// Un párrafo vacío para separar esta gráfica de la siguiente.
		Paragraph separacion = new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 6));
		separacion.setSpacingAfter(4);
		documento.add(separacion);
	}

	// Cuántas filas ocupa la leyenda: los renglones se llenan y, si no caben, siguen en la fila de abajo.
	//
	// Hay que CALCULARLO antes de dibujar, para saber cuánto espacio reservar. Este método y
	// dibujarLeyenda recorren las muestras con la misma lógica: si no coincidieran, el dibujo se saldría
	// del hueco o dejaría un espacio de más.
	private static int filasDeLeyenda(BaseFont fuente, List<GraficaPdf.Muestra> muestras, float ancho) {
		if (muestras.isEmpty()) {
			return 0;
		}
		int filas = 1;
		float x = 0;
		for (GraficaPdf.Muestra m : muestras) {
			float w = anchoDeMuestra(fuente, m.texto());
			// El "x > 0" evita saltar de fila con la primera muestra aunque sea más ancha que la página:
			// ahí no hay nada que ganar y quedaría una fila vacía.
			if (x + w > ancho && x > 0) {
				filas++;
				x = 0;
			}
			x += w;
		}
		return filas;
	}

	// Lo que mide una muestra de la leyenda: el cuadrito (14), el texto y un hueco hasta la siguiente (18).
	// getWidthPoint mide el ancho real del texto con esa tipografía y ese tamaño, en vez de estimarlo por
	// el número de caracteres, que fallaría porque una "i" es mucho más estrecha que una "m".
	private static float anchoDeMuestra(BaseFont fuente, String texto) {
		return 14f + fuente.getWidthPoint(texto, TAMANO_LEYENDA) + 18f;
	}

	// Dibuja la leyenda con la MISMA lógica de salto de fila que filasDeLeyenda.
	private static void dibujarLeyenda(DibujanteGraficas d, BaseFont fuente, List<GraficaPdf.Muestra> muestras, float ancho) {
		float x = 0;
		float y = 2;
		for (GraficaPdf.Muestra m : muestras) {
			float w = anchoDeMuestra(fuente, m.texto());
			if (x + w > ancho && x > 0) {
				x = 0;
				y += ALTO_FILA_LEYENDA;
			}
			// El cuadrito de 8x8 con el color del estilo y un borde gris.
			d.rectangulo(x, y, 8, 8, m.estilo().color(), EstiloPdf.GRIS_MEDIO);
			// Y encima su patrón: es lo que distingue dos series del mismo tono al imprimir en blanco y
			// negro (ver EstiloPdf).
			d.patron(x, y, 8, 8, m.estilo().patron());
			// El texto a la derecha del cuadrito; el 6.5 lo alinea con su centro.
			d.texto(m.texto(), x + 11, y + 6.5f, TAMANO_LEYENDA, DibujanteGraficas.IZQUIERDA, EstiloPdf.NEGRO, false);
			x += w;
		}
	}
}
