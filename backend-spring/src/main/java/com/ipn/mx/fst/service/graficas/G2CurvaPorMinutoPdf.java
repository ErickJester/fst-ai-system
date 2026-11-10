// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

// Imports estáticos de los lectores de DatosGraficas: permiten escribir grupos(datos) sin el prefijo.
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
//
// Mientras G1 enseña el total de los cinco minutos, esta enseña la EVOLUCIÓN: cómo cambia la conducta a lo
// largo de la sesión. Es lo que suele interesar en esta prueba, porque la inmovilidad crece con los
// minutos y el efecto de un fármaco se ve en cómo cambia esa pendiente, no solo en el total.
// Tres paneles y no tres líneas en uno solo: con tres grupos serían nueve curvas encimadas e ilegibles.
// Cada panel es una conducta, y dentro de él una línea por grupo, que sí se puede leer.
public final class G2CurvaPorMinutoPdf implements GraficaPdf {

	private static final float ALTO = 196f;
	// La separación entre los tres paneles.
	private static final float GAP = 12f;
	private static final float M_IZQ = 24f;
	private static final float M_DER = 6f;
	private static final float M_SUP = 18f;
	private static final float M_INF = 30f;
	// El eje Y llega a 60 porque un minuto tiene 60 segundos: es el máximo posible, y fijarlo hace que
	// los tres paneles sean comparables entre sí y entre experimentos.
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

	// Aplica si ALGÚN grupo con datos tiene ALGÚN minuto con especímenes.
	// flatMap aplana los minutos de todos los grupos en un solo flujo, para poder preguntarlo de una vez.
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

	// Aquí la leyenda son los GRUPOS (no las conductas, como en G1): cada grupo es una línea, y la
	// conducta la dice el título de cada panel.
	@Override
	public List<Muestra> leyenda(Map<String, Object> datos) {
		List<Muestra> muestras = new ArrayList<>();
		List<Map<String, Object>> grupos = grupos(datos);
		List<EstiloPdf> estilos = estilosDeGrupos(grupos);
		for (int i = 0; i < grupos.size(); i++) {
			// Solo se nombran los grupos que de verdad se dibujan.
			if (tieneDatos(grupos.get(i))) {
				muestras.add(new Muestra(String.valueOf(grupos.get(i).get("etiqueta")), estilos.get(i)));
			}
		}
		return muestras;
	}

	// Un estilo por grupo, en el orden de la respuesta: control, referencia y luego los experimentales.
	//
	// La lista va EN PARALELO con la de grupos: el estilo i es el del grupo i. Se calcula una sola vez y
	// se usa en la leyenda y en el dibujo, para que una línea y su muestra no puedan salir distintas.
	// El contador de experimentales solo avanza con los experimentales, para que roten entre las tres
	// combinaciones de EstiloPdf.grupo sin saltarse ninguna.
	static List<EstiloPdf> estilosDeGrupos(List<Map<String, Object>> grupos) {
		List<EstiloPdf> estilos = new ArrayList<>();
		int experimentales = 0;
		for (Map<String, Object> g : grupos) {
			String tipo = String.valueOf(g.get("tipo"));
			boolean experimental = !"control".equals(tipo) && !"referencia".equals(tipo);
			// experimentales++ devuelve el valor ANTES de incrementar, que es justo el índice que toca.
			estilos.add(EstiloPdf.grupo(tipo, experimental ? experimentales++ : 0));
		}
		return estilos;
	}

