// Carpeta lógica de esta interfaz: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

import java.util.List;
import java.util.Map;

// Lo que todas las gráficas del PDF tienen en común. Cada una vive en su archivo y se registra en SeccionGraficasPdf.
// Los datos son los del mapa de GraficasDeResultados (los mismos que la pantalla): una gráfica no calcula estadísticos.
// Una gráfica que no aplica (aplica = false) no se dibuja, ni su marcador, ni su sección de leyenda.
//
// Es el contrato que permite añadir una gráfica nueva sin tocar nada más: se escribe una clase que
// implemente esta interfaz y se registra en SeccionGraficasPdf. El resto (el orden, los marcadores, el
// espacio, las notas) lo resuelve esa clase para todas por igual.
// "Una gráfica no calcula estadísticos" es la regla de siempre en el proyecto: las medias y las
// desviaciones vienen ya hechas de GraficasDeResultados, que es el único que las calcula, para la web y
// para el papel. Aquí solo se dibuja.
// El mecanismo de "aplica" es lo que permite tener en el código las seis gráficas del plan aunque cuatro
// estén pendientes: devuelven false y el PDF sale como si no existieran.
public interface GraficaPdf {

	// Dos notas que varias gráficas comparten, en constantes para que el texto sea idéntico en todas.
	//
	// La primera es una advertencia estadística importante: una barra de error que se solapa con otra NO
	// demuestra que no haya diferencia, y una que no se solapa no demuestra que la haya. La desviación
	// estándar describe la dispersión de los datos, no hace una prueba de hipótesis.
	String NOTA_DE = "La barra ± DE muestra la dispersión entre especímenes; no indica si una diferencia es significativa.";
	// La segunda es de honestidad: estos números salen de lo que midió el modelo, cuya exactitud todavía
	// no se ha validado contra observadores humanos. Decirlo en cada gráfica evita que alguien las use
	// como si fueran mediciones verificadas.
	String NOTA_ORIGEN = "Los datos son sobre la salida del modelo; su exactitud todavía no está medida.";

	// Un renglón de la leyenda: el texto y el estilo con el que se dibuja la muestra.
	// El estilo es el mismo objeto con el que se dibuja la serie, así que la muestra de la leyenda y la
	// barra o la línea no pueden salir distintas.
	record Muestra(String texto, EstiloPdf estilo) {
	}

	// «g1», «g2»…: el id del marcador y del destino interno.
	// Un texto corto y sin espacios, porque acaba siendo el nombre de un destino dentro del PDF.
	String id();

	// El título que se imprime encima de la gráfica y que aparece en el marcador.
	String titulo();

	// Una línea debajo del título que explica qué se está viendo.
	String subtitulo();

	// Si hay algo que dibujar con estos datos (al menos un grupo con datos, o un minuto con datos).
	// Devolver false es lo que hace desaparecer la gráfica entera, con su marcador y su leyenda.
	boolean aplica(Map<String, Object> datos);

	// Altura del dibujo en puntos, sin título, leyenda ni notas.
	//
	// Un punto es 1/72 de pulgada, la unidad de medida de los PDF.
	// La altura la decide cada gráfica porque unas necesitan más alto que otras; quien la coloca
	// (SeccionGraficasPdf) la usa para saber si cabe en la página o hay que saltar a la siguiente.
	float alto();

	// Los renglones de la leyenda. Se calculan a partir de los datos porque dependen de ellos: la leyenda
	// solo debe nombrar los grupos que de verdad salen en la gráfica.
	List<Muestra> leyenda(Map<String, Object> datos);

	// Las notas que van debajo del dibujo: las de la gráfica y las de cada grupo (sin datos, mixto, pendientes).
	// Las segundas son las que avisan de que un grupo no se pudo dibujar y por qué, en vez de omitirlo en
	// silencio y dejar que alguien crea que ese grupo no existe.
	List<String> notas(Map<String, Object> datos);

	// Dibuja en las coordenadas locales de d (origen arriba a la izquierda, ancho d.ancho(), alto d.alto()).
	//
	// "Coordenadas locales" es la clave: la gráfica dibuja como si estuviera sola en un lienzo que empieza
	// en (0,0), y DibujanteGraficas traduce eso a la posición real de la página. Así una gráfica no tiene
	// que saber dónde la van a colocar ni en qué página acabará.
	// El origen arriba a la izquierda es al revés que en un PDF (donde está abajo a la izquierda), porque
	// es como se piensa al dibujar; el dibujante hace la conversión.
	void dibujar(DibujanteGraficas d, Map<String, Object> datos);
}
