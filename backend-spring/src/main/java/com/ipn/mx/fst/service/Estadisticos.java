// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

import java.util.List;

// Media, varianza y desviación estándar MUESTRALES (n − 1) de los segundos de una conducta por espécimen. Los usan
// /results/groups y los reportes CSV y XLSX, para que la pantalla y los archivos digan lo mismo. Sin especímenes los
// tres valores son null; con uno solo, la varianza y la desviación valen 0.
//
// Es el archivo donde vive la estadística del proyecto, y conviene entender por qué dice "muestrales".
// Al dividir entre n − 1 en vez de entre n se calcula la varianza MUESTRAL, no la poblacional. La
// diferencia: los especímenes de un grupo son una MUESTRA de todos los que podrían haberse usado, no la
// población entera. Dividir entre n − 1 (la corrección de Bessel) corrige el sesgo que aparece al estimar
// la variabilidad de una población a partir de unos pocos individuos.
// No es un detalle menor: con 8 especímenes por grupo, dividir entre 7 u 8 cambia el resultado lo
// bastante como para que un revisor lo note. Es lo que se espera en un artículo.
//
// "Para que la pantalla y los archivos digan lo mismo" es la otra razón de existir de esta clase. Si cada
// sitio calculara su media, bastaría un redondeo distinto para que el PDF y la web mostraran números que
// no cuadran, y entonces no se puede confiar en ninguno de los dos.
// final + constructor privado: es una caja de métodos estáticos, no se instancia (como Respuesta).
public final class Estadisticos {

	// Impide escribir "new Estadisticos()".
	private Estadisticos() {
	}

	// El promedio. Devuelve Double (objeto) y no double para poder devolver null.
	public static Double media(List<Long> valores) {
		// Sin datos no hay media. Devolver null y no 0 es importante: 0 significaría "el promedio es cero",
		// que es una afirmación sobre los datos; null significa "no hay datos", que es otra cosa.
		// Enseñar un 0 donde no hubo mediciones sería dar por buena una conducta que nadie observó.
		if (valores.isEmpty()) {
			return null;
		}
		double suma = 0;
		for (long valor : valores) {
			suma += valor;
		}
		// La división es de double porque suma lo es, así que no trunca: no hace falta convertir nada.
		return suma / valores.size();
	}

	// La varianza muestral: el promedio de los cuadrados de las distancias a la media, dividido entre n − 1.
	public static Double varianza(List<Long> valores) {
		// Mismo criterio que arriba: sin datos, null.
		if (valores.isEmpty()) {
			return null;
		}
		// Con un solo valor la fórmula dividiría entre cero (n − 1 = 0). Se devuelve 0 porque un único
		// dato no tiene dispersión: no se aleja de sí mismo. Es un caso real (un grupo de un espécimen).
		if (valores.size() == 1) {
			return 0.0;
		}
		// Se reutiliza media(): un solo sitio donde el promedio se calcula.
		// La asignación de Double a double funciona por el desempaquetado automático de Java, y aquí es
		// seguro porque ya se comprobó que la lista no está vacía y media() no puede devolver null.
		double media = media(valores);
		double cuadrados = 0;
		for (long valor : valores) {
			double diferencia = valor - media;
			// Se eleva al cuadrado multiplicando, en vez de con Math.pow: es más rápido y más claro para
			// un exponente 2. Elevar al cuadrado cumple dos funciones: quita el signo (si no, las
			// distancias positivas y negativas se cancelarían) y pesa más las desviaciones grandes.
			cuadrados += diferencia * diferencia;
		}
		// Aquí está la corrección de Bessel: el − 1 del divisor.
		return cuadrados / (valores.size() - 1);
	}

	// La desviación estándar: la raíz cuadrada de la varianza.
	// Se usa en vez de la varianza para presentar resultados porque vuelve a las unidades originales:
	// la varianza está en segundos AL CUADRADO, que no significa nada que se pueda interpretar; la
	// desviación está en segundos y se puede escribir como "media ± DE".
	public static Double desviacion(List<Long> valores) {
		Double varianza = varianza(valores);
		// Si no hubo datos, se propaga el null en vez de tropezar al calcular la raíz.
		return varianza == null ? null : Math.sqrt(varianza);
	}

	// Un decimal, como se muestra en la pantalla y en los reportes.
	//
	// El truco de multiplicar por 10, redondear y dividir es la forma estándar de redondear a un decimal.
	// El 10.0 con punto obliga a que la división sea decimal; con un 10 entero, Math.round devuelve un
	// long y la división truncaría el decimal recién calculado.
	// Un decimal es lo razonable para segundos: el dato de origen son conteos enteros, y más decimales
	// darían una falsa impresión de precisión.
	public static double redondear(double valor) {
		return Math.round(valor * 10.0) / 10.0;
	}

	// Null se queda null.
	//
	// Es la misma operación sobrecargada para el tipo objeto. Las dos versiones conviven: Java elige una u
	// otra según el tipo del argumento. Sin esta, redondear(media(...)) tronaría cuando no hay datos,
	// porque desempaquetar un Double null lanza NullPointerException.
	// doubleValue() lo desempaqueta a propósito, para que Java llame a la versión de arriba y no se quede
	// llamándose a sí misma sin fin.
	public static Double redondear(Double valor) {
		return valor == null ? null : redondear(valor.doubleValue());
	}

}
