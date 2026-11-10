package com.ipn.mx.fst.service.graficas;

import java.util.List;
import java.util.Map;

// G4 · Barra apilada en el PDF. Pendiente (lo hace m1): mientras no aplica, no se dibuja ni tiene marcador.
// Al implementarla, aplica debe decir cuándo hay datos (ver ESPECIFICACION.md, §3 G4 y «Cómo agregar una gráfica al PDF»).
public final class G4ApiladaPdf implements GraficaPdf {

	@Override
	public String id() {
		return "g4";
	}

	@Override
	public String titulo() {
		return "Reparto de la duración analizada · Día 2";
	}

	@Override
	public String subtitulo() {
		return "Media por espécimen de cada categoría; la suma es la duración analizada.";
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
