// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

// Un "import static" trae MÉTODOS sueltos, no clases: permite escribir grupos(datos) en vez de
// DatosGraficas.grupos(datos). Se usa aquí porque estos seis aparecen decenas de veces y el prefijo
// repetido haría ilegible el código de dibujo.
import static com.ipn.mx.fst.service.graficas.DatosGraficas.bool;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.dbl;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.estadisticos;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.grupos;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.tieneDatos;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.valoresS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// G1 · Barras agrupadas en el PDF: la media de cada conducta por grupo, con su barra de error ± DE y un punto por
// espécimen. En el PDF van las tres conductas juntas (no hay selector). Conducta activa va en las notas, con sus segundos
// medios. Misma escala, orden y textos que la pantalla (frontend/src/components/resultados/graficas/ESPECIFICACION.md, §3).
//
// Es la gráfica principal del reporte: la que responde la pregunta del experimento, si el grupo tratado se
// comporta distinto del control.
// Dos decisiones de presentación que merecen entenderse:
//   el PUNTO POR ESPECIMEN encima de la barra enseña los datos crudos, no solo el promedio. Con n = 6,
//   ver los seis individuos dice mucho más que una barra sola: una media de 30 puede venir de seis
//   animales en torno a 30 o de tres en 5 y tres en 55, y la figura lo delata.
//   «conducta activa» se saca de las barras y va a una nota porque solo existe en los análisis de nivel
//   agrupado: meterla como cuarta barra haría comparar cosas que no son comparables.
public final class G1BarrasGruposPdf implements GraficaPdf {

	// Las medidas del dibujo, en puntos. Los cuatro márgenes dejan sitio para los ejes y las etiquetas:
	// el izquierdo para los números del eje Y, el inferior para los nombres de grupo y su n.
	private static final float ALTO = 200f;
	private static final float M_IZQ = 34f;
	private static final float M_DER = 6f;
	private static final float M_SUP = 12f;
	private static final float M_INF = 40f;

	@Override
	public String id() {
		return "g1";
	}

	@Override
	public String titulo() {
		return "Tiempo por conducta · Día 2";
	}

	// El subtítulo explica la escala, que es justo lo que podría malinterpretarse (ver escalaSegundos).
	@Override
	public String subtitulo() {
		return "Media ± DE de los especímenes, en segundos analizados. El eje llega a 300 s, la ventana del Día 2.";
	}

	// Basta con que UN grupo tenga datos para que valga la pena dibujar.
	// DatosGraficas::tieneDatos es una referencia a método: equivale a g -> tieneDatos(g).
	@Override
	public boolean aplica(Map<String, Object> datos) {
		return grupos(datos).stream().anyMatch(DatosGraficas::tieneDatos);
	}

	@Override
	public float alto() {
		return ALTO;
	}

	// La leyenda son las tres conductas, con el mismo estilo con el que se dibujan las barras.
	@Override
	public List<Muestra> leyenda(Map<String, Object> datos) {
		List<Muestra> muestras = new ArrayList<>();
		for (String c : DatosGraficas.CONDUCTAS_TRES) {
			muestras.add(new Muestra(etiquetaDeConducta(c), EstiloPdf.conducta(c)));
		}
		return muestras;
	}

	// De la clave de la base ("nado activo") al texto que se imprime ("Nado activo"), con mayúscula.
	// El default devuelve la clave tal cual: es mejor que una conducta nueva salga en minúsculas a que
	// desaparezca de la leyenda.
	private static String etiquetaDeConducta(String clave) {
		return switch (clave) {
			case "nado activo" -> "Nado activo";
			case "inmovilidad" -> "Inmovilidad";
			case "escalamiento" -> "Escalamiento";
			default -> clave;
		};
	}

