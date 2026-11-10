// Carpeta lógica de este enum: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// @JsonCreator: el método que usa Jackson para construir el valor al LEER un JSON.
import com.fasterxml.jackson.annotation.JsonCreator;
// @JsonValue: el método que usa Jackson para escribir el valor al SALIR en un JSON.
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio etapa_analisis, en el orden en que las recorre el worker.
//
// Mientras el estado dice "en qué situación está el análisis" (en cola, procesando...), la etapa dice por
// qué PARTE del trabajo va el worker cuando está procesando. Son dos columnas distintas.
// El patrón de este archivo está explicado a fondo en EstadoAnalisis.
public enum EtapaAnalisis implements ValorDeBase {

	// El texto entre paréntesis es el de la columna en PostgreSQL y el del JSON.
	// El orden es el real del trabajo: primero se prepara el video, luego se detectan los cilindros donde
	// nadan los especímenes, y al final se clasifica la conducta de cada segundo.
	PREPROCESAMIENTO("preprocesamiento"),
	CILINDROS("cilindros"),
	// Sin acento en "clasificacion": así está en la base, y aquí se copia el texto tal cual.
	CLASIFICACION("clasificacion");

	// El texto que carga cada constante, fijado al construirse.
	private final String valor;

	// Constructor del enum: privado por definición, lo llama Java una vez por constante.
	EtapaAnalisis(String valor) {
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
	public static EtapaAnalisis desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "etapa_analisis");
	}

}
