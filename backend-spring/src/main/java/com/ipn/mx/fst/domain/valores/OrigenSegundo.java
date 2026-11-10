// Carpeta lógica de este enum: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// @JsonCreator: el método que usa Jackson para construir el valor al LEER un JSON.
import com.fasterxml.jackson.annotation.JsonCreator;
// @JsonValue: el método que usa Jackson para escribir el valor al SALIR en un JSON.
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio origen_segundo. MAQUINA es lo que escribe el worker; las otras tres, una persona al revisar.
//
// Es la columna que da trazabilidad al dato: por cada segundo de video queda registrado si la conducta la
// decidió el modelo o la tocó un humano. Sin ella no se podría defender un resultado ante nadie, porque no
// habría forma de separar lo medido automáticamente de lo corregido a mano.
// El patrón de este archivo está explicado a fondo en EstadoAnalisis.
public enum OrigenSegundo implements ValorDeBase {

	// Sin acento en "maquina": así está en la base, y aquí se copia el texto tal cual.
	// Lo escribió el modelo y nadie lo ha revisado.
	MAQUINA("maquina"),
	// Una persona lo miró y estuvo de acuerdo con el modelo: la conducta no cambió, pero ahora está avalada.
	CONFIRMADA("confirmada"),
	// Una persona lo miró y cambió la conducta: el modelo se había equivocado.
	CORREGIDA("corregida"),
	// Una persona lo miró y no supo decidir. La conducta del segundo queda en null, no en una constante
	// de TipoConducta: el dato existe, pero sin valor, y los cálculos lo tratan aparte.
	DUDOSA("dudosa");

	// El texto que carga cada constante, fijado al construirse.
	private final String valor;

	// Constructor del enum: privado por definición, lo llama Java una vez por constante.
	OrigenSegundo(String valor) {
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
	public static OrigenSegundo desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "origen_segundo");
	}

}