	// Las notas de debajo de la gráfica. Son la parte honesta de la figura: explican qué NO se está
	// viendo y por qué, en vez de omitir grupos en silencio.
	@Override
	public List<String> notas(Map<String, Object> datos) {
		List<String> notas = new ArrayList<>();
		for (Map<String, Object> g : grupos(datos)) {
			String etiqueta = String.valueOf(g.get("etiqueta"));
			// Dos motivos distintos por los que un grupo puede no tener barras, con su explicación.
			if (bool(g.get("nivelMixto"))) {
				notas.add(etiqueta + ": mezcla niveles de clasificación; no se promedia junto. Sus números van por tanda en la tabla.");
			} else if (!tieneDatos(g)) {
				notas.add(etiqueta + ": sin datos.");
			}
			// Y un tercero, que puede darse incluso en un grupo que SÍ tiene barras: que falten
			// especímenes por analizar, con lo que la media es de menos individuos de los previstos.
			List<Map<String, Object>> faltantes = DatosGraficas.lista(g.get("faltantes"));
			if (!faltantes.isEmpty()) {
				// Se suman los cilindros de las tandas pendientes: cuántos especímenes faltan en total.
				int pendientes = faltantes.stream().mapToInt(f -> DatosGraficas.entero(f.get("nCilindros"))).sum();
				// Y el detalle de por qué: el estado de cada tanda, o "sin subir" si ni siquiera tiene
				// video (el estado llega null en ese caso).
				String causas = String.join(", ", faltantes.stream()
						.map(f -> "Tanda " + f.get("letra") + " " + (f.get("estado") == null ? "sin subir" : f.get("estado"))).toList());
				notas.add(etiqueta + ": " + pendientes + " especímenes pendientes (" + causas + ").");
			}
		}
		// La conducta activa, que no tiene barra, se informa aquí con sus números.
		String activa = conductaActivaFueraDeBarras(grupos(datos));
		if (activa != null) {
			notas.add("Conducta activa, fuera de las barras (segundos medios por espécimen): " + activa + ".");
		}
		// Las dos notas comunes van siempre al final (ver GraficaPdf).
		notas.add(NOTA_DE);
		notas.add(NOTA_ORIGEN);
		return notas;
	}

	// Los grupos con conducta activa de media mayor que 0, con su media; null si ninguno.
	//
	// El "> 0" es lo que hace que la nota no aparezca en el caso normal: en un análisis de nivel preciso
	// nadie tiene conducta activa, así que todas las medias son 0 y la nota se omite entera.
	private static String conductaActivaFueraDeBarras(List<Map<String, Object>> grupos) {
		List<String> partes = new ArrayList<>();
		for (Map<String, Object> g : grupos) {
			if (!tieneDatos(g)) {
				continue;
			}
			Double media = dbl(estadisticos(g, DatosGraficas.CONDUCTA_ACTIVA).get("media"));
			if (media != null && media > 0) {
				partes.add(g.get("etiqueta") + " " + DatosGraficas.numero(media) + " s");
			}
		}
		// null y no un texto vacío: quien llama solo añade la nota si hay algo que decir.
		return partes.isEmpty() ? null : String.join(" · ", partes);
	}

