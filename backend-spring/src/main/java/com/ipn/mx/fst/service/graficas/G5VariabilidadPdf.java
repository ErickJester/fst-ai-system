package com.ipn.mx.fst.service.graficas;

import java.util.List;
import java.util.Map;

// G5 · Variabilidad entre animales en el PDF. Pendiente (lo hace m2): mientras no aplica, no se dibuja ni tiene marcador.
// Al implementarla, aplica debe decir cuándo hay datos (ver ESPECIFICACION.md, §3 G5 y «Cómo agregar una gráfica al PDF»).
// Mide la variación entre animales de un grupo, no entre analistas: la nota va en la gráfica.
public final class G5VariabilidadPdf implements GraficaPdf {

	@Override
	public String id() {
		return "g5";
	}

	@Override
	public String titulo() {
		return "Variabilidad entre animales · Día 2";
	}

	@Override
	public String subtitulo() {
		return "Coeficiente de variación (DE ÷ media) por grupo y conducta.";
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
