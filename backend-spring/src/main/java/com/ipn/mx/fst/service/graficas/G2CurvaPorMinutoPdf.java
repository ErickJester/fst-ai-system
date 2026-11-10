package com.ipn.mx.fst.service.graficas;

import static com.ipn.mx.fst.service.graficas.DatosGraficas.bool;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.dbl;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.entero;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.grupos;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.lista;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.mapa;
import static com.ipn.mx.fst.service.graficas.DatosGraficas.tieneDatos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// G2 · Curva por minuto en el PDF: tres paneles (nado activo, inmovilidad, escalamiento), uno por conducta, con una línea
// por grupo a lo largo de los minutos 1 a 5. Cada punto es la media del minuto; su barra vertical es ± DE. Un minuto con
// menos de 60 s en algún espécimen va con marcador hueco. Conducta activa no tiene panel: va en las notas de G1.
// Es la forma impresa de la curva de la pantalla (ESPECIFICACION.md, §3 G2). Eje de 0 a 60 s, el minuto.
public final class G2CurvaPorMinutoPdf implements GraficaPdf {

	private static final float ALTO = 196f;
	private static final float GAP = 12f;
	private static final float M_IZQ = 24f;
	private static final float M_DER = 6f;
	private static final float M_SUP = 18f;
	private static final float M_INF = 30f;
	private static final double ESCALA = 60.0;

	@Override
	public String id() {
		return "g2";
	}

	@Override
	public String titulo() {
		return "Curva por minuto · Día 2";
	}

	@Override
	public String subtitulo() {
		return "Media por minuto de cada conducta, con su barra ± DE. Minutos 1 a 5; cada minuto va de 0 a 60 s.";
	}

	@Override
	public boolean aplica(Map<String, Object> datos) {
		return grupos(datos).stream().filter(DatosGraficas::tieneDatos)
				.flatMap(g -> lista(g.get("porMinuto")).stream())
				.anyMatch(m -> entero(m.get("n")) > 0);
	}

	@Override
	public float alto() {
		return ALTO;
	}

	@Override
	public List<Muestra> leyenda(Map<String, Object> datos) {
		List<Muestra> muestras = new ArrayList<>();
		List<Map<String, Object>> grupos = grupos(datos);
		List<EstiloPdf> estilos = estilosDeGrupos(grupos);
		for (int i = 0; i < grupos.size(); i++) {
			if (tieneDatos(grupos.get(i))) {
				muestras.add(new Muestra(String.valueOf(grupos.get(i).get("etiqueta")), estilos.get(i)));
			}
		}
		return muestras;
	}

	// Un estilo por grupo, en el orden de la respuesta: control, referencia y luego los experimentales.
	static List<EstiloPdf> estilosDeGrupos(List<Map<String, Object>> grupos) {
		List<EstiloPdf> estilos = new ArrayList<>();
		int experimentales = 0;
		for (Map<String, Object> g : grupos) {
			String tipo = String.valueOf(g.get("tipo"));
			boolean experimental = !"control".equals(tipo) && !"referencia".equals(tipo);
			estilos.add(EstiloPdf.grupo(tipo, experimental ? experimentales++ : 0));
		}
		return estilos;
	}

	@Override
	public List<String> notas(Map<String, Object> datos) {
		List<String> notas = new ArrayList<>();
		for (Map<String, Object> g : grupos(datos)) {
			String etiqueta = String.valueOf(g.get("etiqueta"));
			if (bool(g.get("nivelMixto"))) {
				notas.add(etiqueta + ": mezcla niveles de clasificación; no se promedia junto, no se dibuja.");
			} else if (!tieneDatos(g)) {
				notas.add(etiqueta + ": sin datos, no se dibuja.");
			}
		}
		boolean incompleto = grupos(datos).stream().flatMap(g -> lista(g.get("porMinuto")).stream())
				.anyMatch(m -> bool(m.get("incompleto")));
		if (incompleto) {
			notas.add("Minuto incompleto (marcador hueco): algún espécimen tiene menos de 60 s en ese minuto; sus segundos son los reales.");
		}
		notas.add(NOTA_DE);
		notas.add(NOTA_ORIGEN);
		return notas;
	}

