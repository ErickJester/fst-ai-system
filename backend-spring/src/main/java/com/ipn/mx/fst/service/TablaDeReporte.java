// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

import java.util.List;
// Collectors: recoger el resultado de un stream (aquí, juntar textos).
import java.util.stream.Collectors;
// Stream: para encadenar dos listas en una.
import java.util.stream.Stream;

// Lo que llevan los reportes CSV y XLSX de un grupo completo o de una tanda: una fila por espécimen y minuto, la media, la
// desviación estándar y la varianza de cada conducta (sobre los totales por espécimen) y qué especímenes tuvieron revisión
// humana. ResultadosService la arma; ReporteCsv y ReporteXlsx solo la escriben, con los mismos textos. En modo desarrollador
// trae además, en modelos, con qué modelo se hizo cada análisis (vacío si no).
//
// Es la pieza que garantiza que el CSV y el XLSX digan EXACTAMENTE lo mismo. Los dos reciben esta misma
// estructura, con los mismos números ya calculados y los mismos textos; lo único que cambia es cómo los
// escriben. Si cada uno calculara por su cuenta, bastaría un redondeo distinto para que no cuadraran.
// Un record es una clase de solo datos: Java genera el constructor, los getters (que se llaman grupo(),
// filas(), ... sin "get"), equals, hashCode y toString. Sus campos son inmutables.
public record TablaDeReporte(String grupo, List<FilaDeReporte> filas, List<Estadistico> estadisticos, boolean nivelMixto,
		List<Revisado> revisados, List<String> modelos) {

	// Sin modelos: lo normal, fuera del modo desarrollador.
	//
	// Es un constructor compacto que delega en el principal con List.of() (una lista vacía inmutable).
	// Así, quien no esté en modo desarrollador no tiene que escribir el argumento, y nunca llega null:
	// un bucle sobre una lista vacía simplemente no da vueltas, mientras que sobre null tronaría.
	public TablaDeReporte(String grupo, List<FilaDeReporte> filas, List<Estadistico> estadisticos, boolean nivelMixto,
			List<Revisado> revisados) {
		this(grupo, filas, estadisticos, nivelMixto, revisados, List.of());
	}

	// D-99: las columnas del reporte, en este orden («Sin ver» al final).
	//
	// Son los textos que ve el usuario, en español y con acentos. Están aquí, en una constante compartida,
	// y no escritos en cada reporte: así el CSV y el XLSX no pueden acabar con encabezados distintos.
	public static final List<String> ENCABEZADO = List.of("Grupo", "Tanda", "Espécimen", "Tiempo (min:seg)", "Nado",
			"Escalamiento", "Inmovilidad", "Conducta activa", "Sin ver");

	// Las conductas de las últimas cuatro columnas, con su valor en la base.
	//
	// Ojo al detalle: son los textos del dominio tipo_conducta, no los del encabezado. "nado activo" en la
	// base se muestra como "Nado". Esta lista es la que se usa para BUSCAR los datos; ENCABEZADO, para
	// escribirlos. Las dos tienen que ir en el mismo orden, y de ahí que estén juntas.
	public static final List<String> CONDUCTAS = List.of("nado activo", "escalamiento", "inmovilidad", "conducta activa");

	// La quinta columna: los segundos con conducta NULL (no se vio al animal o la revisión los marcó como no visibles).
	//
	// Es la columna que convierte un null en un dato visible. Sin ella, los segundos sin conducta
	// desaparecerían del reporte y los minutos no sumarían 60, lo que haría dudar de todo lo demás.
	public static final String SIN_VER = "sin ver";

	// Las cinco columnas de segundos, en el orden del encabezado.
	// Stream.concat encadena las cuatro conductas con "sin ver" y toList() lo cierra en una lista
	// inmutable. Se construye a partir de las otras dos constantes para que no puedan descuadrarse.
	public static final List<String> COLUMNAS = Stream.concat(CONDUCTAS.stream(), Stream.of(SIN_VER)).toList();

	// Las etiquetas de las filas de resumen que van debajo de la tabla. Igual que el encabezado: en un
	// solo sitio, para que los dos formatos las escriban idénticas.
	public static final String ETIQUETA_MEDIA = "Media";
	public static final String ETIQUETA_DE = "Desviación estándar";
	public static final String ETIQUETA_VARIANZA = "Varianza";
	public static final String ETIQUETA_REVISION = "Revisión humana";
	public static final String ETIQUETA_AVISO = "Aviso";
	// Solo en modo desarrollador: una línea por análisis con el modelo con el que se hizo («Tanda A: fst_cnn_seg.pt · red 5.0.0 · …»).
	public static final String ETIQUETA_MODELO = "Modelo";
	// El aviso que se imprime cuando nivelMixto es true. Es importante: significa que el grupo mezcla
	// tandas analizadas con detalle "preciso" y otras "agrupado" (ver NivelClasifAnalisis), y entonces sus
	// columnas no son comparables entre sí. El reporte lo advierte en vez de callarlo.
	public static final String TEXTO_AVISO = "Mezcla tandas de clasificación precisa y agrupada";

	// segundos va en el orden de COLUMNAS.
	//
	// Una fila de la tabla: un espécimen en un minuto, con los segundos de cada una de las cinco columnas.
	// La lista es posicional y no un mapa: es más compacta, y el orden está fijado por COLUMNAS.
	public record FilaDeReporte(String tanda, int cilindro, int minuto, List<Long> segundos) {

		// Un record puede traer métodos propios además de sus datos. Este arma el texto de la columna
		// "Espécimen" a partir del número de cilindro, que es lo único que identifica a un espécimen
		// (ver la entidad Especimen).
		public String especimen() {
			return "Espécimen " + cilindro;
		}

		// Mismo formato que el PDF: el minuto 1 es 0:00-1:00.
		//
		// El menos uno es lo que traduce de "minuto 1, 2, 3" a "0:00-1:00, 1:00-2:00". La nota de que es
		// el mismo formato del PDF es la razón de que este método esté aquí y no en cada reporte.
		public String tiempo() {
			return (minuto - 1) + ":00-" + minuto + ":00";
		}
	}

	// Una conducta, con un decimal. Nunca null: el reporte solo existe si hay al menos un espécimen.
	//
	// Por eso los campos son double primitivos y no Double: aquí el null no puede darse. Compárese con
	// Estadisticos, que sí devuelve Double porque trabaja con listas que pueden venir vacías. El filtro
	// que garantiza que haya datos está antes, en quien arma la tabla.
	public record Estadistico(double media, double de, double varianza) {
	}

	// Un espécimen que tuvo revisión humana. Los dos campos juntos lo identifican dentro del grupo.
	public record Revisado(String tanda, int cilindro) {
	}

	// «Sí: Tanda A · Espécimen 2; Tanda B · Espécimen 1» o «No».
	//
	// Es el texto de la fila "Revisión humana" del reporte. Que sea un método del record, y no algo que
	// arme cada formato, es la misma idea de siempre: un solo sitio donde se decide cómo se escribe.
	// Esta línea es la que da transparencia al resultado: dice qué especímenes tocó una persona y cuáles
	// quedaron tal como los dejó el modelo (ver FilaEspecimenRevisado).
	public String textoDeRevision() {
		if (revisados.isEmpty()) {
			return "No";
		}
		// map convierte cada Revisado en su texto y joining los une con "; ".
		return "Sí: " + revisados.stream().map(r -> "Tanda " + r.tanda() + " · Espécimen " + r.cilindro())
				.collect(Collectors.joining("; "));
	}

}
