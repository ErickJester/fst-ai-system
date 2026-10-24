package com.ipn.mx.fst.domain.valores;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio tipo_notificacion: las seis de la guía. Sin acentos, como en la base.
public enum TipoNotificacion implements ValorDeBase {

	ANALISIS_COMPLETADO("analisis completado"),
	ERROR_DEL_PIPELINE("error del pipeline"),
	ANALISIS_EN_PAUSA("analisis en pausa"),
	ANALISIS_CANCELADO("analisis cancelado"),
	ALERTA_DE_DISCO("alerta de disco"),
	VIDEO_BORRADO("video borrado");

	private final String valor;

	TipoNotificacion(String valor) {
		this.valor = valor;
	}

	@Override
	@JsonValue
	public String valor() {
		return valor;
	}

	@JsonCreator
	public static TipoNotificacion desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "tipo_notificacion");
	}

}
