// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// El estilo del reporte de resultados en PDF: el mismo del sistema (frontend/src/styles/fst.css y modernist.css). La tipografía es
// Archivo (la de la web, incrustada en el PDF), la tinta, el vino y los grises son los de la web, las tablas llevan solo líneas
// horizontales finas, sin rejilla ni fondo gris, con los números a la derecha, y cada página tiene un encabezado con «FST» y las
// migas del reporte, y un pie con la versión y el número de página.
//
// La página va en blanco (no en el gris claro de la pantalla) porque el reporte se imprime.
//
// Una instancia por documento: una BaseFont incrustada anota qué letras usó para incluir solo esas, y compartirla entre dos
// PDF mezclaría esas cuentas. Los bytes de las fuentes sí se leen una sola vez.
public final class EstiloReportePdf {

	private static final Logger log = LoggerFactory.getLogger(EstiloReportePdf.class);

	// Los colores del sistema. Los grises «transparentes» de la web (la tinta al 40, 55 y 65 %) se calculan sobre el blanco del papel.
	public static final BaseColor TINTA = new BaseColor(0x23, 0x1f, 0x20);       // --color-text
	public static final BaseColor ACENTO = new BaseColor(0x6f, 0x1d, 0x46);      // --color-accent
	public static final BaseColor ACENTO_300 = new BaseColor(0xdd, 0xb2, 0xc5);  // --color-accent-300
	public static final BaseColor SUPERFICIE = new BaseColor(0xe7, 0xe7, 0xe9);  // --color-surface
	public static final BaseColor NEUTRO_300 = new BaseColor(0xd3, 0xd4, 0xd6);  // --color-neutral-300
	public static final BaseColor NEUTRO_400 = new BaseColor(0xb2, 0xb4, 0xb7);  // --color-neutral-400
	public static final BaseColor DIVISOR = new BaseColor(167, 165, 166);        // --color-divider: la tinta al 40 %
	public static final BaseColor APAGADO = new BaseColor(134, 132, 132);        // --muted: la tinta al 55 %
	public static final BaseColor APAGADO_2 = new BaseColor(112, 109, 110);      // --muted-2: la tinta al 65 %

	public enum Peso {
		NORMAL("Archivo-Regular.ttf"), NEGRITA("Archivo-Bold.ttf"), EXTRANEGRITA("Archivo-ExtraBold.ttf");

		private final String archivo;

		Peso(String archivo) {
			this.archivo = archivo;
		}
	}

	// Los bytes de cada archivo de fuente, leídos una vez. Null si el archivo falta: entonces se usa Helvetica.
	private static final byte[] BYTES_NORMAL = leer(Peso.NORMAL);
	private static final byte[] BYTES_NEGRITA = leer(Peso.NEGRITA);
	private static final byte[] BYTES_EXTRANEGRITA = leer(Peso.EXTRANEGRITA);

	private final BaseFont normal;
	private final BaseFont negrita;
	private final BaseFont extranegrita;

	public EstiloReportePdf() {
		this.normal = crear(Peso.NORMAL, BYTES_NORMAL, BaseFont.HELVETICA);
		this.negrita = crear(Peso.NEGRITA, BYTES_NEGRITA, BaseFont.HELVETICA_BOLD);
		this.extranegrita = crear(Peso.EXTRANEGRITA, BYTES_EXTRANEGRITA, BaseFont.HELVETICA_BOLD);
	}