	@Override
	public void dibujar(DibujanteGraficas d, Map<String, Object> datos) {
		List<Map<String, Object>> grupos = grupos(datos);
		List<EstiloPdf> estilos = estilosDeGrupos(grupos);
		float panelAncho = (d.ancho() - 2 * GAP) / 3f;
		String[] conductas = DatosGraficas.CONDUCTAS_TRES.toArray(new String[0]);
		for (int p = 0; p < conductas.length; p++) {
			float x0 = p * (panelAncho + GAP);
			dibujarPanel(d, x0, panelAncho, grupos, estilos, conductas[p]);
		}
	}

	private void dibujarPanel(DibujanteGraficas d, float x0, float panelAncho, List<Map<String, Object>> grupos,
			List<EstiloPdf> estilos, String conducta) {
		float ancho = panelAncho - M_IZQ - M_DER;
		float alto = d.alto() - M_SUP - M_INF;
		float yBase = M_SUP + alto;
		float paso = ancho / DatosGraficas.MINUTOS;
		float separacion = Math.min(7f, paso / (grupos.size() + 1f));

		d.texto(etiquetaConducta(conducta), x0 + M_IZQ, 10, 8f, DibujanteGraficas.IZQUIERDA, EstiloPdf.NEGRO, true);
		for (int v = 0; v <= 60; v += 20) {
			float yv = yBase - (float) (v / ESCALA) * alto;
			d.linea(x0 + M_IZQ, yv, x0 + M_IZQ + ancho, yv, v == 0 ? 1f : 0.35f, EstiloPdf.GRIS_CLARO, null);
			d.texto(String.valueOf(v), x0 + M_IZQ - 3, yv + 2.5f, 7f, DibujanteGraficas.DERECHA, EstiloPdf.GRIS_OSCURO, false);
		}
		for (int m = 1; m <= DatosGraficas.MINUTOS; m++) {
			d.texto("min " + m, x0 + M_IZQ + (m - 0.5f) * paso, yBase + 11, 7f, DibujanteGraficas.CENTRO, EstiloPdf.GRIS_OSCURO, false);
		}

		for (int i = 0; i < grupos.size(); i++) {
			Map<String, Object> g = grupos.get(i);
			if (!tieneDatos(g)) {
				continue;
			}
			EstiloPdf estilo = estilos.get(i);
			float desplazamiento = (i - (grupos.size() - 1) / 2f) * separacion;
			List<float[]> puntos = new ArrayList<>();
			List<Boolean> incompletos = new ArrayList<>();
			for (Map<String, Object> minuto : lista(g.get("porMinuto"))) {
				int m = entero(minuto.get("minuto"));
				Map<String, Object> e = mapa(mapa(minuto.get("porConducta")).get(conducta));
				Double media = dbl(e.get("media"));
				Double de = dbl(e.get("de"));
				if (media == null || entero(minuto.get("n")) == 0) {
					continue;
				}
				float x = x0 + M_IZQ + (m - 0.5f) * paso + desplazamiento;
				float y = yBase - (float) (media / ESCALA) * alto;
				puntos.add(new float[] {x, y});
				incompletos.add(bool(minuto.get("incompleto")));
				if (de != null) {
					float lo = yBase - (float) (Math.min(ESCALA, media + de) / ESCALA) * alto;
					float hi = yBase - (float) (Math.max(0, media - de) / ESCALA) * alto;
					d.linea(x - 1.6f, lo, x - 1.6f, hi, 0.5f, estilo.color(), null);
					d.linea(x - 3.5f, lo, x + 0.1f, lo, 0.5f, estilo.color(), null);
					d.linea(x - 3.5f, hi, x + 0.1f, hi, 0.5f, estilo.color(), null);
				}
			}
			for (int k = 1; k < puntos.size(); k++) {
				float[] a = puntos.get(k - 1);
				float[] b = puntos.get(k);
				d.linea(a[0], a[1], b[0], b[1], 0.9f, estilo.color(), estilo.trazo());
			}
			for (int k = 0; k < puntos.size(); k++) {
				float[] pt = puntos.get(k);
				d.marca(estilo.forma(), pt[0], pt[1], 2.4f, estilo.color(), incompletos.get(k));
			}
		}
		d.linea(x0 + M_IZQ, yBase, x0 + M_IZQ + ancho, yBase, 1f, EstiloPdf.NEGRO, null);
	}

	private static String etiquetaConducta(String clave) {
		return switch (clave) {
			case "nado activo" -> "Nado activo";
			case "inmovilidad" -> "Inmovilidad";
			case "escalamiento" -> "Escalamiento";
			default -> clave;
		};
	}
}
