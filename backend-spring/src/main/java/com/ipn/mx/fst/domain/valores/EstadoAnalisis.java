package com.ipn.mx.fst.domain.valores;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio estado_analisis.
public enum EstadoAnalisis implements ValorDeBase {

	EN_COLA("en cola"),
	PROCESANDO("procesando"),
	EN_PAUSA("en pausa"),
	COMPLETADO("completado"),
	ERROR("error"),
	CANCELADO("cancelado");

	private final String valor;

	EstadoAnalisis(String valor) {
		this.valor = valor;
	}

	@Override
	@JsonValue
	public String valor() {
		return valor;
	}

	@JsonCreator
	public static EstadoAnalisis desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "estado_analisis");
	}

}
