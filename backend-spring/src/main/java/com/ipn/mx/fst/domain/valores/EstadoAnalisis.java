// Carpeta lógica de este enum: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// @JsonCreator: le dice a Jackson qué método usar para construir el valor al LEER un JSON que entra.
import com.fasterxml.jackson.annotation.JsonCreator;
// @JsonValue: le dice a Jackson qué método usar para escribir el valor al SALIR en un JSON.
import com.fasterxml.jackson.annotation.JsonValue;

// Dominio estado_analisis.
//
// Los seis estados por los que pasa un análisis. Un enum es una lista cerrada de valores: no se puede
// escribir un estado que no esté aquí, y el compilador avisa si un switch se olvida de uno.
// La estructura de este archivo se repite igual en los nueve enums de este paquete; está explicada
// a fondo aquí y resumida en los demás. ValorDeBase explica el porqué del patrón completo.
public enum EstadoAnalisis implements ValorDeBase {

	// Cada constante lleva entre paréntesis el texto EXACTO de la columna en PostgreSQL. Ese texto es
	// además el que viaja en el JSON de la API, así que cambiarlo rompería la base Y el frontend a la vez.
	// El orden es el del dominio en 001_esquema.sql, y también el del ciclo de vida de un análisis:
	// entra en cola, el worker lo procesa, puede pausarse, y termina completado, con error o cancelado.
	EN_COLA("en cola"),
	PROCESANDO("procesando"),
	EN_PAUSA("en pausa"),
	COMPLETADO("completado"),
	ERROR("error"),
	// El punto y coma cierra la lista de constantes y abre el resto de la clase.
	CANCELADO("cancelado");

	// El texto que carga cada constante. private final: se fija al construirse y nadie lo puede cambiar.
	private final String valor;

	// El constructor de un enum es SIEMPRE privado (aunque no se escriba): las constantes de arriba son
	// las únicas instancias que existirán nunca. Java lo llama una vez por constante, al cargar la clase.
	EstadoAnalisis(String valor) {
		this.valor = valor;
	}

	// @Override: implementa valor() de la interfaz ValorDeBase.
	@Override
	// @JsonValue: al serializar, Jackson escribe lo que devuelve este método en vez del nombre de la
	// constante. Sin esta línea, la API respondería "EN_COLA" y el frontend esperaría "en cola".
	@JsonValue
	public String valor() {
		return valor;
	}

	// @JsonCreator: al deserializar un JSON que entra, Jackson llama a este método con el texto recibido.
	// Es el inverso exacto de @JsonValue; los dos juntos hacen que el enum entre y salga igual que en la base.
	@JsonCreator
	public static EstadoAnalisis desdeValor(String texto) {
		// values() es un método que Java genera solo en todo enum: devuelve el arreglo de sus constantes.
		// El tercer argumento es el nombre del dominio, solo para el mensaje de error de ValorDeBase.buscar.
		return ValorDeBase.buscar(values(), texto, "estado_analisis");
	}

}
