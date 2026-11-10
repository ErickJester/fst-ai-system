// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

// Los tres enums anidados de EstiloPdf, importados para nombrarlos sin el prefijo.
import com.ipn.mx.fst.service.graficas.EstiloPdf.Forma;
import com.ipn.mx.fst.service.graficas.EstiloPdf.Patron;
import com.ipn.mx.fst.service.graficas.EstiloPdf.Trazo;
import com.itextpdf.text.BaseColor;
// BaseFont: la tipografía de bajo nivel, la única que entiende el lienzo de dibujo directo.
import com.itextpdf.text.pdf.BaseFont;
// PdfContentByte: el lienzo donde se pinta con coordenadas absolutas.
import com.itextpdf.text.pdf.PdfContentByte;

// Dibujo vectorial sobre PdfContentByte para las gráficas del PDF. Las coordenadas locales van con el origen arriba a la
// izquierda de la caja (en puntos, como una página de iText), así cada gráfica se escribe como en un lienzo de pantalla.
// Un solo dibujante para todas las gráficas: ejes, barras, líneas, marcadores, patrones y texto.
//
// Qué es VECTORIAL: el dibujo se guarda como instrucciones de geometría (líneas, rectángulos, círculos),
// no como una imagen de píxeles. Se puede acercar sin que se pixele y se imprime nítido a cualquier
// tamaño. Para una figura de artículo es lo que corresponde.
// El cambio de coordenadas es la razón de ser de la clase: en un PDF el eje Y crece HACIA ARRIBA y el
// origen está abajo a la izquierda, mientras que al dibujar se piensa de arriba hacia abajo, como en una
// pantalla. Los métodos X() e Y() de abajo hacen esa traducción, y así ninguna gráfica tiene que pensar
// en ella ni saber en qué parte de la página va a acabar.
public final class DibujanteGraficas {

	// Las tres alineaciones de texto, como enteros. Son constantes propias para que las gráficas no
	// tengan que importar nada de iText.
	public static final int IZQUIERDA = 0;
	public static final int CENTRO = 1;
	public static final int DERECHA = 2;

	private final PdfContentByte cb;
	private final BaseFont fuente;
	private final BaseFont fuenteNegrita;
	// El origen de la caja en coordenadas de la PÁGINA, y su tamaño.
	private final float x0;
	private final float yArriba;
	private final float ancho;
	private final float alto;

	// Todos los campos son final: un dibujante se construye para una caja concreta y no cambia.
	// SeccionGraficasPdf crea uno por leyenda y otro por gráfica, con orígenes distintos.
	public DibujanteGraficas(PdfContentByte cb, BaseFont fuente, BaseFont fuenteNegrita, float x0, float yArriba, float ancho, float alto) {
		this.cb = cb;
		this.fuente = fuente;
		this.fuenteNegrita = fuenteNegrita;
		this.x0 = x0;
		this.yArriba = yArriba;
		this.ancho = ancho;
		this.alto = alto;
	}

	// El tamaño de la caja, para que la gráfica reparta su espacio.
	public float ancho() {
		return ancho;
	}

	public float alto() {
		return alto;
	}

	// LA traducción de coordenadas, en dos líneas.
	// En X solo hay que desplazar: los dos ejes crecen hacia la derecha.
	private float X(float x) {
		return x0 + x;
	}

	// En Y hay que RESTAR, porque el eje local baja y el del PDF sube. Es lo que permite escribir "y = 10"
	// pensando "diez puntos desde arriba" y que salga bien.
	// Mayúsculas en los nombres, contra la costumbre de Java: son tan cortos y aparecen tantas veces que
	// así destacan como lo que son, un cambio de sistema de coordenadas.
	private float Y(float y) {
		return yArriba - y;
	}

