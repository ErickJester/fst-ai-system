package com.ipn.mx.fst.domain.valores;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio sesion_video. Sin acento a propósito: así está en la base y en el JSON ('Dia 1', 'Dia 2').
public enum SesionVideo implements ValorDeBase {

	DIA_1("Dia 1"),
	DIA_2("Dia 2");

	private final String valor;

	SesionVideo(String valor) {
		this.valor = valor;
	}

	@Override
	@JsonValue
	public String valor() {
		return valor;
	}

	@JsonCreator
	public static SesionVideo desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "sesion_video");
	}

}
