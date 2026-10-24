package com.ipn.mx.fst.domain.valores;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio tipo_grupo.
public enum TipoGrupo implements ValorDeBase {

	CONTROL("control"),
	REFERENCIA("referencia"),
	TRATAMIENTO_EXPERIMENTAL("tratamiento experimental");

	private final String valor;

	TipoGrupo(String valor) {
		this.valor = valor;
	}

	@Override
	@JsonValue
	public String valor() {
		return valor;
	}

	@JsonCreator
	public static TipoGrupo desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "tipo_grupo");
	}

}
