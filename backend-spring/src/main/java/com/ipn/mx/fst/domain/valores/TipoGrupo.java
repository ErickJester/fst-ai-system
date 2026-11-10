// Carpeta lógica de este enum: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// @JsonCreator: el método que usa Jackson para construir el valor al LEER un JSON.
import com.fasterxml.jackson.annotation.JsonCreator;
// @JsonValue: el método que usa Jackson para escribir el valor al SALIR en un JSON.
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio tipo_grupo.
//
// El papel de cada grupo dentro del experimento, que es lo que da sentido a comparar los resultados:
// CONTROL no recibe tratamiento y es la línea base; REFERENCIA recibe un fármaco de efecto conocido, para
// comprobar que el experimento funciona; TRATAMIENTO_EXPERIMENTAL es lo que se está probando.
// El patrón de este archivo está explicado a fondo en EstadoAnalisis.
public enum TipoGrupo implements ValorDeBase {

	// El texto entre paréntesis es el de la columna en PostgreSQL y el del JSON.
	CONTROL("control"),
	REFERENCIA("referencia"),
	// Dos palabras con espacio en la base; en Java, guion bajo.
	TRATAMIENTO_EXPERIMENTAL("tratamiento experimental");

	// El texto que carga cada constante, fijado al construirse.
	private final String valor;

	// Constructor del enum: privado por definición, lo llama Java una vez por constante.
	TipoGrupo(String valor) {
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
	public static TipoGrupo desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "tipo_grupo");
	}

}
