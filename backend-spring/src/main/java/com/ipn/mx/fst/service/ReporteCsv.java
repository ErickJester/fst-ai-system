package com.ipn.mx.fst.service;

import com.ipn.mx.fst.service.TablaDeReporte.Estadistico;
import com.ipn.mx.fst.service.TablaDeReporte.FilaDeReporte;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

// El reporte CSV de un grupo o de una tanda (D-99). UTF-8 con BOM para que Excel muestre los acentos, coma como separador,
// \r\n al final de cada línea y comillas como pide RFC 4180. Es una clase pura: arma los bytes desde una TablaDeReporte.
//
// Fórmulas (encargo 40): Excel toma como fórmula una celda de un CSV que empieza con =, +, -, @, tabulador o retorno, y el
// nombre de un grupo lo escribe el usuario. Por eso toda celda de TEXTO lleva un apóstrofo delante si empieza así; las
// celdas numéricas (segundos y estadísticos) se escriben tal cual, y el código las distingue por cómo se arman (texto() y
// numero()), no por su contenido. El apóstrofo va antes de las comillas de RFC 4180. El XLSX no necesita esto: ReporteXlsx
// guarda los textos como texto y nunca escribe fórmulas.
public final class ReporteCsv {

	private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
	private static final String INICIOS_DE_FORMULA = "=+-@\t\r";

	private ReporteCsv() {
	}

	public static byte[] armar(TablaDeReporte tabla) {
		StringBuilder texto = new StringBuilder();
		linea(texto, TablaDeReporte.ENCABEZADO.stream().map(ReporteCsv::texto).toList());

		for (FilaDeReporte fila : tabla.filas()) {
			List<String> celdas = new ArrayList<>();
			celdas.add(texto(tabla.grupo()));
			celdas.add(texto(fila.tanda()));
			celdas.add(texto(fila.especimen()));
			celdas.add(texto(fila.tiempo()));
			for (Long segundos : fila.segundos()) {
				celdas.add(String.valueOf(segundos));
			}
			linea(texto, celdas);
		}

		estadistico(texto, TablaDeReporte.ETIQUETA_MEDIA, tabla.estadisticos(), Estadistico::media);
		estadistico(texto, TablaDeReporte.ETIQUETA_DE, tabla.estadisticos(), Estadistico::de);
		estadistico(texto, TablaDeReporte.ETIQUETA_VARIANZA, tabla.estadisticos(), Estadistico::varianza);

		textoFinal(texto, TablaDeReporte.ETIQUETA_REVISION, tabla.textoDeRevision());
		if (tabla.nivelMixto()) {
			textoFinal(texto, TablaDeReporte.ETIQUETA_AVISO, TablaDeReporte.TEXTO_AVISO);
		}

		byte[] contenido = texto.toString().getBytes(StandardCharsets.UTF_8);
		byte[] salida = new byte[BOM.length + contenido.length];
		System.arraycopy(BOM, 0, salida, 0, BOM.length);
		System.arraycopy(contenido, 0, salida, BOM.length, contenido.length);
		return salida;
	}

	// Grupo, Tanda y Tiempo vacíos; la etiqueta va en la columna Espécimen y un decimal por conducta (números).
	private static void estadistico(StringBuilder texto, String etiqueta, List<Estadistico> estadisticos,
			ToDoubleFunction<Estadistico> valor) {
		List<String> celdas = new ArrayList<>(List.of(texto(""), texto(""), texto(etiqueta), texto("")));
		for (Estadistico e : estadisticos) {
			celdas.add(String.format(Locale.ROOT, "%.1f", valor.applyAsDouble(e)));
		}
		linea(texto, celdas);
	}

	// Revisión humana y Aviso: la etiqueta en la columna Espécimen y su texto en la de Tiempo.
	private static void textoFinal(StringBuilder texto, String etiqueta, String contenido) {
		linea(texto, List.of(texto(""), texto(""), texto(etiqueta), texto(contenido)));
	}

	// Las celdas ya vienen listas (texto() o números): aquí solo se unen.
	private static void linea(StringBuilder texto, List<String> celdas) {
		texto.append(String.join(",", celdas)).append("\r\n");
	}

	// Una celda de texto: con el apóstrofo si Excel la tomaría como fórmula y luego, si hace falta, entre comillas.
	static String texto(String valor) {
		return celda(valor.isEmpty() || INICIOS_DE_FORMULA.indexOf(valor.charAt(0)) < 0 ? valor : "'" + valor);
	}

	// Entre comillas si lleva coma, comilla o salto de línea; las comillas de dentro se duplican.
	static String celda(String valor) {
		if (valor.indexOf(',') < 0 && valor.indexOf('"') < 0 && valor.indexOf('\n') < 0 && valor.indexOf('\r') < 0) {
			return valor;
		}
		return '"' + valor.replace("\"", "\"\"") + '"';
	}

}
