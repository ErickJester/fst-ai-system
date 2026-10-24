package com.ipn.mx.fst.domain.valores;

// Lo que comparten los nueve enums de los dominios cerrados de la base (001_esquema.sql): cada constante guarda el
// texto EXACTO que lleva la columna ('en cola', 'nado activo', 'Dia 2'...). Ese texto es también el que sale en el JSON
// de la API y el que entra en los datos; el nombre de la constante (EN_COLA) solo existe dentro de Java.
public interface ValorDeBase {

	// El texto de la base.
	String valor();

	// ¿Este texto es el valor de la constante? Sirve para comparar un texto leído con SQL directo (JdbcTemplate): no falla
	// con null ni con un texto desconocido, simplemente da false.
	default boolean es(String texto) {
		return valor().equals(texto);
	}

	// La constante que lleva ese texto, o IllegalArgumentException con el dominio y el texto si no existe.
	static <E extends Enum<E> & ValorDeBase> E buscar(E[] constantes, String texto, String dominio) {
		for (E constante : constantes) {
			if (constante.valor().equals(texto)) {
				return constante;
			}
		}
		throw new IllegalArgumentException("'" + texto + "' no es un valor del dominio " + dominio + " de la base");
	}

}
