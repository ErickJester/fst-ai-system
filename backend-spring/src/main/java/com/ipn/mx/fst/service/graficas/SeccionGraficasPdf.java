package com.ipn.mx.fst.service.graficas;

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
public final class SeccionGraficasPdf {

	// Las seis gráficas, en el orden de la pantalla. G3 a G6 todavía no aplican (ver cada clase).
	public static final List<GraficaPdf> GRAFICAS = List.of(
			new G1BarrasGruposPdf(), new G2CurvaPorMinutoPdf(), new G3BarrasPorMinutoPdf(),
			new G4ApiladaPdf(), new G5VariabilidadPdf(), new G6MapaPdf());

	private static final float ALTO_FILA_LEYENDA = 12f;
	private static final float ALTO_NOTA = 9.5f;
	private static final float ALTO_TITULO = 44f;
	private static final float TAMANO_LEYENDA = 7.5f;

	private SeccionGraficasPdf() {
	}

	// Escribe la sección y sus marcadores. Devuelve false si ninguna gráfica aplica (no se escribe nada).
	public static boolean escribir(Document documento, PdfWriter writer, Map<String, Object> datos, MarcadoresPdf marcadores)
			throws DocumentException, IOException {
		List<GraficaPdf> aplican = GRAFICAS.stream().filter(g -> g.aplica(datos)).toList();
		if (aplican.isEmpty()) {
			return false;
		}
		BaseFont fuente = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
		BaseFont negrita = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

		PdfOutline seccion = marcadores.seccion("Gráficas", MarcadoresPdf.GRAFICAS);
		documento.add(MarcadoresPdf.encabezado("Gráficas", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12), MarcadoresPdf.GRAFICAS));

		for (GraficaPdf grafica : aplican) {
			float ancho = documento.getPageSize().getWidth() - documento.leftMargin() - documento.rightMargin();
			float alturaLeyenda = filasDeLeyenda(fuente, grafica.leyenda(datos), ancho) * ALTO_FILA_LEYENDA + 4;
			float alturaTotal = ALTO_TITULO + alturaLeyenda + grafica.alto() + grafica.notas(datos).size() * ALTO_NOTA + 14;
			if (writer.getVerticalPosition(false) - alturaTotal < documento.bottomMargin()) {
				documento.newPage();
			}

			// El marcador y el destino van en el título, así que el título y el dibujo quedan en la misma página.
			MarcadoresPdf.hijo(seccion, grafica.titulo(), "grafica-" + grafica.id());
			Paragraph titulo = MarcadoresPdf.encabezado(grafica.titulo(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), "grafica-" + grafica.id());
			titulo.setSpacingBefore(8);
			documento.add(titulo);
			documento.add(new Paragraph(grafica.subtitulo(), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, BaseColor.DARK_GRAY)));
			dibujarBloque(documento, writer, fuente, negrita, grafica, datos, alturaLeyenda);
		}
		return true;
	}

	// Leyenda, dibujo y notas de una gráfica. El dibujo va en un hueco de altura fija que empieza en la posición actual de
	// la página; las coordenadas se toman de ahí.
	private static void dibujarBloque(Document documento, PdfWriter writer, BaseFont fuente, BaseFont negrita,
			GraficaPdf grafica, Map<String, Object> datos, float alturaLeyenda) throws DocumentException {
		float ancho = documento.getPageSize().getWidth() - documento.leftMargin() - documento.rightMargin();
		float yInicio = writer.getVerticalPosition(false);
		float alturaDibujo = alturaLeyenda + grafica.alto();

		PdfPCell hueco = new PdfPCell(new Phrase(" ", FontFactory.getFont(FontFactory.HELVETICA, 2)));
		hueco.setFixedHeight(alturaDibujo);
		hueco.setBorder(Rectangle.NO_BORDER);
		hueco.setPadding(0);
		PdfPTable tabla = new PdfPTable(1);
		tabla.setWidthPercentage(100);
		tabla.setSpacingBefore(0);
		tabla.setSpacingAfter(0);
		tabla.addCell(hueco);

		PdfContentByte cb = writer.getDirectContent();
		float x0 = documento.leftMargin();
		dibujarLeyenda(new DibujanteGraficas(cb, fuente, negrita, x0, yInicio, ancho, alturaLeyenda), fuente, grafica.leyenda(datos), ancho);
		grafica.dibujar(new DibujanteGraficas(cb, fuente, negrita, x0, yInicio - alturaLeyenda, ancho, grafica.alto()), datos);
		documento.add(tabla);

		for (String nota : grafica.notas(datos)) {
			Paragraph p = new Paragraph("• " + nota, FontFactory.getFont(FontFactory.HELVETICA, TAMANO_LEYENDA, BaseColor.DARK_GRAY));
			p.setSpacingBefore(1);
			documento.add(p);
		}
		Paragraph separacion = new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 6));
		separacion.setSpacingAfter(4);
		documento.add(separacion);
	}

	// Cuántas filas ocupa la leyenda: los renglones se llenan y, si no caben, siguen en la fila de abajo.
	private static int filasDeLeyenda(BaseFont fuente, List<GraficaPdf.Muestra> muestras, float ancho) {
		if (muestras.isEmpty()) {
			return 0;
		}
		int filas = 1;
		float x = 0;
		for (GraficaPdf.Muestra m : muestras) {
			float w = anchoDeMuestra(fuente, m.texto());
			if (x + w > ancho && x > 0) {
				filas++;
				x = 0;
			}
			x += w;
		}
		return filas;
	}

	private static float anchoDeMuestra(BaseFont fuente, String texto) {
		return 14f + fuente.getWidthPoint(texto, TAMANO_LEYENDA) + 18f;
	}

	private static void dibujarLeyenda(DibujanteGraficas d, BaseFont fuente, List<GraficaPdf.Muestra> muestras, float ancho) {
		float x = 0;
		float y = 2;
		for (GraficaPdf.Muestra m : muestras) {
			float w = anchoDeMuestra(fuente, m.texto());
			if (x + w > ancho && x > 0) {
				x = 0;
				y += ALTO_FILA_LEYENDA;
			}
			d.rectangulo(x, y, 8, 8, m.estilo().color(), EstiloPdf.GRIS_MEDIO);
			d.patron(x, y, 8, 8, m.estilo().patron());
			d.texto(m.texto(), x + 11, y + 6.5f, TAMANO_LEYENDA, DibujanteGraficas.IZQUIERDA, EstiloPdf.NEGRO, false);
			x += w;
		}
	}
}
