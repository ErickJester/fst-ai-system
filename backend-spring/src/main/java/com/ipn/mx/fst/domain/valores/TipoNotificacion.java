// Carpeta lógica de este enum: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// @JsonCreator: el método que usa Jackson para construir el valor al LEER un JSON.
import com.fasterxml.jackson.annotation.JsonCreator;
// @JsonValue: el método que usa Jackson para escribir el valor al SALIR en un JSON.
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio tipo_notificacion: las seis de la guía. Sin acentos, como en la base.
//
// Los seis motivos por los que el sistema avisa a un investigador. Las cuatro primeras son del ciclo de
// vida de un análisis; las dos últimas, del mantenimiento del disco.
// El patrón de este archivo está explicado a fondo en EstadoAnalisis.
public enum TipoNotificacion implements ValorDeBase {

	// Sin acento en "analisis": así está en la base, y aquí se copia el texto tal cual.
	// El análisis terminó bien y ya hay resultados que ver.
	ANALISIS_COMPLETADO("analisis completado"),
	// El worker falló procesando el video.
	ERROR_DEL_PIPELINE("error del pipeline"),
	// El análisis se detuvo y espera a que una persona ajuste algo (ver PausaService).
	ANALISIS_EN_PAUSA("analisis en pausa"),
	// Alguien canceló el análisis antes de que terminara.
	ANALISIS_CANCELADO("analisis cancelado"),
	// Queda poco espacio en disco (lo vigila UsoDeDisco).
	ALERTA_DE_DISCO("alerta de disco"),
	// Un video se borró por antigüedad. Los videos no se guardan para siempre: el aviso avisa antes y este
	// tipo registra cuándo ya ocurrió.
	VIDEO_BORRADO("video borrado");

	// El texto que carga cada constante, fijado al construirse.
	private final String valor;

	// Constructor del enum: privado por definición, lo llama Java una vez por constante.
	TipoNotificacion(String valor) {
		this.valor = valor;
	}

	// Implementa valor() de ValorDeBase; @JsonValue hace que sea esto lo que salga en el JSON.
	@Override
	@JsonValue
	public String valor() {
		return valor;
	}

	// El camino inverso: del texto de la base o del JSON a la constante de Java.
	@JsonCreator
	public static TipoNotificacion desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "tipo_notificacion");
	}

}
