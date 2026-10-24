package com.ipn.mx.fst.domain.valores;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio origen_segundo. MAQUINA es lo que escribe el worker; las otras tres, una persona al revisar.
public enum OrigenSegundo implements ValorDeBase {

	MAQUINA("maquina"),
	CONFIRMADA("confirmada"),
	CORREGIDA("corregida"),
	DUDOSA("dudosa");

	private final String valor;

	OrigenSegundo(String valor) {
		this.valor = valor;
	}

	@Override
	@JsonValue
	public String valor() {
		return valor;
	}

	@JsonCreator
	public static OrigenSegundo desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "origen_segundo");
	}

}
