// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

// BaseColor: el color de iText.
import com.itextpdf.text.BaseColor;

// Estilos de las gráficas del PDF, en blanco y negro (el reporte se imprime así): tonos de gris, patrones de relleno,
// formas de marcador y trazos. Los grupos y las conductas se distinguen por la combinación, no solo por el tono.
//
// Esa última frase es el principio de diseño del archivo, y es también una buena práctica de
// accesibilidad: una gráfica que solo se distingue por el color deja fuera a quien la imprime en blanco y
// negro, la fotocopia o no distingue bien los colores. Combinando tono, patrón, forma y trazo, cada serie
// se reconoce por cuatro señales distintas a la vez.
// Un record con cuatro campos: el estilo entero viaja como un solo objeto, de la leyenda al dibujo.
public record EstiloPdf(BaseColor color, Patron patron, Forma forma, Trazo trazo) {

	// Cómo se rellena un área (una barra, un sector). NINGUNO es relleno liso.
	public enum Patron { NINGUNO, RAYADO, PUNTEADO, VERTICAL, HORIZONTAL }

	// La figurita que marca cada punto de una línea.
	public enum Forma { CIRCULO, CUADRADO, TRIANGULO, ROMBO, CRUZ }

	// Null = sólido; si no, el patrón de guiones (trazo, espacio).
	//
	// Es un enum CON datos: cada constante carga su propio patrón de guiones, como los enums de
	// domain/valores cargan su texto. {4f, 2f} significa 4 puntos de línea y 2 de hueco, repetidos.
	public enum Trazo {
		SOLIDA(null), DISCONTINUA(new float[] {4f, 2f}), LARGA(new float[] {8f, 3f});

		private final float[] guiones;

		// El constructor de un enum es siempre privado: las constantes de arriba son las únicas instancias.
		Trazo(float[] guiones) {
			this.guiones = guiones;
		}

		// El null de SOLIDA es lo que el dibujante interpreta como "línea continua".
		public float[] guiones() {
			return guiones;
		}
	}

	// La paleta: cinco tonos de gris, de negro a blanco. No hay colores a propósito (ver el encabezado).
	// Los tres números de BaseColor son rojo, verde y azul, de 0 a 255; iguales entre sí dan un gris.
	public static final BaseColor NEGRO = new BaseColor(0, 0, 0);
	public static final BaseColor GRIS_OSCURO = new BaseColor(90, 90, 90);
	public static final BaseColor GRIS_MEDIO = new BaseColor(150, 150, 150);
	public static final BaseColor GRIS_CLARO = new BaseColor(215, 215, 215);
	public static final BaseColor BLANCO = new BaseColor(255, 255, 255);

	// Conducta del PDF: nado sólido negro, inmovilidad rayada, escalamiento punteada (las tres juntas en G1).
	//
	// Las tres se distinguen por tono, patrón, forma Y trazo a la vez: en una impresión en blanco y negro
	// siguen siendo tres series reconocibles.
	// El switch con flechas (->) es la forma moderna: no hace falta break y cada rama devuelve su valor.
	public static EstiloPdf conducta(String clave) {
		return switch (clave) {
			case "nado activo" -> new EstiloPdf(NEGRO, Patron.NINGUNO, Forma.CIRCULO, Trazo.SOLIDA);
			case "inmovilidad" -> new EstiloPdf(GRIS_OSCURO, Patron.RAYADO, Forma.CUADRADO, Trazo.DISCONTINUA);
			// default y no un case para "escalamiento": cubre también cualquier otra conducta, así que el
			// switch nunca se queda sin respuesta.
			default -> new EstiloPdf(GRIS_MEDIO, Patron.PUNTEADO, Forma.TRIANGULO, Trazo.LARGA);
		};
	}

	// Igual que estilosDeGrupos de la pantalla: control sólido, referencia rayada, experimentales rotan entre tres.
	//
	// "Igual que la pantalla" importa: el mismo grupo se ve con el mismo estilo en la web y en el PDF, así
	// que quien compare las dos figuras reconoce las series sin volver a mirar la leyenda.
	// El control siempre en negro sólido no es casual: es la línea base, la referencia contra la que se
	// lee todo lo demás, y conviene que sea la más marcada.
	public static EstiloPdf grupo(String tipo, int indiceExperimental) {
		if ("control".equals(tipo)) {
			return new EstiloPdf(NEGRO, Patron.NINGUNO, Forma.CIRCULO, Trazo.SOLIDA);
		}
		if ("referencia".equals(tipo)) {
			return new EstiloPdf(GRIS_OSCURO, Patron.RAYADO, Forma.ROMBO, Trazo.DISCONTINUA);
		}
		// Los experimentales pueden ser varios, así que rotan entre tres combinaciones.
		// Los tres arreglos van EN PARALELO: el índice k elige a la vez patrón, forma y trazo, de modo que
		// cada combinación es única en las tres señales.
		Patron[] patrones = {Patron.PUNTEADO, Patron.VERTICAL, Patron.HORIZONTAL};
		Forma[] formas = {Forma.TRIANGULO, Forma.CUADRADO, Forma.CRUZ};
		Trazo[] trazos = {Trazo.LARGA, Trazo.DISCONTINUA, Trazo.SOLIDA};
		// El módulo hace que el cuarto grupo experimental vuelva a la primera combinación. Con más de tres
		// se repiten los estilos, que es mejor que quedarse sin ninguno; en la práctica no suele pasar.
		int k = indiceExperimental % 3;
		return new EstiloPdf(GRIS_MEDIO, patrones[k], formas[k], trazos[k]);
	}
}
