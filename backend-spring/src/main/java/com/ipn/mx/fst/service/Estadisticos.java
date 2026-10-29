package com.ipn.mx.fst.service;

import java.util.List;

// Media, varianza y desviación estándar MUESTRALES (n − 1) de los segundos de una conducta por espécimen. Los usan
// /results/groups y los reportes CSV y XLSX, para que la pantalla y los archivos digan lo mismo. Sin especímenes los
// tres valores son null; con uno solo, la varianza y la desviación valen 0.
public final class Estadisticos {

	private Estadisticos() {
	}

	public static Double media(List<Long> valores) {
		if (valores.isEmpty()) {
			return null;
		}
		double suma = 0;
		for (long valor : valores) {
			suma += valor;
		}
		return suma / valores.size();
	}

	public static Double varianza(List<Long> valores) {
		if (valores.isEmpty()) {
			return null;
		}
		if (valores.size() == 1) {
			return 0.0;
		}
		double media = media(valores);
		double cuadrados = 0;
		for (long valor : valores) {
			double diferencia = valor - media;
			cuadrados += diferencia * diferencia;
		}
		return cuadrados / (valores.size() - 1);
	}

	public static Double desviacion(List<Long> valores) {
		Double varianza = varianza(valores);
		return varianza == null ? null : Math.sqrt(varianza);
	}

	// Un decimal, como se muestra en la pantalla y en los reportes.
	public static double redondear(double valor) {
		return Math.round(valor * 10.0) / 10.0;
	}

	// Null se queda null.
	public static Double redondear(Double valor) {
		return valor == null ? null : redondear(valor.doubleValue());
	}

}
