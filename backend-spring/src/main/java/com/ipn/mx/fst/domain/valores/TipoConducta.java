// Carpeta lógica de este enum: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// @JsonCreator: el método que usa Jackson para construir el valor al LEER un JSON.
import com.fasterxml.jackson.annotation.JsonCreator;
// @JsonValue: el método que usa Jackson para escribir el valor al SALIR en un JSON.
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio tipo_conducta (D-102, no es tabla), en el orden del dominio de la base. La conducta de un segundo también
// puede ser null (no se ve al espécimen, el worker no decidió o el usuario lo marcó dudoso): eso no es una constante.
//
// Es el enum central del sistema: lo que mide todo el proyecto. Cada segundo de video de cada espécimen
// acaba clasificado en una de estas conductas, o en null.
// "No es tabla" quiere decir que no hay una tabla conducta con una fila por conducta: es un DOMAIN de
// PostgreSQL, una lista fija de textos permitidos en la columna. El README lo menciona como una de las
// diferencias con el modelo original, donde sí existían las tablas intervalo, presenta y conducta.
// Por qué null no es una constante más: null significa "no hay dato", y meterlo como constante obligaría a
// tratarlo como una conducta al sumar y promediar. Al dejarlo fuera, los cálculos lo excluyen solos.
// El patrón de este archivo está explicado a fondo en EstadoAnalisis.
public enum TipoConducta implements ValorDeBase {

	// Las tres primeras son las conductas clásicas de la prueba de nado forzado, y son las que se comparan
	// entre grupo tratado y control.
	NADO_ACTIVO("nado activo"),
	INMOVILIDAD("inmovilidad"),
	ESCALAMIENTO("escalamiento"),
	// La cuarta solo aparece cuando el análisis se hizo en nivel AGRUPADO (ver NivelClasifAnalisis): junta
	// varias conductas bajo una sola etiqueta porque el modelo no las separó.
	CONDUCTA_ACTIVA("conducta activa");

	// El texto que carga cada constante, fijado al construirse.
	private final String valor;

	// Constructor del enum: privado por definición, lo llama Java una vez por constante.
	TipoConducta(String valor) {
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
	public static TipoConducta desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "tipo_conducta");
	}

}
