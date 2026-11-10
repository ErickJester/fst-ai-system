package com.ipn.mx.fst.service.graficas;

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
public final class G1BarrasGruposPdf implements GraficaPdf {

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

	@Override
	public String subtitulo() {
		return "Media ± DE de los especímenes, en segundos analizados. El eje llega a 300 s, la ventana del Día 2.";
	}

	@Override
	public boolean aplica(Map<String, Object> datos) {
		return grupos(datos).stream().anyMatch(DatosGraficas::tieneDatos);
	}

	@Override
	public float alto() {
		return ALTO;
	}

	@Override
	public List<Muestra> leyenda(Map<String, Object> datos) {
		List<Muestra> muestras = new ArrayList<>();
		for (String c : DatosGraficas.CONDUCTAS_TRES) {
			muestras.add(new Muestra(etiquetaDeConducta(c), EstiloPdf.conducta(c)));
		}
		return muestras;
	}

	private static String etiquetaDeConducta(String clave) {
		return switch (clave) {
			case "nado activo" -> "Nado activo";
			case "inmovilidad" -> "Inmovilidad";
			case "escalamiento" -> "Escalamiento";
			default -> clave;
		};
	}

	@Override
	public List<String> notas(Map<String, Object> datos) {
		List<String> notas = new ArrayList<>();
		for (Map<String, Object> g : grupos(datos)) {
			String etiqueta = String.valueOf(g.get("etiqueta"));
			if (bool(g.get("nivelMixto"))) {
				notas.add(etiqueta + ": mezcla niveles de clasificación; no se promedia junto. Sus números van por tanda en la tabla.");
			} else if (!tieneDatos(g)) {
				notas.add(etiqueta + ": sin datos.");
			}
			List<Map<String, Object>> faltantes = DatosGraficas.lista(g.get("faltantes"));
			if (!faltantes.isEmpty()) {
				int pendientes = faltantes.stream().mapToInt(f -> DatosGraficas.entero(f.get("nCilindros"))).sum();
				String causas = String.join(", ", faltantes.stream()
						.map(f -> "Tanda " + f.get("letra") + " " + (f.get("estado") == null ? "sin subir" : f.get("estado"))).toList());
				notas.add(etiqueta + ": " + pendientes + " especímenes pendientes (" + causas + ").");
			}
		}
		String activa = conductaActivaFueraDeBarras(grupos(datos));
		if (activa != null) {
			notas.add("Conducta activa, fuera de las barras (segundos medios por espécimen): " + activa + ".");
		}
		notas.add(NOTA_DE);
		notas.add(NOTA_ORIGEN);
		return notas;
	}

