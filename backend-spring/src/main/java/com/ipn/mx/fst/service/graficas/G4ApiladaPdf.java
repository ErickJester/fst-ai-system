// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

import java.util.List;
import java.util.Map;

// G4 · Barra apilada en el PDF. Pendiente (lo hace m1): mientras no aplica, no se dibuja ni tiene marcador.
// Al implementarla, aplica debe decir cuándo hay datos (ver ESPECIFICACION.md, §3 G4 y «Cómo agregar una gráfica al PDF»).
//
// Esqueleto, igual que G3: ver sus comentarios para el porqué del patrón.
// Esta es la gráfica donde «sin ver» importa más: al apilar las cinco categorías, la barra tiene que
// sumar la duración analizada entera, y sin incluir los segundos que no se vieron quedaría corta sin
// explicación (por eso GraficasDeResultados.CONDUCTAS lleva «sin ver» al final).
public final class G4ApiladaPdf implements GraficaPdf {

	@Override
	public String id() {
		return "g4";
	}

	@Override
	public String titulo() {
		return "Reparto de la duración analizada · Día 2";
	}

	// El subtítulo ya anuncia la propiedad clave: la suma de los trozos es la duración analizada.
	@Override
	public String subtitulo() {
		return "Media por espécimen de cada categoría; la suma es la duración analizada.";
	}

	// Mientras devuelva false, la gráfica no existe para el PDF.
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
