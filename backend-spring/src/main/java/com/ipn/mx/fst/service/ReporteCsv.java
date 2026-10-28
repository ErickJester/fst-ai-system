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
public final class ReporteCsv {

	private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

	private ReporteCsv() {
	}

	public static byte[] armar(TablaDeReporte tabla) {
		StringBuilder texto = new StringBuilder();
		linea(texto, TablaDeReporte.ENCABEZADO);

		for (FilaDeReporte fila : tabla.filas()) {
			List<String> celdas = new ArrayList<>();
			celdas.add(tabla.grupo());
			celdas.add(fila.tanda());
			celdas.add(fila.especimen());
			celdas.add(fila.tiempo());
			for (Long segundos : fila.segundos()) {
				celdas.add(String.valueOf(segundos));
			}
			linea(texto, celdas);
		}

		estadistico(texto, TablaDeReporte.ETIQUETA_MEDIA, tabla.estadisticos(), Estadistico::media);
		estadistico(texto, TablaDeReporte.ETIQUETA_DE, tabla.estadisticos(), Estadistico::de);
		estadistico(texto, TablaDeReporte.ETIQUETA_VARIANZA, tabla.estadisticos(), Estadistico::varianza);

		linea(texto, List.of("", "", TablaDeReporte.ETIQUETA_REVISION, tabla.textoDeRevision()));
		if (tabla.nivelMixto()) {
			linea(texto, List.of("", "", TablaDeReporte.ETIQUETA_AVISO, TablaDeReporte.TEXTO_AVISO));
		}

		byte[] contenido = texto.toString().getBytes(StandardCharsets.UTF_8);
		byte[] salida = new byte[BOM.length + contenido.length];
		System.arraycopy(BOM, 0, salida, 0, BOM.length);
		System.arraycopy(contenido, 0, salida, BOM.length, contenido.length);
		return salida;
	}

	// Grupo, Tanda y Tiempo vacíos; la etiqueta va en la columna Espécimen y un decimal por conducta.
	private static void estadistico(StringBuilder texto, String etiqueta, List<Estadistico> estadisticos,
			ToDoubleFunction<Estadistico> valor) {
		List<String> celdas = new ArrayList<>(List.of("", "", etiqueta, ""));
		for (Estadistico e : estadisticos) {
			celdas.add(String.format(Locale.ROOT, "%.1f", valor.applyAsDouble(e)));
		}
		linea(texto, celdas);
	}

	private static void linea(StringBuilder texto, List<String> celdas) {
		for (int i = 0; i < celdas.size(); i++) {
			if (i > 0) {
				texto.append(',');
			}
			texto.append(celda(celdas.get(i)));
		}
		texto.append("\r\n");
	}

	// Entre comillas si lleva coma, comilla o salto de línea; las comillas de dentro se duplican.
	static String celda(String valor) {
		if (valor.indexOf(',') < 0 && valor.indexOf('"') < 0 && valor.indexOf('\n') < 0 && valor.indexOf('\r') < 0) {
			return valor;
		}
		return '"' + valor.replace("\"", "\"\"") + '"';
	}

}
