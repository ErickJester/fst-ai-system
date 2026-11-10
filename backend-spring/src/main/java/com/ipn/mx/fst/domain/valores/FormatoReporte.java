// Carpeta lógica de este enum: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// @JsonCreator: el método que usa Jackson para construir el valor al LEER un JSON.
import com.fasterxml.jackson.annotation.JsonCreator;
// @JsonValue: el método que usa Jackson para escribir el valor al SALIR en un JSON.
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio formato_reporte.
//
// Los tres formatos en que se puede descargar un reporte de resultados. Es la columna que distingue las
// filas de la tabla reporte, y la que usa ReporteAlmacen para saber qué archivo guardado buscar.
// El patrón de este archivo está explicado a fondo en EstadoAnalisis.
public enum FormatoReporte implements ValorDeBase {

	// Aquí el texto de la base coincide con el nombre de la constante, porque son siglas y ya van en
	// mayúsculas. Aun así se escribe el texto aparte: el patrón es el mismo para los nueve enums, y
	// depender de que coincidan sería frágil.
	CSV("CSV"),
	XLSX("XLSX"),
	PDF("PDF");

	// El texto que carga cada constante, fijado al construirse.
	private final String valor;

	// Constructor del enum: privado por definición, lo llama Java una vez por constante.
	FormatoReporte(String valor) {
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
	public static FormatoReporte desdeValor(String texto) {
		return ValorDeBase.buscar(values(), texto, "formato_reporte");
	}

}
