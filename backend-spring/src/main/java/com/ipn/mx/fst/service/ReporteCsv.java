// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Los dos records anidados de TablaDeReporte que se usan aquí.
import com.ipn.mx.fst.service.TablaDeReporte.Estadistico;
import com.ipn.mx.fst.service.TablaDeReporte.FilaDeReporte;
// StandardCharsets: para codificar el texto en UTF-8 de forma explícita.
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
// Locale: la convención regional de los números (ver la nota de Locale.ROOT abajo).
import java.util.Locale;
// ToDoubleFunction: una función que recibe un objeto y devuelve un double. Permite pasar "qué campo leer".
import java.util.function.ToDoubleFunction;

// El reporte CSV de un grupo o de una tanda (D-99). UTF-8 con BOM para que Excel muestre los acentos, coma como separador,
// \r\n al final de cada línea y comillas como pide RFC 4180. Es una clase pura: arma los bytes desde una TablaDeReporte.
//
// Fórmulas (encargo 40): Excel toma como fórmula una celda de un CSV que empieza con =, +, -, @, tabulador o retorno, y el
// nombre de un grupo lo escribe el usuario. Por eso toda celda de TEXTO lleva un apóstrofo delante si empieza así; las
// celdas numéricas (segundos y estadísticos) se escriben tal cual, y el código las distingue por cómo se arman (texto() y
// numero()), no por su contenido. El apóstrofo va antes de las comillas de RFC 4180. El XLSX no necesita esto: ReporteXlsx
// guarda los textos como texto y nunca escribe fórmulas.
//
// El segundo párrafo describe un problema de seguridad real, la INYECCIÓN DE FÓRMULAS EN CSV. Si un
// investigador llama a su grupo "=1+1", Excel no lo muestra como texto: lo evalúa. Con fórmulas más
// elaboradas se puede llegar a ejecutar cosas en la máquina de quien abre el archivo. El apóstrofo
// delante obliga a Excel a tratar la celda como texto.
// Y es importante que la distinción sea por CÓMO se arma la celda y no por su contenido: un número
// negativo empieza por "-" y no debe llevar apóstrofo, porque entonces dejaría de ser un número.
// Qué es RFC 4180: la norma que define el formato CSV. De ahí salen la coma, el \r\n y el escape de
// comillas de abajo.
public final class ReporteCsv {

	// El BOM (Byte Order Mark) de UTF-8: tres bytes invisibles al principio del archivo. Sin ellos, Excel
	// en Windows supone que el CSV está en la codificación local y muestra "Espécimen" como "EspÃ©cimen".
	// Es el truco estándar para que Excel reconozca UTF-8, y no afecta a ningún otro programa.
	private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
	// Los caracteres con los que Excel empieza a interpretar una fórmula.
	private static final String INICIOS_DE_FORMULA = "=+-@\t\r";

	// Impide instanciarla: es una caja de métodos estáticos.
	private ReporteCsv() {
	}

	public static byte[] armar(TablaDeReporte tabla) {
		// StringBuilder y no concatenar textos con +: con cientos de filas, el + crearía un texto nuevo
		// en cada paso y sería mucho más lento.
		StringBuilder texto = new StringBuilder();
		// La fila de encabezados, con los textos de TablaDeReporte (compartidos con el XLSX).
		linea(texto, TablaDeReporte.ENCABEZADO.stream().map(ReporteCsv::texto).toList());

		// Una fila por espécimen y minuto.
		for (FilaDeReporte fila : tabla.filas()) {
			List<String> celdas = new ArrayList<>();
			// Las cuatro primeras son TEXTO y pasan por texto(), que las protege de la inyección.
			// El nombre del grupo lo escribe el usuario: es justo el que puede traer un "=" delante.
			celdas.add(texto(tabla.grupo()));
			celdas.add(texto(fila.tanda()));
			celdas.add(texto(fila.especimen()));
			celdas.add(texto(fila.tiempo()));
			// Los segundos son NÚMEROS y se escriben tal cual, sin apóstrofo: con él, Excel los leería
			// como texto y no se podrían sumar.
			for (Long segundos : fila.segundos()) {
				celdas.add(String.valueOf(segundos));
			}
			linea(texto, celdas);
		}

		// Las tres filas de resumen. Estadistico::media es una referencia a método: se le pasa QUÉ campo
		// leer, para no repetir tres veces el mismo bucle cambiando una palabra.
		estadistico(texto, TablaDeReporte.ETIQUETA_MEDIA, tabla.estadisticos(), Estadistico::media);
		estadistico(texto, TablaDeReporte.ETIQUETA_DE, tabla.estadisticos(), Estadistico::de);
		estadistico(texto, TablaDeReporte.ETIQUETA_VARIANZA, tabla.estadisticos(), Estadistico::varianza);

		// Qué especímenes revisó una persona (ver TablaDeReporte.textoDeRevision).
		textoFinal(texto, TablaDeReporte.ETIQUETA_REVISION, tabla.textoDeRevision());
		// El aviso de que el grupo mezcla niveles de clasificación, solo si toca.
		if (tabla.nivelMixto()) {
			textoFinal(texto, TablaDeReporte.ETIQUETA_AVISO, TablaDeReporte.TEXTO_AVISO);
		}
		// Modo desarrollador: con qué modelo se hizo cada análisis (la lista viene vacía fuera de él).
		// Al venir vacía, el bucle no da ninguna vuelta: no hace falta comprobar el modo aquí.
		for (String modelo : tabla.modelos()) {
			textoFinal(texto, TablaDeReporte.ETIQUETA_MODELO, modelo);
		}

		// Se pasa el texto a bytes en UTF-8 y se le pega el BOM delante.
		byte[] contenido = texto.toString().getBytes(StandardCharsets.UTF_8);
		// Un arreglo del tamaño justo y dos copias: es la forma de concatenar arreglos de bytes en Java
		// sin bibliotecas de por medio.
		byte[] salida = new byte[BOM.length + contenido.length];
		System.arraycopy(BOM, 0, salida, 0, BOM.length);
		System.arraycopy(contenido, 0, salida, BOM.length, contenido.length);
		return salida;
	}