	// Una línea recta, con su grosor, color y trazo (continuo o de guiones).
	public void linea(float x1, float y1, float x2, float y2, float grosor, BaseColor color, Trazo trazo) {
		// saveState/restoreState guardan y recuperan el estado del lienzo (color, grosor, guiones).
		// Sin ellos, lo que se cambia aquí afectaría a todo lo que se dibuje después. Es el patrón que
		// siguen TODOS los métodos de esta clase.
		cb.saveState();
		cb.setLineWidth(grosor);
		cb.setColorStroke(color);
		// El null de Trazo.SOLIDA significa línea continua, así que solo se configuran los guiones cuando
		// de verdad los hay.
		if (trazo != null && trazo.guiones() != null) {
			cb.setLineDash(trazo.guiones()[0], trazo.guiones()[1], 0);
		}
		// moveTo lleva el lápiz sin pintar; lineTo traza; stroke es lo que de verdad dibuja.
		cb.moveTo(X(x1), Y(y1));
		cb.lineTo(X(x2), Y(y2));
		cb.stroke();
		cb.restoreState();
	}

	// Un rectángulo, con relleno, con borde o con los dos.
	public void rectangulo(float x, float y, float w, float h, BaseColor relleno, BaseColor borde) {
		cb.saveState();
		// Y(y + h) y no Y(y): el rectángulo de iText se define por su esquina INFERIOR izquierda, y en
		// coordenadas locales (que bajan) la de abajo es y + h. Es justo el tipo de detalle que esta
		// clase existe para encapsular.
		cb.rectangle(X(x), Y(y + h), w, h);
		if (relleno != null) {
			cb.setColorFill(relleno);
		}
		if (borde != null) {
			cb.setColorStroke(borde);
			cb.setLineWidth(0.6f);
		}
		// Tres operaciones distintas según lo que se pidió: pintar y bordear, solo pintar, o solo bordear.
		// Llamar a la que no toca dejaría un borde negro o un relleno no deseado.
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
	//
	// Es la pieza que hace funcionar el diseño accesible de EstiloPdf: dos barras del mismo gris se
	// distinguen por su patrón.
	public void patron(float x, float y, float w, float h, Patron patron) {
		// Salida rápida: sin patrón no hay nada que hacer.
		if (patron == null || patron == Patron.NINGUNO) {
			return;
		}
		cb.saveState();
		// clip() recorta TODO lo que se dibuje después a este rectángulo: las líneas del patrón se trazan
		// de largo y lo que se sale simplemente no aparece. Sin esto habría que calcular dónde cortar cada
		// una, sobre todo las diagonales.
		cb.rectangle(X(x), Y(y + h), w, h);
		cb.clip();
		// newPath limpia el contorno usado para recortar, para que no se dibuje además como una línea.
		cb.newPath();
		cb.setLineWidth(0.7f);
		// El patrón va en BLANCO sobre el relleno: funciona con cualquier tono de gris de abajo.
		cb.setColorStroke(EstiloPdf.BLANCO);
		cb.setColorFill(EstiloPdf.BLANCO);
		// La separación entre líneas o puntos. Lo bastante junta para verse como textura y lo bastante
		// separada para no emborronar la barra.
		final float paso = 3.5f;
		switch (patron) {
			case RAYADO -> {
				// Diagonales a 45 grados. Empieza en -h para que las primeras entren por la esquina: sin
				// ese desplazamiento, la parte superior izquierda quedaría sin rayar.
				for (float k = -h; k < w + h; k += paso) {
					cb.moveTo(X(x + k), Y(y + h));
					cb.lineTo(X(x + k + h), Y(y));
				}
				// Un solo stroke para todas las líneas: mucho más eficiente que uno por línea.
				cb.stroke();
			}
			case VERTICAL -> {
				// paso / 2 arranca a media separación del borde, para que el patrón quede centrado.
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
				// Una rejilla de puntitos: dos bucles anidados y un solo fill al final.
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
		// restoreState también deshace el recorte: a partir de aquí se vuelve a poder dibujar en todas partes.
		cb.restoreState();
	}

	// Un marcador de forma fija; hueco = relleno blanco con borde (un minuto incompleto).
	//
	// El "hueco" es una señal con significado: un punto vacío avisa de que ese minuto no tiene los 60
	// segundos completos (ver la regla de GraficasDeResultados). Así el dato se muestra, pero marcado.
	public void marca(Forma forma, float x, float y, float r, BaseColor relleno, boolean hueco) {
		cb.saveState();
		// El centro, ya en coordenadas de la página; las formas se dibujan alrededor de él.
		float cx = X(x);
		float cy = Y(y);
		cb.setLineWidth(0.8f);
		cb.setColorStroke(EstiloPdf.NEGRO);
		cb.setColorFill(hueco ? EstiloPdf.BLANCO : relleno);
		switch (forma) {
			case CIRCULO -> cb.circle(cx, cy, r);
			// El rectángulo se define por su esquina, de ahí el -r en las dos coordenadas.
			case CUADRADO -> cb.rectangle(cx - r, cy - r, 2 * r, 2 * r);
			case TRIANGULO -> {
				// Tres vértices y closePath para cerrar la figura.
				cb.moveTo(cx, cy + r);
				cb.lineTo(cx + r, cy - r);
				cb.lineTo(cx - r, cy - r);
				cb.closePath();
			}
			case ROMBO -> {
				// Cuatro vértices, uno en cada punto cardinal.
				cb.moveTo(cx, cy + r);
				cb.lineTo(cx + r, cy);
				cb.lineTo(cx, cy - r);
				cb.lineTo(cx - r, cy);
				cb.closePath();
			}
			case CRUZ -> {
				// La cruz es el caso aparte: dos aspas que no encierran ningún área, así que no se puede
				// rellenar. Se traza y se sale del método antes del fillStroke común, cuidando de
				// restaurar el estado primero.
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
		// Las otras cuatro formas sí encierran área: se rellenan y se bordean de una vez.
		cb.fillStroke();
		cb.restoreState();
	}

	// Lo que mide un texto, para poder colocarlo sin que se encime con otra cosa.
	// Se mide con la tipografía real y no por el número de caracteres, porque una "i" es mucho más
	// estrecha que una "m".
	public float anchoTexto(String texto, float tamano, boolean negrita) {
		return (negrita ? fuenteNegrita : fuente).getWidthPoint(texto, tamano);
	}

	// Escribe un texto en una posición, con su tamaño, alineación, color y grosor.
	public void texto(String texto, float x, float y, float tamano, int alineacion, BaseColor color, boolean negrita) {
		// El texto en un PDF va entre beginText y endText; no lleva saveState porque esas dos ya acotan
		// el estado de texto.
		cb.beginText();
		cb.setFontAndSize(negrita ? fuenteNegrita : fuente, tamano);
		cb.setColorFill(color == null ? EstiloPdf.NEGRO : color);
		// Se traducen las constantes propias a las de iText, para que las gráficas no dependan de iText.
		int alineacionPdf = switch (alineacion) {
			case CENTRO -> PdfContentByte.ALIGN_CENTER;
			case DERECHA -> PdfContentByte.ALIGN_RIGHT;
			default -> PdfContentByte.ALIGN_LEFT;
		};
		// El último 0 es el ángulo de giro: todo el texto va horizontal.
		cb.showTextAligned(alineacionPdf, texto, X(x), Y(y), 0);
		cb.endText();
	}

	// Un polígono cerrado, por ejemplo una banda; el relleno va en gris claro.
	// Sirve para pintar una franja de ±DE alrededor de una curva: se recorre el borde de arriba, se baja
	// y se vuelve por el de abajo, y la figura cerrada se rellena.
	public void poligono(float[] xs, float[] ys, BaseColor relleno) {
		// Con menos de tres puntos no hay área que rellenar.
		if (xs.length < 3) {
			return;
		}
		cb.saveState();
		cb.setColorFill(relleno);
		cb.moveTo(X(xs[0]), Y(ys[0]));
		for (int i = 1; i < xs.length; i++) {
			cb.lineTo(X(xs[i]), Y(ys[i]));
		}
		// closePath une el último punto con el primero; sin él la figura quedaría abierta y el relleno
		// saldría mal.
		cb.closePath();
		// Solo fill, sin stroke: una banda de fondo no lleva contorno, que competiría con la curva.
		cb.fill();
		cb.restoreState();
	}
}
