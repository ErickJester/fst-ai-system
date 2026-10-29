package com.ipn.mx.fst.domain.valores;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio formato_reporte.
public enum FormatoReporte implements ValorDeBase {

	CSV("CSV"),
	XLSX("XLSX"),
	PDF("PDF");

	private final String valor;

	FormatoReporte(String valor) {
		this.valor = valor;
	}

	@Override
	@JsonValue
	public String valor() {
		return valor;
	}

	@JsonCreator
	public static FormatoReporte desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "formato_reporte");
	}

}
