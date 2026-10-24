package com.ipn.mx.fst.domain.valores;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio etapa_analisis, en el orden en que las recorre el worker.
public enum EtapaAnalisis implements ValorDeBase {

	PREPROCESAMIENTO("preprocesamiento"),
	CILINDROS("cilindros"),
	CLASIFICACION("clasificacion");

	private final String valor;

	EtapaAnalisis(String valor) {
		this.valor = valor;
	}

	@Override
	@JsonValue
	public String valor() {
		return valor;
	}

	@JsonCreator
	public static EtapaAnalisis desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "etapa_analisis");
	}

}