	private static byte[] leer(Peso peso) {
		try (InputStream in = EstiloReportePdf.class.getResourceAsStream("/fonts/" + peso.archivo)) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			log.warn("No se pudo leer la fuente {}: {}", peso.archivo, e.getMessage());
			return null;
		}
	}

	// Si la fuente de Archivo falta o no se puede incrustar, el PDF sale con Helvetica: un reporte con otra letra es mejor que
	// ningún reporte.
	private static BaseFont crear(Peso peso, byte[] bytes, String alterna) {
		try {
			if (bytes != null) {
				return BaseFont.createFont(peso.archivo, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, bytes, null);
			}
			log.warn("Falta la fuente {}: el PDF usa Helvetica.", peso.archivo);
			return BaseFont.createFont(alterna, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
		} catch (DocumentException | IOException e) {
			log.warn("No se pudo preparar la fuente {} ({}): el PDF usa Helvetica.", peso.archivo, e.getMessage());
			try {
				return BaseFont.createFont(alterna, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
			} catch (DocumentException | IOException ex) {
				throw new IllegalStateException("No hay ninguna fuente para el PDF", ex);
			}
		}
	}

	// ── tipografía ──────────────────────────────────────────────────────────

	public BaseFont base(Peso peso) {
		return switch (peso) {
			case NORMAL -> normal;
			case NEGRITA -> negrita;
			case EXTRANEGRITA -> extranegrita;
		};
	}

	public Font fuente(float tamano, Peso peso, BaseColor color) {
		return new Font(base(peso), tamano, Font.NORMAL, color);
	}

	// El texto corriente: Archivo normal en tinta.
	public Font texto(float tamano) {
		return fuente(tamano, Peso.NORMAL, TINTA);
	}

	// Las aclaraciones al pie de una tabla o una gráfica: la letra de «hint» de la web, más pequeña y apagada.
	public Font nota() {
		return fuente(7.5f, Peso.NORMAL, APAGADO_2);
	}

	// ── párrafos ────────────────────────────────────────────────────────────

	// «Etiqueta: valor» con la etiqueta en negrita apagada y el valor en tinta. Todo en un solo párrafo, para que el texto del PDF
	// siga diciendo «Revisión humana: No» de corrido.
	public Paragraph dato(String etiqueta, String valor) {
		Paragraph p = new Paragraph();
		p.setFont(texto(9.5f));
		p.add(new Chunk(etiqueta + ": ", fuente(9.5f, Peso.EXTRANEGRITA, APAGADO_2)));
		p.add(new Chunk(valor, texto(9.5f)));
		p.setLeading(0, 1.45f);
		p.setSpacingAfter(3);
		return p;
	}

	// El título del documento, alineado a la izquierda como los h2 de la web.
	public Paragraph titulo(String texto, String destino) {
		Chunk c = new Chunk(texto, fuente(22, Peso.EXTRANEGRITA, TINTA));
		c.setLocalDestination(destino);
		Paragraph p = new Paragraph(c);
		p.setLeading(0, 1.1f);
		p.setSpacingAfter(8);
		return p;
	}

	// El título de una sección, con su destino para el marcador.
	public Paragraph seccion(String texto, String destino) {
		Chunk c = new Chunk(texto, fuente(13, Peso.EXTRANEGRITA, TINTA));
		c.setLocalDestination(destino);
		Paragraph p = new Paragraph(c);
		p.setSpacingBefore(16);
		p.setSpacingAfter(7);
		return p;
	}

	// El subtítulo de una subsección (por ejemplo, «Tiempos de análisis»).
	public Paragraph subseccion(String texto) {
		Paragraph p = new Paragraph(texto, fuente(10, Peso.EXTRANEGRITA, TINTA));
		p.setSpacingBefore(4);
		p.setSpacingAfter(6);
		return p;
	}

	public Paragraph nota(String texto) {
		Paragraph p = new Paragraph(texto, nota());
		p.setLeading(0, 1.45f);
		p.setSpacingBefore(4);
		return p;
	}

	// ── tablas ──────────────────────────────────────────────────────────────

	// Una tabla a todo el ancho, con los anchos relativos dados.
	public PdfPTable tabla(float... anchos) throws DocumentException {
		PdfPTable t = new PdfPTable(anchos.length);
		t.setWidthPercentage(100);
		t.setWidths(anchos);
		t.setSpacingBefore(2);
		return t;
	}

	// Los encabezados: letra pequeña, en negrita y apagada, con una línea más gruesa debajo, como el th de la web. `alineaciones` trae
	// una letra por columna: I (izquierda, para el texto) o D (derecha, para los números).
	public void encabezados(PdfPTable tabla, String alineaciones, String... textos) {
		for (int i = 0; i < textos.length; i++) {
			Chunk c = new Chunk(textos[i], fuente(7.2f, Peso.EXTRANEGRITA, APAGADO_2));
			c.setCharacterSpacing(0.35f);
			PdfPCell celda = new PdfPCell(new Phrase(c));
			celda.setHorizontalAlignment(alineaciones.charAt(i) == 'D' ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
			celda.setVerticalAlignment(Element.ALIGN_BOTTOM);
			celda.setBorder(Rectangle.BOTTOM);
			celda.setBorderWidthBottom(1.3f);
			celda.setBorderColorBottom(DIVISOR);
			celda.setPaddingTop(4);
			celda.setPaddingBottom(5);
			celda.setPaddingLeft(i == 0 ? 0 : 4);
			celda.setPaddingRight(4);
			celda.setUseAscender(true);
			celda.setUseDescender(true);
			tabla.addCell(celda);
		}
		tabla.setHeaderRows(1);
	}

	// Una celda de dato: solo la línea fina de abajo, sin rejilla ni relleno.
	private PdfPCell celda(String texto, Font letra, int alineacion, boolean primera) {
		PdfPCell celda = new PdfPCell(new Phrase(texto, letra));
		celda.setHorizontalAlignment(alineacion);
		celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
		celda.setBorder(Rectangle.BOTTOM);
		celda.setBorderWidthBottom(0.5f);
		celda.setBorderColorBottom(DIVISOR);
		celda.setPaddingTop(4.5f);
		celda.setPaddingBottom(5.5f);
		celda.setPaddingLeft(primera ? 0 : 4);
		celda.setPaddingRight(4);
		celda.setUseAscender(true);
		celda.setUseDescender(true);
		return celda;
	}

	// Texto a la izquierda (nombres, grupos, archivos). `primera` quita el relleno izquierdo para alinear con el resto de la página.
	public PdfPCell izquierda(String texto, Font letra, boolean primera) {
		return celda(texto, letra, Element.ALIGN_LEFT, primera);
	}

	// Números a la derecha, como en las tablas de la web.
	public PdfPCell derecha(String texto, Font letra) {
		return celda(texto, letra, Element.ALIGN_RIGHT, false);
	}

	// ── encabezado y pie de cada página ─────────────────────────────────────

	// El marco de cada página: arriba «FST» con las migas «Experimentos / nombre / Resultados» y una línea de tinta (como la barra
	// superior de la web), y abajo una línea fina con el nombre del reporte, la versión del sistema y «Página n de N».
	public static final class Marco extends PdfPageEventHelper {
		private final EstiloReportePdf estilo;
		private final String experimento;
		private final String version;
		private PdfTemplate total;
		private int ultimaPagina;

		public Marco(EstiloReportePdf estilo, String experimento, String version) {
			this.estilo = estilo;
			this.experimento = experimento == null ? "" : experimento;
			this.version = version == null || version.isBlank() ? "" : "v" + version.trim();
		}

		@Override
		public void onOpenDocument(PdfWriter writer, Document documento) {
			// Hueco para el total de páginas, que solo se sabe al cerrar el documento.
			total = writer.getDirectContent().createTemplate(30, 12);
		}

		@Override
		public void onEndPage(PdfWriter writer, Document documento) {
			ultimaPagina = writer.getPageNumber();
			PdfContentByte cb = writer.getDirectContent();
			float izq = documento.leftMargin();
			float der = documento.getPageSize().getWidth() - documento.rightMargin();
			float alto = documento.getPageSize().getHeight();

			// Arriba: «FST» y las migas.
			BaseFont marca = estilo.base(Peso.EXTRANEGRITA);
			float yTexto = alto - 38;
			cb.beginText();
			cb.setFontAndSize(marca, 14);
			cb.setColorFill(TINTA);
			cb.setTextMatrix(izq, yTexto);
			cb.showText("FST");
			cb.endText();
			float xMigas = izq + marca.getWidthPoint("FST", 14) + 12;
			String migas = recortar(estilo.base(Peso.NORMAL), 8f, "Experimentos / " + experimento + " / Resultados", der - xMigas);
			cb.beginText();
			cb.setFontAndSize(estilo.base(Peso.NORMAL), 8f);
			cb.setColorFill(APAGADO_2);
			cb.setTextMatrix(xMigas, yTexto);
			cb.showText(migas);
			cb.endText();
			cb.setColorStroke(TINTA);
			cb.setLineWidth(1.6f);
			cb.moveTo(izq, alto - 46);
			cb.lineTo(der, alto - 46);
			cb.stroke();

			// Abajo: línea fina, nombre del reporte a la izquierda y versión y página a la derecha.
			cb.setColorStroke(DIVISOR);
			cb.setLineWidth(0.5f);
			cb.moveTo(izq, 44);
			cb.lineTo(der, 44);
			cb.stroke();

			BaseFont letra = estilo.base(Peso.NORMAL);
			String pagina = (version.isEmpty() ? "" : version + " · ") + "Página " + writer.getPageNumber() + " de ";
			float anchoPagina = letra.getWidthPoint(pagina, 7.2f) + letra.getWidthPoint("00", 7.2f);
			String izquierda = recortar(letra, 7.2f, "Reporte de resultados · " + experimento, der - izq - anchoPagina - 16);
			cb.beginText();
			cb.setFontAndSize(letra, 7.2f);
			cb.setColorFill(APAGADO);
			cb.setTextMatrix(izq, 32);
			cb.showText(izquierda);
			cb.endText();
			cb.beginText();
			cb.setFontAndSize(letra, 7.2f);
			cb.setColorFill(APAGADO);
			float xPagina = der - anchoPagina;
			cb.setTextMatrix(xPagina, 32);
			cb.showText(pagina);
			cb.endText();
			// El total va en un hueco reservado: se rellena al cerrar.
			cb.addTemplate(total, xPagina + letra.getWidthPoint(pagina, 7.2f), 32);
		}

		@Override
		public void onCloseDocument(PdfWriter writer, Document documento) {
			total.beginText();
			total.setFontAndSize(estilo.base(Peso.NORMAL), 7.2f);
			total.setColorFill(APAGADO);
			total.setTextMatrix(0, 0);
			total.showText(String.valueOf(ultimaPagina));
			total.endText();
		}

		// Lo que cabe del texto en el ancho dado, con «…» si se recorta.
		private static String recortar(BaseFont letra, float tamano, String texto, float ancho) {
			if (letra.getWidthPoint(texto, tamano) <= ancho) {
				return texto;
			}
			String corto = texto;
			while (corto.length() > 1 && letra.getWidthPoint(corto + "…", tamano) > ancho) {
				corto = corto.substring(0, corto.length() - 1);
			}
			return corto + "…";
		}
	}
}