	// Los grupos con conducta activa de media mayor que 0, con su media; null si ninguno.
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
		return partes.isEmpty() ? null : String.join(" · ", partes);
	}

	@Override
	public void dibujar(DibujanteGraficas d, Map<String, Object> datos) {
		List<Map<String, Object>> grupos = grupos(datos);
		float ancho = d.ancho() - M_IZQ - M_DER;
		float alto = d.alto() - M_SUP - M_INF;
		double escala = DatosGraficas.escalaSegundos(datos, DatosGraficas.CONDUCTAS_TRES);
		float yBase = M_SUP + alto;
		float slot = grupos.isEmpty() ? ancho : ancho / grupos.size();
		float bw = Math.min(40f, slot * 0.2f);

		// Ejes y marcas: 0 a la escala, cada quinta parte.
		for (int k = 0; k <= 5; k++) {
			float v = (float) (escala / 5 * k);
			float yv = yBase - (v / (float) escala) * alto;
			d.linea(M_IZQ, yv, M_IZQ + ancho, yv, k == 0 ? 1.1f : 0.35f, EstiloPdf.GRIS_CLARO, null);
			d.texto(String.valueOf(Math.round(v)), M_IZQ - 4, yv + 2.5f, 7.5f, DibujanteGraficas.DERECHA, EstiloPdf.GRIS_OSCURO, false);
		}
		d.texto("s", M_IZQ, 4, 7.5f, DibujanteGraficas.IZQUIERDA, EstiloPdf.GRIS_OSCURO, false);

		for (int i = 0; i < grupos.size(); i++) {
			Map<String, Object> g = grupos.get(i);
			float cx = M_IZQ + slot * (i + 0.5f);
			if (tieneDatos(g)) {
				dibujarGrupo(d, g, cx, bw, yBase, alto, escala);
			} else {
				String marca = bool(g.get("nivelMixto")) ? "nivel mixto" : "sin datos";
				d.texto(marca, cx, yBase - 6, 7.5f, DibujanteGraficas.CENTRO, EstiloPdf.GRIS_OSCURO, false);
			}
			List<String> lineas = partirEtiqueta(d, String.valueOf(g.get("etiqueta")), slot - 4f, 8f);
			for (int k = 0; k < lineas.size(); k++) {
				d.texto(lineas.get(k), cx, yBase + 13 + k * 9.5f, 8f, DibujanteGraficas.CENTRO, EstiloPdf.NEGRO, true);
			}
			float yN = yBase + 13 + (lineas.size() - 1) * 9.5f + 11f;
			d.texto(DatosGraficas.textoN(g), cx, yN, 7f, DibujanteGraficas.CENTRO, EstiloPdf.GRIS_OSCURO, false);
		}
		d.linea(M_IZQ, yBase, M_IZQ + ancho, yBase, 1.1f, EstiloPdf.NEGRO, null);
	}

	// Las tres barras de un grupo, en su orden, con el patrón de cada conducta y su barra de error.
	private void dibujarGrupo(DibujanteGraficas d, Map<String, Object> g, float cx, float bw, float yBase, float alto, double escala) {
		float separacion = bw * 1.15f;
		for (int j = 0; j < DatosGraficas.CONDUCTAS_TRES.size(); j++) {
			String conducta = DatosGraficas.CONDUCTAS_TRES.get(j);
			EstiloPdf estilo = EstiloPdf.conducta(conducta);
			Map<String, Object> e = estadisticos(g, conducta);
			Double media = dbl(e.get("media"));
			Double de = dbl(e.get("de"));
			if (media == null) {
				continue;
			}
			float x = cx + (j - 1) * separacion - bw / 2;
			float top = yBase - (float) (Math.min(media, escala) / escala) * alto;
			d.rectangulo(x, top, bw, yBase - top, estilo.color(), null);
			d.patron(x, top, bw, yBase - top, estilo.patron());
			float cxBarra = x + bw / 2;
			if (de != null) {
				float lo = yBase - (float) (Math.min(escala, media + de) / escala) * alto;
				float hi = yBase - (float) (Math.max(0, media - de) / escala) * alto;
				d.linea(cxBarra, lo, cxBarra, hi, 0.9f, EstiloPdf.NEGRO, null);
				d.linea(cxBarra - bw * 0.3f, lo, cxBarra + bw * 0.3f, lo, 0.9f, EstiloPdf.NEGRO, null);
				d.linea(cxBarra - bw * 0.3f, hi, cxBarra + bw * 0.3f, hi, 0.9f, EstiloPdf.NEGRO, null);
			}
			List<Double> valores = valoresS(e);
			for (int k = 0; k < valores.size(); k++) {
				Double v = valores.get(k);
				if (v == null) {
					continue;
				}
				float px = cxBarra + (((k * 7) % 5) - 2) * (bw * 0.12f);
				float py = yBase - (float) (Math.min(v, escala) / escala) * alto;
				d.marca(EstiloPdf.Forma.CIRCULO, px, py, 1.6f, EstiloPdf.NEGRO, false);
			}
		}
	}

	// Parte la etiqueta en hasta dos renglones que caben en el ancho dado (medido con la fuente real); lo que no cabe
	// se recorta con «…».
	static List<String> partirEtiqueta(DibujanteGraficas d, String texto, float ancho, float tamano) {
		if (d.anchoTexto(texto, tamano, true) <= ancho) {
			return List.of(texto);
		}
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
		List<String> dos = new ArrayList<>();
		for (int k = 0; k < Math.min(2, lineas.size()); k++) {
			dos.add(recortar(d, lineas.get(k), ancho, tamano));
		}
		if (lineas.size() > 2) {
			dos.set(1, recortar(d, dos.get(1) + "…", ancho, tamano));
		}
		return dos;
	}

	// Si el texto no cabe, quita caracteres del final y pone «…» hasta que quepa.
	private static String recortar(DibujanteGraficas d, String texto, float ancho, float tamano) {
		if (d.anchoTexto(texto, tamano, true) <= ancho) {
			return texto;
		}
		String base = texto.endsWith("…") ? texto.substring(0, texto.length() - 1) : texto;
		String t = base + "…";
		while (d.anchoTexto(t, tamano, true) > ancho && !base.isEmpty()) {
			base = base.substring(0, base.length() - 1);
			t = base + "…";
		}
		return t;
	}
}
