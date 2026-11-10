// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

import java.util.List;
import java.util.Map;

// G3 · Barras por minuto en el PDF. Pendiente (lo hace m1): mientras no aplica, no se dibuja ni tiene marcador.
// Al implementarla, aplica debe decir cuándo hay datos (ver ESPECIFICACION.md, §3 G3 y «Cómo agregar una gráfica al PDF»).
//
// Es un ESQUELETO: la clase existe y está registrada en SeccionGraficasPdf, pero aplica() devuelve false,
// así que el PDF sale exactamente como si no existiera.
// Tenerla ya escrita tiene dos ventajas: el reparto del trabajo queda claro (m1 y m2 saben qué les toca) y
// el día que se implemente no hay que tocar SeccionGraficasPdf ni el orden de los marcadores.
// Las cuatro gráficas pendientes (G3 a G6) son idénticas salvo el id, el título y el subtítulo.
// final: no está pensada para heredarse.
public final class G3BarrasPorMinutoPdf implements GraficaPdf {

	// El id del marcador y del destino interno dentro del PDF.
	@Override
	public String id() {
		return "g3";
	}

	// El título, ya escrito, para que al implementarla no haya que inventarlo.
	@Override
	public String titulo() {
		return "Barras por minuto · Día 2";
	}

	@Override
	public String subtitulo() {
		return "Media por minuto de cada grupo, con su barra ± DE.";
	}

	// El false es lo que la mantiene invisible. Al implementarla, aquí va la comprobación de que haya al
	// menos un minuto con datos.
	@Override
	public boolean aplica(Map<String, Object> datos) {
		return false;
	}

	// Altura 0: no ocupa espacio mientras no aplica.
	@Override
	public float alto() {
		return 0f;
	}

	// Listas vacías y no null: así, si algún día se recorrieran sin comprobar aplica(), el bucle
	// simplemente no daría vueltas en vez de tronar.
	@Override
	public List<Muestra> leyenda(Map<String, Object> datos) {
		return List.of();
	}

	@Override
	public List<String> notas(Map<String, Object> datos) {
		return List.of();
	}

	// Cuerpo vacío: nunca se llama, porque SeccionGraficasPdf solo dibuja las que aplican.
	@Override
	public void dibujar(DibujanteGraficas d, Map<String, Object> datos) {
		// Pendiente.
	}
}
