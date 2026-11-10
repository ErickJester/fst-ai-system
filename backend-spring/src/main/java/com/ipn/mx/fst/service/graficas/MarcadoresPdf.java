// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

// Las clases de iText que hacen falta: un trozo de texto, la tipografía, el párrafo, la acción de saltar
// a un destino, el marcador en sí y el escritor del PDF.
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfAction;
import com.itextpdf.text.pdf.PdfOutline;
import com.itextpdf.text.pdf.PdfWriter;

// Los marcadores del PDF (el índice del panel izquierdo del lector) y sus destinos internos. Cada sección tiene un
// destino con nombre, puesto en su título con Chunk.setLocalDestination; el marcador salta ahí. El orden de los
// marcadores es fijo: Resumen · Resultados por grupo · Gráficas (con las que aplican) · Tabla por espécimen ·
// Desglose por minuto · Modelo del análisis (solo en modo desarrollador). El PDF se abre con el panel desplegado.
//
// Para qué sirve: un reporte de resultados puede tener decenas de páginas, y sin índice hay que ir
// pasando hasta encontrar la tabla que interesa. Los marcadores lo convierten en un documento navegable.
// Cómo funciona, en dos piezas: un DESTINO es una marca con nombre puesta dentro del texto (el título de
// la sección), y un MARCADOR es una entrada del índice que salta a ese nombre. Hay que poner las dos.
public final class MarcadoresPdf {

	// Los nombres de los destinos, en constantes: los escribe quien pone el destino y quien crea el
	// marcador, y si no coincidieran el salto no llevaría a ninguna parte.
	public static final String RESUMEN = "resumen";
	public static final String RESULTADOS_GRUPO = "resultados-grupo";
	public static final String GRAFICAS = "graficas";
	public static final String TABLA_ESPECIMEN = "tabla-especimen";
	public static final String DESGLOSE_MINUTO = "desglose-minuto";
	public static final String MODELO = "modelo-analisis";

	private final PdfWriter writer;

	// Llamar justo después de PdfWriter.getInstance y antes de abrir el documento: el modo de página se fija ahí.
	//
	// El orden es obligatorio: setViewerPreferences escribe en la cabecera del PDF, y una vez abierto el
	// documento ya se está escribiendo contenido y la cabecera no se puede cambiar.
	public MarcadoresPdf(PdfWriter writer) {
		// PageModeUseOutlines hace que el lector abra el PDF con el panel de marcadores desplegado. Sin
		// esto el índice existe pero nadie lo ve, porque casi nadie abre ese panel a mano.
		writer.setViewerPreferences(PdfWriter.PageModeUseOutlines);
		this.writer = writer;
	}

	// Un marcador de primer nivel, que salta al destino con nombre. La raíz se pide al momento: antes de abrir el
	// documento todavía no existe.
	//
	// De ahí que este método NO sea static, al revés que los otros dos: necesita el writer guardado para
	// pedir la raíz en el momento de crear cada marcador y no antes.
	// gotoLocalPage con false significa "salta dentro de este mismo documento", no a otro archivo.
	public PdfOutline seccion(String titulo, String destino) {
		return new PdfOutline(writer.getRootOutline(), PdfAction.gotoLocalPage(destino, false), titulo);
	}

	// Un marcador bajo otro (por ejemplo, cada gráfica bajo «Gráficas»).
	//
	// La única diferencia con el de arriba es el primer argumento: en vez de la raíz, el marcador padre.
	// Así el índice tiene dos niveles y las seis gráficas no inundan la lista principal.
	// static porque recibe el padre y no necesita nada de la instancia.
	public static PdfOutline hijo(PdfOutline padre, String titulo, String destino) {
		return new PdfOutline(padre, PdfAction.gotoLocalPage(destino, false), titulo);
	}

	// Un encabezado de sección con su destino: el texto y la fuente, y el destino en el mismo párrafo.
	//
	// Es la OTRA mitad del mecanismo: aquí se planta la marca a la que saltará el marcador.
	// Tiene que ir en un Chunk (un trozo de texto) y no en el párrafo, porque el destino apunta a una
	// posición exacta dentro de la página, y el Chunk es la unidad más pequeña que iText sabe situar.
	public static Paragraph encabezado(String texto, Font fuente, String destino) {
		Chunk chunk = new Chunk(texto, fuente);
		chunk.setLocalDestination(destino);
		Paragraph parrafo = new Paragraph(chunk);
		// Un poco de aire debajo del título, para que no quede pegado a la tabla.
		parrafo.setSpacingAfter(6);
		return parrafo;
	}
}