	@Override
	public void dibujar(DibujanteGraficas d, Map<String, Object> datos) {
		List<Map<String, Object>> grupos = grupos(datos);
		// El área de dibujo útil, descontados los márgenes.
		float ancho = d.ancho() - M_IZQ - M_DER;
		float alto = d.alto() - M_SUP - M_INF;
		// La escala del eje Y: 300 como mínimo, más si algo se sale (ver DatosGraficas.escalaSegundos).
		double escala = DatosGraficas.escalaSegundos(datos, DatosGraficas.CONDUCTAS_TRES);
		// La línea de base (el cero del eje Y), en coordenadas locales que bajan.
		float yBase = M_SUP + alto;
		// El ancho que le toca a cada grupo, repartiendo el espacio por igual.
		float slot = grupos.isEmpty() ? ancho : ancho / grupos.size();
		// El ancho de cada barra: la quinta parte del hueco del grupo, con un tope de 40 puntos para que
		// con pocos grupos no salgan barras absurdamente anchas.
		float bw = Math.min(40f, slot * 0.2f);

		// Ejes y marcas: 0 a la escala, cada quinta parte.
		// Seis líneas (de 0 a 5) con su número a la izquierda. La de k == 0 es la base y va más gruesa.
		for (int k = 0; k <= 5; k++) {
			float v = (float) (escala / 5 * k);
			// La fórmula que convierte un valor en segundos a una altura del dibujo: la proporción sobre
			// la escala, aplicada al alto, restada de la base (porque las coordenadas locales bajan).
			float yv = yBase - (v / (float) escala) * alto;
			d.linea(M_IZQ, yv, M_IZQ + ancho, yv, k == 0 ? 1.1f : 0.35f, EstiloPdf.GRIS_CLARO, null);
			// El número, alineado a la derecha y pegado al eje; el +2.5 lo centra verticalmente.
			d.texto(String.valueOf(Math.round(v)), M_IZQ - 4, yv + 2.5f, 7.5f, DibujanteGraficas.DERECHA, EstiloPdf.GRIS_OSCURO, false);
		}
		// La unidad del eje, arriba a la izquierda.
		d.texto("s", M_IZQ, 4, 7.5f, DibujanteGraficas.IZQUIERDA, EstiloPdf.GRIS_OSCURO, false);

		for (int i = 0; i < grupos.size(); i++) {
			Map<String, Object> g = grupos.get(i);
			// El centro del hueco de este grupo: el 0.5 lo pone en medio y no en el borde.
			float cx = M_IZQ + slot * (i + 0.5f);
			if (tieneDatos(g)) {
				dibujarGrupo(d, g, cx, bw, yBase, alto, escala);
			} else {
				// Un grupo sin barras deja su hueco con un texto que dice por qué: así se ve que el grupo
				// existe y no se pudo dibujar, en vez de desaparecer de la figura.
				String marca = bool(g.get("nivelMixto")) ? "nivel mixto" : "sin datos";
				d.texto(marca, cx, yBase - 6, 7.5f, DibujanteGraficas.CENTRO, EstiloPdf.GRIS_OSCURO, false);
			}
			// La etiqueta debajo, partida en hasta dos renglones si no cabe.
			List<String> lineas = partirEtiqueta(d, String.valueOf(g.get("etiqueta")), slot - 4f, 8f);
			for (int k = 0; k < lineas.size(); k++) {
				d.texto(lineas.get(k), cx, yBase + 13 + k * 9.5f, 8f, DibujanteGraficas.CENTRO, EstiloPdf.NEGRO, true);
			}
			// Y la n debajo del último renglón, sea cual sea su número de líneas.
			float yN = yBase + 13 + (lineas.size() - 1) * 9.5f + 11f;
			d.texto(DatosGraficas.textoN(g), cx, yN, 7f, DibujanteGraficas.CENTRO, EstiloPdf.GRIS_OSCURO, false);
		}
		// El eje X se traza AL FINAL, encima de las barras, para que quede limpio y continuo.
		d.linea(M_IZQ, yBase, M_IZQ + ancho, yBase, 1.1f, EstiloPdf.NEGRO, null);
	}

	// Las tres barras de un grupo, en su orden, con el patrón de cada conducta y su barra de error.
	private void dibujarGrupo(DibujanteGraficas d, Map<String, Object> g, float cx, float bw, float yBase, float alto, double escala) {
		// Un poco más que el ancho de barra, para que queden separadas sin tocarse.
		float separacion = bw * 1.15f;
		for (int j = 0; j < DatosGraficas.CONDUCTAS_TRES.size(); j++) {
			String conducta = DatosGraficas.CONDUCTAS_TRES.get(j);
			EstiloPdf estilo = EstiloPdf.conducta(conducta);
			Map<String, Object> e = estadisticos(g, conducta);
			Double media = dbl(e.get("media"));
			Double de = dbl(e.get("de"));
			// Sin media no hay barra: se salta en vez de dibujar una de altura cero, que parecería un dato.
			if (media == null) {
				continue;
			}
			// (j - 1) coloca las tres barras a la izquierda, en el centro y a la derecha del centro del
			// grupo; el - bw/2 pasa del centro de la barra a su borde izquierdo.
			float x = cx + (j - 1) * separacion - bw / 2;
			// Math.min con la escala evita que una barra se salga del dibujo si por algo superara el eje.
			float top = yBase - (float) (Math.min(media, escala) / escala) * alto;
			// Primero la barra rellena y luego su patrón encima (ver DibujanteGraficas.patron).
			d.rectangulo(x, top, bw, yBase - top, estilo.color(), null);
			d.patron(x, top, bw, yBase - top, estilo.patron());
			float cxBarra = x + bw / 2;
			if (de != null) {
				// La barra de error: la línea vertical de media-DE a media+DE y dos travesaños.
				// Math.min arriba y Math.max(0, ...) abajo: el extremo superior no se sale del dibujo y el
				// inferior no baja del cero, porque un tiempo negativo no significa nada.
				float lo = yBase - (float) (Math.min(escala, media + de) / escala) * alto;
				float hi = yBase - (float) (Math.max(0, media - de) / escala) * alto;
				d.linea(cxBarra, lo, cxBarra, hi, 0.9f, EstiloPdf.NEGRO, null);
				d.linea(cxBarra - bw * 0.3f, lo, cxBarra + bw * 0.3f, lo, 0.9f, EstiloPdf.NEGRO, null);
				d.linea(cxBarra - bw * 0.3f, hi, cxBarra + bw * 0.3f, hi, 0.9f, EstiloPdf.NEGRO, null);
			}
			// Los puntos de cada espécimen, encima de la barra: los datos crudos detrás del promedio.
			List<Double> valores = valoresS(e);
			for (int k = 0; k < valores.size(); k++) {
				Double v = valores.get(k);
				if (v == null) {
					continue;
				}
				// El desplazamiento horizontal reparte los puntos para que dos valores iguales no se
				// tapen. (k * 7) % 5 genera 0,2,4,1,3,... que es una dispersión más repartida que k % 5;
				// el -2 la centra sobre la barra. Es determinista: la misma figura sale siempre igual,
				// cosa que un desplazamiento al azar no garantizaría.
				float px = cxBarra + (((k * 7) % 5) - 2) * (bw * 0.12f);
				float py = yBase - (float) (Math.min(v, escala) / escala) * alto;
				d.marca(EstiloPdf.Forma.CIRCULO, px, py, 1.6f, EstiloPdf.NEGRO, false);
			}
		}
	}

