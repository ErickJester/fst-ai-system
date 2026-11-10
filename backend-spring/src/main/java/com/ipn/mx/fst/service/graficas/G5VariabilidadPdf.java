// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

import java.util.List;
import java.util.Map;

// G5 · Variabilidad entre animales en el PDF. Pendiente (lo hace m2): mientras no aplica, no se dibuja ni tiene marcador.
// Al implementarla, aplica debe decir cuándo hay datos (ver ESPECIFICACION.md, §3 G5 y «Cómo agregar una gráfica al PDF»).
// Mide la variación entre animales de un grupo, no entre analistas: la nota va en la gráfica.
//
// Esqueleto, igual que G3: ver sus comentarios para el porqué del patrón.
// Esa última línea previene un malentendido real: alguien podría leer "variabilidad" como "qué tan de
// acuerdo están los que miden". No es eso: es qué tan parecidos se comportan entre sí los animales de un
// mismo grupo. Por eso la aclaración tiene que salir impresa en la propia gráfica, no solo aquí.
// El coeficiente de variación (DE ÷ media) lo calcula GraficasDeResultados, no esta clase.
public final class G5VariabilidadPdf implements GraficaPdf {

	@Override
	public String id() {
		return "g5";
	}

	@Override
	public String titulo() {
		return "Variabilidad entre animales · Día 2";
	}

	// El subtítulo deja clara la fórmula, para que nadie tenga que adivinar qué es el CV.
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