	// Grupo, Tanda y Tiempo vacíos; la etiqueta va en la columna Espécimen y un decimal por conducta (números).
	//
	// El cuarto parámetro es la función que dice qué campo del Estadistico leer. Gracias a él, este
	// método sirve para la media, la desviación y la varianza sin repetirse.
	private static void estadistico(StringBuilder texto, String etiqueta, List<Estadistico> estadisticos,
			ToDoubleFunction<Estadistico> valor) {
		// Las tres primeras columnas van vacías para que la etiqueta caiga bajo "Espécimen" y los números
		// queden alineados con las columnas de conducta de arriba.
		// List.of(...) es inmutable, de ahí el new ArrayList<>(...) para poder añadir después.
		List<String> celdas = new ArrayList<>(List.of(texto(""), texto(""), texto(etiqueta), texto("")));
		for (Estadistico e : estadisticos) {
			// Locale.ROOT es importante: con la configuración española o mexicana, %.1f escribiría "12,5"
			// con coma decimal, y la coma es el separador de columnas del CSV. El archivo se rompería.
			// Locale.ROOT fuerza el punto decimal.
			celdas.add(String.format(Locale.ROOT, "%.1f", valor.applyAsDouble(e)));
		}
		linea(texto, celdas);
	}

	// Revisión humana, Aviso y Modelo: la etiqueta en la columna Espécimen y su texto en la de Tiempo.
	// Las tres filas tienen la misma forma, de ahí el método común.
	private static void textoFinal(StringBuilder texto, String etiqueta, String contenido) {
		linea(texto, List.of(texto(""), texto(""), texto(etiqueta), texto(contenido)));
	}

	// Las celdas ya vienen listas (texto() o números): aquí solo se unen.
	// \r\n y no \n: lo pide RFC 4180 y es lo que espera Excel en Windows.
	private static void linea(StringBuilder texto, List<String> celdas) {
		texto.append(String.join(",", celdas)).append("\r\n");
	}

	// Una celda de texto: con el apóstrofo si Excel la tomaría como fórmula y luego, si hace falta, entre comillas.
	//
	// La condición se lee al revés de lo que parece: se deja el valor TAL CUAL si está vacío o si su
	// primer carácter NO está en la lista de peligrosos (indexOf < 0 significa "no lo encontré").
	// En cualquier otro caso se le antepone el apóstrofo.
	// El orden importa, como dice el comentario de la clase: primero el apóstrofo, y después las comillas
	// de RFC 4180 por fuera.
	static String texto(String valor) {
		return celda(valor.isEmpty() || INICIOS_DE_FORMULA.indexOf(valor.charAt(0)) < 0 ? valor : "'" + valor);
	}

	// Entre comillas si lleva coma, comilla o salto de línea; las comillas de dentro se duplican.
	//
	// Es la regla de escape de RFC 4180. Sin ella, un tratamiento llamado "salina, 0.9%" partiría la fila
	// en dos columnas y desplazaría todo lo demás.
	static String celda(String valor) {
		// Camino rápido: si no tiene ninguno de los cuatro caracteres problemáticos, se devuelve igual.
		// Entrecomillar todo también sería válido según la norma, pero ensucia el archivo al leerlo.
		if (valor.indexOf(',') < 0 && valor.indexOf('"') < 0 && valor.indexOf('\n') < 0 && valor.indexOf('\r') < 0) {
			return valor;
		}
		// Las comillas de dentro se duplican (" pasa a ""): así es como la norma dice que se escapan.
		return '"' + valor.replace("\"", "\"\"") + '"';
	}

}