	@Override
	public List<String> notas(Map<String, Object> datos) {
		List<String> notas = new ArrayList<>();
		// Igual que en G1: se explica qué grupos no se dibujan y por qué.
		for (Map<String, Object> g : grupos(datos)) {
			String etiqueta = String.valueOf(g.get("etiqueta"));
			if (bool(g.get("nivelMixto"))) {
				notas.add(etiqueta + ": mezcla niveles de clasificación; no se promedia junto, no se dibuja.");
			} else if (!tieneDatos(g)) {
				notas.add(etiqueta + ": sin datos, no se dibuja.");
			}
		}
		// Si hay algún minuto incompleto, se explica qué significa el marcador hueco. La nota solo sale
		// cuando de verdad hay alguno, para no cargar la figura de advertencias que no aplican.
		boolean incompleto = grupos(datos).stream().flatMap(g -> lista(g.get("porMinuto")).stream())
				.anyMatch(m -> bool(m.get("incompleto")));
		if (incompleto) {
			// "Sus segundos son los reales" es la parte importante: el minuto no se rellena hasta 60, se
			// enseña lo que de verdad se midió y se marca (ver la regla en GraficasDeResultados).
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
		// El ancho se reparte entre tres paneles, descontando las dos separaciones.
		float panelAncho = (d.ancho() - 2 * GAP) / 3f;
		String[] conductas = DatosGraficas.CONDUCTAS_TRES.toArray(new String[0]);
		for (int p = 0; p < conductas.length; p++) {
			// El origen horizontal de cada panel.
			float x0 = p * (panelAncho + GAP);
			dibujarPanel(d, x0, panelAncho, grupos, estilos, conductas[p]);
		}
	}

	// Un panel: sus ejes, sus marcas de minuto y una línea por grupo.
	private void dibujarPanel(DibujanteGraficas d, float x0, float panelAncho, List<Map<String, Object>> grupos,
			List<EstiloPdf> estilos, String conducta) {
		float ancho = panelAncho - M_IZQ - M_DER;
		float alto = d.alto() - M_SUP - M_INF;
		float yBase = M_SUP + alto;
		// El ancho de cada minuto en el eje X.
		float paso = ancho / DatosGraficas.MINUTOS;
		// Cuánto se separan entre sí las líneas de los grupos en un mismo minuto, para que sus puntos y
		// sus barras de error no se encimen. Con tope de 7 puntos y repartido según cuántos grupos haya.
		float separacion = Math.min(7f, paso / (grupos.size() + 1f));

		// El título del panel, arriba a la izquierda y en negrita.
		d.texto(etiquetaConducta(conducta), x0 + M_IZQ, 10, 8f, DibujanteGraficas.IZQUIERDA, EstiloPdf.NEGRO, true);
		// Las marcas del eje Y, de 20 en 20 hasta 60.
		for (int v = 0; v <= 60; v += 20) {
			float yv = yBase - (float) (v / ESCALA) * alto;
			d.linea(x0 + M_IZQ, yv, x0 + M_IZQ + ancho, yv, v == 0 ? 1f : 0.35f, EstiloPdf.GRIS_CLARO, null);
			d.texto(String.valueOf(v), x0 + M_IZQ - 3, yv + 2.5f, 7f, DibujanteGraficas.DERECHA, EstiloPdf.GRIS_OSCURO, false);
		}
		// Las etiquetas del eje X. El (m - 0.5f) las centra en su franja de minuto.
		for (int m = 1; m <= DatosGraficas.MINUTOS; m++) {
			d.texto("min " + m, x0 + M_IZQ + (m - 0.5f) * paso, yBase + 11, 7f, DibujanteGraficas.CENTRO, EstiloPdf.GRIS_OSCURO, false);
		}

		for (int i = 0; i < grupos.size(); i++) {
			Map<String, Object> g = grupos.get(i);
			if (!tieneDatos(g)) {
				continue;
			}
			EstiloPdf estilo = estilos.get(i);
			// Centra el abanico de grupos sobre la marca del minuto: con tres grupos, uno a la izquierda,
			// otro en el centro y otro a la derecha.
			float desplazamiento = (i - (grupos.size() - 1) / 2f) * separacion;
			// Se recogen los puntos primero y se unen después: la línea necesita conocer el siguiente
			// punto, y así además las barras de error quedan DEBAJO de la línea y los marcadores.
			List<float[]> puntos = new ArrayList<>();
			List<Boolean> incompletos = new ArrayList<>();
			for (Map<String, Object> minuto : lista(g.get("porMinuto"))) {
				int m = entero(minuto.get("minuto"));
				// Doble mapa: del minuto a su porConducta, y de ahí a la conducta de este panel.
				Map<String, Object> e = mapa(mapa(minuto.get("porConducta")).get(conducta));
				Double media = dbl(e.get("media"));
				Double de = dbl(e.get("de"));
				// Un minuto sin datos se SALTA, no se dibuja como 0: un video de 4 minutos no tiene
				// minuto 5, y pintarlo en cero diría que el animal no hizo nada, que es falso.
				if (media == null || entero(minuto.get("n")) == 0) {
					continue;
				}
				float x = x0 + M_IZQ + (m - 0.5f) * paso + desplazamiento;
				float y = yBase - (float) (media / ESCALA) * alto;
				puntos.add(new float[] {x, y});
				incompletos.add(bool(minuto.get("incompleto")));
				if (de != null) {
					// La barra de error, acotada arriba por la escala y abajo por cero (un tiempo
					// negativo no significa nada).
					float lo = yBase - (float) (Math.min(ESCALA, media + de) / ESCALA) * alto;
					float hi = yBase - (float) (Math.max(0, media - de) / ESCALA) * alto;
					// Desplazada 1.6 puntos a la izquierda del punto, para que no lo tape.
					d.linea(x - 1.6f, lo, x - 1.6f, hi, 0.5f, estilo.color(), null);
					d.linea(x - 3.5f, lo, x + 0.1f, lo, 0.5f, estilo.color(), null);
					d.linea(x - 3.5f, hi, x + 0.1f, hi, 0.5f, estilo.color(), null);
				}
			}
			// La línea que une los puntos, con el trazo del grupo (continuo, discontinuo o largo).
			// Empieza en k = 1 porque cada tramo necesita el punto anterior.
			for (int k = 1; k < puntos.size(); k++) {
				float[] a = puntos.get(k - 1);
				float[] b = puntos.get(k);
				d.linea(a[0], a[1], b[0], b[1], 0.9f, estilo.color(), estilo.trazo());
			}
			// Y los marcadores ENCIMA de la línea, con su forma. El hueco marca el minuto incompleto.
			for (int k = 0; k < puntos.size(); k++) {
				float[] pt = puntos.get(k);
				d.marca(estilo.forma(), pt[0], pt[1], 2.4f, estilo.color(), incompletos.get(k));
			}
		}
		// El eje X al final, encima de todo, para que quede limpio.
		d.linea(x0 + M_IZQ, yBase, x0 + M_IZQ + ancho, yBase, 1f, EstiloPdf.NEGRO, null);
	}

	// De la clave de la base al texto con mayúscula. Igual que en G1; se repite porque las dos clases son
	// independientes y comparten solo la interfaz.
	private static String etiquetaConducta(String clave) {
		return switch (clave) {
			case "nado activo" -> "Nado activo";
			case "inmovilidad" -> "Inmovilidad";
			case "escalamiento" -> "Escalamiento";
			default -> clave;
		};
	}
}
