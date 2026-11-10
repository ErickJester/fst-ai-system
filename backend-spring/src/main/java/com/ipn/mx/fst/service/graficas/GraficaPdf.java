package com.ipn.mx.fst.service.graficas;

import java.util.List;
import java.util.Map;

// Lo que todas las gráficas del PDF tienen en común. Cada una vive en su archivo y se registra en SeccionGraficasPdf.
// Los datos son los del mapa de GraficasDeResultados (los mismos que la pantalla): una gráfica no calcula estadísticos.
// Una gráfica que no aplica (aplica = false) no se dibuja, ni su marcador, ni su sección de leyenda.
public interface GraficaPdf {

	String NOTA_DE = "La barra ± DE muestra la dispersión entre especímenes; no indica si una diferencia es significativa.";
	String NOTA_ORIGEN = "Los datos son sobre la salida del modelo; su exactitud todavía no está medida.";

	// Un renglón de la leyenda: el texto y el estilo con el que se dibuja la muestra.
	record Muestra(String texto, EstiloPdf estilo) {
	}

	// «g1», «g2»…: el id del marcador y del destino interno.
	String id();

	String titulo();

	String subtitulo();

	// Si hay algo que dibujar con estos datos (al menos un grupo con datos, o un minuto con datos).
	boolean aplica(Map<String, Object> datos);

	// Altura del dibujo en puntos, sin título, leyenda ni notas.
	float alto();

	List<Muestra> leyenda(Map<String, Object> datos);

	// Las notas que van debajo del dibujo: las de la gráfica y las de cada grupo (sin datos, mixto, pendientes).
	List<String> notas(Map<String, Object> datos);

	// Dibuja en las coordenadas locales de d (origen arriba a la izquierda, ancho d.ancho(), alto d.alto()).
	void dibujar(DibujanteGraficas d, Map<String, Object> datos);
}
