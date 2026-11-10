// Carpeta lógica de este enum: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// @JsonCreator: el método que usa Jackson para construir el valor al LEER un JSON.
import com.fasterxml.jackson.annotation.JsonCreator;
// @JsonValue: el método que usa Jackson para escribir el valor al SALIR en un JSON.
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio nivel_clasif_analisis.
//
// Con qué detalle clasificó el modelo las conductas de un análisis: PRECISO distingue las cuatro conductas
// de TipoConducta una por una; AGRUPADO junta varias bajo "conducta activa". Importa al presentar
// resultados, porque dos análisis de niveles distintos no se pueden comparar columna por columna.
// El patrón de este archivo está explicado a fondo en EstadoAnalisis.
public enum NivelClasifAnalisis implements ValorDeBase {

	// El texto entre paréntesis es el de la columna en PostgreSQL y el del JSON.
	PRECISO("preciso"),
	AGRUPADO("agrupado");

	// El texto que carga cada constante, fijado al construirse.
	private final String valor;

	// Constructor del enum: privado por definición, lo llama Java una vez por constante.
	NivelClasifAnalisis(String valor) {
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
	public static NivelClasifAnalisis desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "nivel_clasif_analisis");
	}

}
