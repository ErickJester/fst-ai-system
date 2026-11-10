// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

import java.util.List;
import java.util.Map;

// G6 · Mapa de calor por minuto en el PDF. Pendiente (lo hace m2): mientras no aplica, no se dibuja ni tiene marcador.
// Al implementarla, aplica debe decir cuándo hay datos (ver ESPECIFICACION.md, §3 G6 y «Cómo agregar una gráfica al PDF»).
//
// Esqueleto, igual que G3: ver sus comentarios para el porqué del patrón.
// Un mapa de calor normalmente usa color para la intensidad, y este PDF es en blanco y negro (ver
// EstiloPdf): de ahí que el subtítulo hable de "rangos de 10 s", es decir, escalones de gris discretos en
// vez de un degradado continuo. Unos pocos tonos bien separados se distinguen al imprimir; un degradado no.
public final class G6MapaPdf implements GraficaPdf {

	@Override
	public String id() {
		return "g6";
	}

	@Override
	public String titulo() {
		return "Mapa por minuto · Día 2";
	}

	@Override
	public String subtitulo() {
		return "Media de la conducta por grupo y minuto, en rangos de 10 s.";
	}

	@Override
	public boolean aplica(Map<String, Object> datos) {
		return false;
	}

	@Override
	public float alto() {
		return 0f;
	}

	@Override
	public List<Muestra> leyenda(Map<String, Object> datos) {
		return List.of();
	}

	@Override
	public List<String> notas(Map<String, Object> datos) {
		return List.of();
	}

	@Override
	public void dibujar(DibujanteGraficas d, Map<String, Object> datos) {
		// Pendiente.
	}
}
