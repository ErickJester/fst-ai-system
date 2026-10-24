package com.ipn.mx.fst.domain.valores;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio tipo_conducta (D-102, no es tabla), en el orden del dominio de la base. La conducta de un segundo también
// puede ser null (no se ve al espécimen, el worker no decidió o el usuario lo marcó dudoso): eso no es una constante.
public enum TipoConducta implements ValorDeBase {

	NADO_ACTIVO("nado activo"),
	INMOVILIDAD("inmovilidad"),
	ESCALAMIENTO("escalamiento"),
	CONDUCTA_ACTIVA("conducta activa");

	private final String valor;

	TipoConducta(String valor) {
		this.valor = valor;
	}

	@Override
	@JsonValue
	public String valor() {
		return valor;
	}

	@JsonCreator
	public static TipoConducta desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "tipo_conducta");
	}

}
