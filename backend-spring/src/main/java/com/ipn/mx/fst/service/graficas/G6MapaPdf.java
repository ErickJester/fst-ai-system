package com.ipn.mx.fst.service.graficas;

import java.util.List;
import java.util.Map;

// G6 · Mapa de calor por minuto en el PDF. Pendiente (lo hace m2): mientras no aplica, no se dibuja ni tiene marcador.
// Al implementarla, aplica debe decir cuándo hay datos (ver ESPECIFICACION.md, §3 G6 y «Cómo agregar una gráfica al PDF»).
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
