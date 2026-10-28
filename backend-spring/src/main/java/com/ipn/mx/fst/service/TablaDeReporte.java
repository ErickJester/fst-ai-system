package com.ipn.mx.fst.service;

import java.util.List;
import java.util.stream.Collectors;

// Lo que llevan los reportes CSV y XLSX de un grupo completo o de una tanda: una fila por espécimen y minuto, la media, la
// desviación estándar y la varianza de cada conducta (sobre los totales por espécimen) y qué especímenes tuvieron revisión
// humana. ResultadosService la arma; ReporteCsv y ReporteXlsx solo la escriben, con los mismos textos.
public record TablaDeReporte(String grupo, List<FilaDeReporte> filas, List<Estadistico> estadisticos, boolean nivelMixto,
		List<Revisado> revisados) {

	// D-99: las columnas del reporte, en este orden.
	public static final List<String> ENCABEZADO = List.of("Grupo", "Tanda", "Espécimen", "Tiempo (min:seg)", "Nado",
			"Escalamiento", "Inmovilidad", "Conducta activa");

	// Las conductas de las últimas cuatro columnas, con su valor en la base.
	public static final List<String> CONDUCTAS = List.of("nado activo", "escalamiento", "inmovilidad", "conducta activa");

	public static final String ETIQUETA_MEDIA = "Media";
	public static final String ETIQUETA_DE = "Desviación estándar";
	public static final String ETIQUETA_VARIANZA = "Varianza";
	public static final String ETIQUETA_REVISION = "Revisión humana";
	public static final String ETIQUETA_AVISO = "Aviso";
	public static final String TEXTO_AVISO = "Mezcla tandas de clasificación precisa y agrupada";

	// segundos va en el orden de CONDUCTAS.
	public record FilaDeReporte(String tanda, int cilindro, int minuto, List<Long> segundos) {

		public String especimen() {
			return "Espécimen " + cilindro;
		}

		// Mismo formato que el PDF: el minuto 1 es 0:00-1:00.
		public String tiempo() {
			return (minuto - 1) + ":00-" + minuto + ":00";
		}
	}

	// Una conducta, con un decimal. Nunca null: el reporte solo existe si hay al menos un espécimen.
	public record Estadistico(double media, double de, double varianza) {
	}

	public record Revisado(String tanda, int cilindro) {
	}

	// «Sí: Tanda A · Espécimen 2; Tanda B · Espécimen 1» o «No».
	public String textoDeRevision() {
		if (revisados.isEmpty()) {
			return "No";
		}
		return "Sí: " + revisados.stream().map(r -> "Tanda " + r.tanda() + " · Espécimen " + r.cilindro())
				.collect(Collectors.joining("; "));
	}

}
