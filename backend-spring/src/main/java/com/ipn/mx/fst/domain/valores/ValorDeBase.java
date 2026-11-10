// Carpeta lógica de esta interfaz: com/ipn/mx/fst/domain/valores.
package com.ipn.mx.fst.domain.valores;

// Lo que comparten los nueve enums de los dominios cerrados de la base (001_esquema.sql): cada constante guarda el
// texto EXACTO que lleva la columna ('en cola', 'nado activo', 'Dia 2'...). Ese texto es también el que sale en el JSON
// de la API y el que entra en los datos; el nombre de la constante (EN_COLA) solo existe dentro de Java.
//
// Qué es un "dominio cerrado": en PostgreSQL, una columna que solo admite una lista fija de textos, obligada
// con un CHECK. El esquema los declara como DOMAIN, de ahí el nombre. La base ya impide meter un valor que
// no esté en la lista; estos enums son la misma lista escrita en Java, para que el compilador también la
// imponga y no haya textos sueltos repartidos por el código.
// Por qué el texto y el nombre de la constante son distintos: Java pide que las constantes vayan en
// MAYUSCULAS_CON_GUION, y la base guarda 'nado activo' con espacio y minúsculas. Cada constante carga su
// texto para poder traducir entre los dos mundos.
public interface ValorDeBase {

	// El texto de la base.
	// Sin cuerpo: cada enum lo implementa devolviendo su propio campo.
	String valor();

	// ¿Este texto es el valor de la constante? Sirve para comparar un texto leído con SQL directo (JdbcTemplate): no falla
	// con null ni con un texto desconocido, simplemente da false.
	//
	// "default" permite que una interfaz traiga un método YA ESCRITO. Antes de Java 8 no se podía, y había
	// que repetirlo en cada clase. Así, los nueve enums heredan este método sin escribir una línea.
	// El orden valor().equals(texto) importa: si fuera texto.equals(valor()) y texto llegara null, truena.
	default boolean es(String texto) {
		return valor().equals(texto);
	}

	// La constante que lleva ese texto, o IllegalArgumentException con el dominio y el texto si no existe.
	//
	// Es el camino inverso de valor(): de texto de la base a constante de Java.
	// static en una interfaz: un método que se llama con el nombre de la interfaz (ValorDeBase.buscar(...)),
	// no sobre una constante. Se pone aquí para escribirlo una vez y que los nueve enums lo usen.
	// <E extends Enum<E> & ValorDeBase> es un parámetro de tipo genérico con DOS condiciones: E tiene que
	// ser un enum Y tiene que implementar ValorDeBase. Gracias a eso, el método vale para los nueve y
	// devuelve el tipo exacto que le pasaron (si le das EstadoAnalisis[], te devuelve un EstadoAnalisis,
	// sin necesidad de convertirlo después).
	static <E extends Enum<E> & ValorDeBase> E buscar(E[] constantes, String texto, String dominio) {
		// Búsqueda lineal. Con listas de dos a seis elementos es más rápida que cualquier mapa, y no hay
		// que construir ni mantener nada.
		for (E constante : constantes) {
			if (constante.valor().equals(texto)) {
				return constante;
			}
		}
		// Si se llega aquí, la base trae un texto que Java no conoce: o alguien agregó un valor al dominio
		// del esquema sin agregarlo al enum, o los datos están corruptos. Las dos cosas son errores de
		// programación, no del usuario, y por eso se lanza una excepción en vez de devolver null.
		// El mensaje incluye el texto Y el dominio para poder localizar el problema sin depurar.
		throw new IllegalArgumentException("'" + texto + "' no es un valor del dominio " + dominio + " de la base");
	}

}