	// Parte la etiqueta en hasta dos renglones que caben en el ancho dado (medido con la fuente real); lo que no cabe
	// se recorta con «…».
	//
	// Hace falta porque los nombres de grupo los escribe el investigador y pueden ser largos
	// ("Fluoxetina 10 mg/kg i.p."). Sin esto se saldrían del hueco y se encimarían con los vecinos.
	// static y sin private para que la prueba pueda llamarlo directamente.
	static List<String> partirEtiqueta(DibujanteGraficas d, String texto, float ancho, float tamano) {
		// Camino rápido: si cabe entero, un solo renglón.
		if (d.anchoTexto(texto, tamano, true) <= ancho) {
			return List.of(texto);
		}
		// Reparto clásico por palabras: se va añadiendo mientras quepa y, cuando no, se empieza otra línea.
		List<String> lineas = new ArrayList<>();
		String actual = "";
		for (String palabra : texto.split(" ")) {
			String candidata = actual.isEmpty() ? palabra : actual + " " + palabra;
			if (d.anchoTexto(candidata, tamano, true) <= ancho) {
				actual = candidata;
			} else {
				if (!actual.isEmpty()) {
					lineas.add(actual);
				}
				actual = palabra;
			}
		}
		if (!actual.isEmpty()) {
			lineas.add(actual);
		}
		// Solo caben dos renglones: el espacio debajo de las barras es fijo (M_INF).
		List<String> dos = new ArrayList<>();
		for (int k = 0; k < Math.min(2, lineas.size()); k++) {
			dos.add(recortar(d, lineas.get(k), ancho, tamano));
		}
		// Si sobraban renglones, se marca el segundo con puntos suspensivos para avisar de que el nombre
		// está cortado.
		if (lineas.size() > 2) {
			dos.set(1, recortar(d, dos.get(1) + "…", ancho, tamano));
		}
		return dos;
	}

	// Si el texto no cabe, quita caracteres del final y pone «…» hasta que quepa.
	//
	// Se mide en cada vuelta con la tipografía real, porque quitar una letra estrecha apenas reduce el
	// ancho: calcularlo por el número de caracteres fallaría.
	private static String recortar(DibujanteGraficas d, String texto, float ancho, float tamano) {
		if (d.anchoTexto(texto, tamano, true) <= ancho) {
			return texto;
		}
		// Si ya traía puntos suspensivos se le quitan, para no acabar con dos seguidos.
		String base = texto.endsWith("…") ? texto.substring(0, texto.length() - 1) : texto;
		String t = base + "…";
		// El !base.isEmpty() del final evita un bucle infinito en un hueco tan estrecho que ni los puntos
		// suspensivos quepan.
		while (d.anchoTexto(t, tamano, true) > ancho && !base.isEmpty()) {
			base = base.substring(0, base.length() - 1);
			t = base + "…";
		}
		return t;
	}
}
