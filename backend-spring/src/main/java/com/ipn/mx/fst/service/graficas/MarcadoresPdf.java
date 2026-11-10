package com.ipn.mx.fst.service.graficas;

import com.itextpdf.text.Chunk;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfAction;
import com.itextpdf.text.pdf.PdfOutline;
import com.itextpdf.text.pdf.PdfWriter;

// Los marcadores del PDF (el índice del panel izquierdo del lector) y sus destinos internos. Cada sección tiene un
// destino con nombre, puesto en su título con Chunk.setLocalDestination; el marcador salta ahí. El orden de los
// marcadores es fijo: Resumen · Resultados por grupo · Gráficas (con las que aplican) · Tabla por espécimen ·
// Desglose por minuto · Modelo del análisis (solo en modo desarrollador). El PDF se abre con el panel desplegado.
public final class MarcadoresPdf {

	public static final String RESUMEN = "resumen";
	public static final String RESULTADOS_GRUPO = "resultados-grupo";
	public static final String GRAFICAS = "graficas";
	public static final String TABLA_ESPECIMEN = "tabla-especimen";
	public static final String DESGLOSE_MINUTO = "desglose-minuto";
	public static final String MODELO = "modelo-analisis";

	private final PdfWriter writer;

	// Llamar justo después de PdfWriter.getInstance y antes de abrir el documento: el modo de página se fija ahí.
	public MarcadoresPdf(PdfWriter writer) {
		writer.setViewerPreferences(PdfWriter.PageModeUseOutlines);
		this.writer = writer;
	}

	// Un marcador de primer nivel, que salta al destino con nombre. La raíz se pide al momento: antes de abrir el
	// documento todavía no existe.
	public PdfOutline seccion(String titulo, String destino) {
		return new PdfOutline(writer.getRootOutline(), PdfAction.gotoLocalPage(destino, false), titulo);
	}

	// Un marcador bajo otro (por ejemplo, cada gráfica bajo «Gráficas»).
	public static PdfOutline hijo(PdfOutline padre, String titulo, String destino) {
		return new PdfOutline(padre, PdfAction.gotoLocalPage(destino, false), titulo);
	}

	// Un encabezado de sección con su destino: el texto y la fuente, y el destino en el mismo párrafo.
	public static Paragraph encabezado(String texto, Font fuente, String destino) {
		Chunk chunk = new Chunk(texto, fuente);
		chunk.setLocalDestination(destino);
		Paragraph parrafo = new Paragraph(chunk);
		parrafo.setSpacingAfter(6);
		return parrafo;
	}
}
