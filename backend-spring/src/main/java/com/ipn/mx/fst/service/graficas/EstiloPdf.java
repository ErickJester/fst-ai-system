package com.ipn.mx.fst.service.graficas;

import com.itextpdf.text.BaseColor;

// Estilos de las gráficas del PDF, en blanco y negro (el reporte se imprime así): tonos de gris, patrones de relleno,
// formas de marcador y trazos. Los grupos y las conductas se distinguen por la combinación, no solo por el tono.
public record EstiloPdf(BaseColor color, Patron patron, Forma forma, Trazo trazo) {

	public enum Patron { NINGUNO, RAYADO, PUNTEADO, VERTICAL, HORIZONTAL }

	public enum Forma { CIRCULO, CUADRADO, TRIANGULO, ROMBO, CRUZ }

	// Null = sólido; si no, el patrón de guiones (trazo, espacio).
	public enum Trazo {
		SOLIDA(null), DISCONTINUA(new float[] {4f, 2f}), LARGA(new float[] {8f, 3f});

		private final float[] guiones;

		Trazo(float[] guiones) {
			this.guiones = guiones;
		}

		public float[] guiones() {
			return guiones;
		}
	}

	public static final BaseColor NEGRO = new BaseColor(0, 0, 0);
	public static final BaseColor GRIS_OSCURO = new BaseColor(90, 90, 90);
	public static final BaseColor GRIS_MEDIO = new BaseColor(150, 150, 150);
	public static final BaseColor GRIS_CLARO = new BaseColor(215, 215, 215);
	public static final BaseColor BLANCO = new BaseColor(255, 255, 255);

	// Conducta del PDF: nado sólido negro, inmovilidad rayada, escalamiento punteada (las tres juntas en G1).
	public static EstiloPdf conducta(String clave) {
		return switch (clave) {
			case "nado activo" -> new EstiloPdf(NEGRO, Patron.NINGUNO, Forma.CIRCULO, Trazo.SOLIDA);
			case "inmovilidad" -> new EstiloPdf(GRIS_OSCURO, Patron.RAYADO, Forma.CUADRADO, Trazo.DISCONTINUA);
			default -> new EstiloPdf(GRIS_MEDIO, Patron.PUNTEADO, Forma.TRIANGULO, Trazo.LARGA);
		};
	}

	// Igual que estilosDeGrupos de la pantalla: control sólido, referencia rayada, experimentales rotan entre tres.
	public static EstiloPdf grupo(String tipo, int indiceExperimental) {
		if ("control".equals(tipo)) {
			return new EstiloPdf(NEGRO, Patron.NINGUNO, Forma.CIRCULO, Trazo.SOLIDA);
		}
		if ("referencia".equals(tipo)) {
			return new EstiloPdf(GRIS_OSCURO, Patron.RAYADO, Forma.ROMBO, Trazo.DISCONTINUA);
		}
		Patron[] patrones = {Patron.PUNTEADO, Patron.VERTICAL, Patron.HORIZONTAL};
		Forma[] formas = {Forma.TRIANGULO, Forma.CUADRADO, Forma.CRUZ};
		Trazo[] trazos = {Trazo.LARGA, Trazo.DISCONTINUA, Trazo.SOLIDA};
		int k = indiceExperimental % 3;
		return new EstiloPdf(GRIS_MEDIO, patrones[k], formas[k], trazos[k]);
	}
}
