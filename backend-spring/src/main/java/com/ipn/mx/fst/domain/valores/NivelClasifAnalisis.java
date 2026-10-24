package com.ipn.mx.fst.domain.valores;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio nivel_clasif_analisis.
public enum NivelClasifAnalisis implements ValorDeBase {

	PRECISO("preciso"),
	AGRUPADO("agrupado");

	private final String valor;

	NivelClasifAnalisis(String valor) {
		this.valor = valor;
	}

	@Override
	@JsonValue
	public String valor() {
		return valor;
	}

	@JsonCreator
	public static NivelClasifAnalisis desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "nivel_clasif_analisis");
	}

}
