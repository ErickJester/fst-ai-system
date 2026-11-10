// Carpeta lógica de este enum: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// @JsonCreator: el método que usa Jackson para construir el valor al LEER un JSON.
import com.fasterxml.jackson.annotation.JsonCreator;
// @JsonValue: el método que usa Jackson para escribir el valor al SALIR en un JSON.
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio sesion_video. Sin acento a propósito: así está en la base y en el JSON ('Dia 1', 'Dia 2').
//
// La prueba de nado forzado se hace en dos días: el primero es la sesión de entrenamiento y el segundo es
// la que se mide. Esta columna dice a cuál de los dos pertenece cada video.
// Fíjate en que este es el único enum del paquete cuyo texto lleva mayúscula inicial y un espacio: el texto
// es el que se enseña tal cual en la interfaz, por eso va escrito como se lee.
// El patrón de este archivo está explicado a fondo en EstadoAnalisis.
public enum SesionVideo implements ValorDeBase {

	// El nombre de la constante no puede llevar espacio (Java no lo permite), de ahí el guion bajo;
	// el texto de la base sí lo lleva. Es el ejemplo más claro de por qué los dos se guardan por separado.
	DIA_1("Dia 1"),
	DIA_2("Dia 2");

	// El texto que carga cada constante, fijado al construirse.
	private final String valor;

	// Constructor del enum: privado por definición, lo llama Java una vez por constante.
	SesionVideo(String valor) {
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
	public static SesionVideo desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "sesion_video");
	}

}
