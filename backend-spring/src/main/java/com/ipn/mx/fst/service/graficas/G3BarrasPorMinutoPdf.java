package com.ipn.mx.fst.service.graficas;

import java.util.List;
import java.util.Map;

// G3 · Barras por minuto en el PDF. Pendiente (lo hace m1): mientras no aplica, no se dibuja ni tiene marcador.
// Al implementarla, aplica debe decir cuándo hay datos (ver ESPECIFICACION.md, §3 G3 y «Cómo agregar una gráfica al PDF»).
public final class G3BarrasPorMinutoPdf implements GraficaPdf {

	@Override
	public String id() {
		return "g3";
	}

	@Override
	public String titulo() {
		return "Barras por minuto · Día 2";
	}

	@Override
	public String subtitulo() {
		return "Media por minuto de cada grupo, con su barra ± DE.";
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
