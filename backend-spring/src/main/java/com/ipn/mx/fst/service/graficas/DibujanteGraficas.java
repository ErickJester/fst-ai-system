package com.ipn.mx.fst.service.graficas;

import com.ipn.mx.fst.service.graficas.EstiloPdf.Forma;
import com.ipn.mx.fst.service.graficas.EstiloPdf.Patron;
import com.ipn.mx.fst.service.graficas.EstiloPdf.Trazo;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;

// Dibujo vectorial sobre PdfContentByte para las gráficas del PDF. Las coordenadas locales van con el origen arriba a la
// izquierda de la caja (en puntos, como una página de iText), así cada gráfica se escribe como en un lienzo de pantalla.
// Un solo dibujante para todas las gráficas: ejes, barras, líneas, marcadores, patrones y texto.
public final class DibujanteGraficas {

	public static final int IZQUIERDA = 0;
	public static final int CENTRO = 1;
	public static final int DERECHA = 2;

	private final PdfContentByte cb;
	private final BaseFont fuente;
	private final BaseFont fuenteNegrita;
	private final float x0;
	private final float yArriba;
	private final float ancho;
	private final float alto;

	public DibujanteGraficas(PdfContentByte cb, BaseFont fuente, BaseFont fuenteNegrita, float x0, float yArriba, float ancho, float alto) {
		this.cb = cb;
		this.fuente = fuente;
		this.fuenteNegrita = fuenteNegrita;
		this.x0 = x0;
		this.yArriba = yArriba;
		this.ancho = ancho;
		this.alto = alto;
	}

	public float ancho() {
		return ancho;
	}

	public float alto() {
		return alto;
	}

	private float X(float x) {
		return x0 + x;
	}

	private float Y(float y) {
		return yArriba - y;
	}

	public void linea(float x1, float y1, float x2, float y2, float grosor, BaseColor color, Trazo trazo) {
		cb.saveState();
		cb.setLineWidth(grosor);
		cb.setColorStroke(color);
		if (trazo != null && trazo.guiones() != null) {
			cb.setLineDash(trazo.guiones()[0], trazo.guiones()[1], 0);
		}
		cb.moveTo(X(x1), Y(y1));
		cb.lineTo(X(x2), Y(y2));
		cb.stroke();
		cb.restoreState();
	}

	public void rectangulo(float x, float y, float w, float h, BaseColor relleno, BaseColor borde) {
		cb.saveState();
		cb.rectangle(X(x), Y(y + h), w, h);
		if (relleno != null) {
			cb.setColorFill(relleno);
		}
		if (borde != null) {
			cb.setColorStroke(borde);
			cb.setLineWidth(0.6f);
		}
		if (relleno != null && borde != null) {
			cb.fillStroke();
		} else if (relleno != null) {
			cb.fill();
		} else {
			cb.stroke();
		}
		cb.restoreState();
	}

	// Un patrón de relleno sobre un rectángulo ya dibujado: líneas o puntos en blanco, recortados a la caja. Así una barra
	// se distingue también impresa en blanco y negro.
	public void patron(float x, float y, float w, float h, Patron patron) {
		if (patron == null || patron == Patron.NINGUNO) {
			return;
		}
		cb.saveState();
		cb.rectangle(X(x), Y(y + h), w, h);
		cb.clip();
		cb.newPath();
		cb.setLineWidth(0.7f);
		cb.setColorStroke(EstiloPdf.BLANCO);
		cb.setColorFill(EstiloPdf.BLANCO);
		final float paso = 3.5f;
		switch (patron) {
			case RAYADO -> {
				for (float k = -h; k < w + h; k += paso) {
					cb.moveTo(X(x + k), Y(y + h));
					cb.lineTo(X(x + k + h), Y(y));
				}
				cb.stroke();
			}
			case VERTICAL -> {
				for (float k = paso / 2; k < w; k += paso) {
					cb.moveTo(X(x + k), Y(y));
					cb.lineTo(X(x + k), Y(y + h));
				}
				cb.stroke();
			}
			case HORIZONTAL -> {
				for (float k = paso / 2; k < h; k += paso) {
					cb.moveTo(X(x), Y(y + k));
					cb.lineTo(X(x + w), Y(y + k));
				}
				cb.stroke();
			}
			case PUNTEADO -> {
				for (float i = paso / 2; i < w; i += paso) {
					for (float j = paso / 2; j < h; j += paso) {
						cb.circle(X(x + i), Y(y + j), 0.8f);
					}
				}
				cb.fill();
			}
			default -> {
				// NINGUNO se descarta arriba.
			}
		}
		cb.restoreState();
	}

	// Un marcador de forma fija; hueco = relleno blanco con borde (un minuto incompleto).
	public void marca(Forma forma, float x, float y, float r, BaseColor relleno, boolean hueco) {
		cb.saveState();
		float cx = X(x);
		float cy = Y(y);
		cb.setLineWidth(0.8f);
		cb.setColorStroke(EstiloPdf.NEGRO);
		cb.setColorFill(hueco ? EstiloPdf.BLANCO : relleno);
		switch (forma) {
			case CIRCULO -> cb.circle(cx, cy, r);
			case CUADRADO -> cb.rectangle(cx - r, cy - r, 2 * r, 2 * r);
			case TRIANGULO -> {
				cb.moveTo(cx, cy + r);
				cb.lineTo(cx + r, cy - r);
				cb.lineTo(cx - r, cy - r);
				cb.closePath();
			}
			case ROMBO -> {
				cb.moveTo(cx, cy + r);
				cb.lineTo(cx + r, cy);
				cb.lineTo(cx, cy - r);
				cb.lineTo(cx - r, cy);
				cb.closePath();
			}
			case CRUZ -> {
				cb.moveTo(cx - r, cy - r);
				cb.lineTo(cx + r, cy + r);
				cb.moveTo(cx - r, cy + r);
				cb.lineTo(cx + r, cy - r);
				cb.setColorStroke(EstiloPdf.NEGRO);
				cb.stroke();
				cb.restoreState();
				return;
			}
			default -> {
				// Los casos están completos.
			}
		}
		cb.fillStroke();
		cb.restoreState();
	}

	public float anchoTexto(String texto, float tamano, boolean negrita) {
		return (negrita ? fuenteNegrita : fuente).getWidthPoint(texto, tamano);
	}

	public void texto(String texto, float x, float y, float tamano, int alineacion, BaseColor color, boolean negrita) {
		cb.beginText();
		cb.setFontAndSize(negrita ? fuenteNegrita : fuente, tamano);
		cb.setColorFill(color == null ? EstiloPdf.NEGRO : color);
		int alineacionPdf = switch (alineacion) {
			case CENTRO -> PdfContentByte.ALIGN_CENTER;
			case DERECHA -> PdfContentByte.ALIGN_RIGHT;
			default -> PdfContentByte.ALIGN_LEFT;
		};
		cb.showTextAligned(alineacionPdf, texto, X(x), Y(y), 0);
		cb.endText();
	}

	// Un polígono cerrado, por ejemplo una banda; el relleno va en gris claro.
	public void poligono(float[] xs, float[] ys, BaseColor relleno) {
		if (xs.length < 3) {
			return;
		}
		cb.saveState();
		cb.setColorFill(relleno);
		cb.moveTo(X(xs[0]), Y(ys[0]));
		for (int i = 1; i < xs.length; i++) {
			cb.lineTo(X(xs[i]), Y(ys[i]));
		}
		cb.closePath();
		cb.fill();
		cb.restoreState();
	}
}
