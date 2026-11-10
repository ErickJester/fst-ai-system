// Carpeta lógica de esta clase: com/ipn/mx/fst/service/graficas.
package com.ipn.mx.fst.service.graficas;

import java.util.ArrayList;
import java.util.List;
// Locale: para que el formato de los números no dependa de la configuración del sistema.
import java.util.Locale;
import java.util.Map;

// Lectura del mapa de GraficasDeResultados (el mismo que consume la pantalla). Aquí no se calcula nada: solo se leen los
// números y se escriben con coma decimal, como en la pantalla.
//
// El problema que resuelve: los datos de las gráficas vienen como Map<String, Object> anidados, y leerlos
// a pelo obliga a convertir tipos y comprobar nulos en cada acceso. Esta clase concentra esas conversiones
// para que las gráficas se lean como lo que son (dibujo) y no como una sucesión de casts.
// "Aquí no se calcula nada" es la regla de siempre: GraficasDeResultados es el único que hace cuentas.
// final + constructor privado: es una caja de métodos estáticos, no se instancia.
public final class DatosGraficas {

	// Las tres conductas que se dibujan juntas en G1. Son las clásicas de la prueba; «conducta activa»
	// queda fuera porque solo aparece en los análisis de nivel agrupado.
	public static final List<String> CONDUCTAS_TRES = List.of("nado activo", "inmovilidad", "escalamiento");
	public static final String CONDUCTA_ACTIVA = "conducta activa";
	public static final int MINUTOS = 5;

	private DatosGraficas() {
	}

	// La lista de grupos del mapa, o una lista vacía si no hay.
	// @SuppressWarnings("unchecked") silencia el aviso por la conversión: Java no puede comprobar en
	// tiempo de ejecución que sea una List<Map<String, Object>>. Es seguro porque lo arma
	// GraficasDeResultados, que está en el mismo proyecto.
	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> grupos(Map<String, Object> datos) {
		Object grupos = datos == null ? null : datos.get("grupos");
		// Lista vacía y no null: quien la recorra no tiene que comprobar nada.
		return grupos == null ? List.of() : (List<Map<String, Object>>) grupos;
	}

	// Un grupo tiene datos si no mezcla niveles y tiene especímenes con Día 2.
	//
	// Las tres condiciones corresponden a tres situaciones distintas por las que un grupo no se puede
	// dibujar: mezcla niveles de clasificación (sus estadísticos van null, ver GraficasDeResultados),
	// no hay mapa de conductas, o no tiene ningún espécimen analizado.
	// Las gráficas lo usan para saltarse esos grupos, y notas() para explicar por qué faltan.
	public static boolean tieneDatos(Map<String, Object> grupo) {
		return !bool(grupo.get("nivelMixto")) && mapa(grupo.get("porConducta")) != null && entero(grupo.get("n")) > 0;
	}

	// Convierte un Object en mapa, o null si no lo es.
	// instanceof con patrón: comprueba el tipo y declara la variable de una vez. Devuelve null en vez de
	// tronar con un ClassCastException si el dato no viene como se esperaba.
	@SuppressWarnings("unchecked")
	public static Map<String, Object> mapa(Object valor) {
		return valor instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
	}

	// Lo mismo para listas, pero devolviendo una lista VACÍA en lugar de null: así se puede meter
	// directamente en un for sin comprobar nada (es lo que hace armarTablaGrupos con porNivel).
	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> lista(Object valor) {
		return valor instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
	}

	// Los estadísticos de una conducta de un grupo (o de un minuto): n, media, de, varianza, cv.
	//
	// Vale para los dos porque el mapa tiene la misma forma en los dos sitios: tanto un grupo como un
	// minuto llevan un porConducta dentro (ver GraficasDeResultados).
	// Map.of() es un mapa vacío inmutable: quien lo reciba puede pedirle claves y recibirá null, sin tronar.
	public static Map<String, Object> estadisticos(Map<String, Object> grupoOMinuto, String conducta) {
		Map<String, Object> porConducta = mapa(grupoOMinuto.get("porConducta"));
		return porConducta == null ? Map.of() : mapa(porConducta.get(conducta));
	}

	// Los valores de cada espécimen, para dibujar los puntos individuales encima de la barra.
	// Se convierten uno a uno con dbl, que deja en null lo que no sea número.
	public static List<Double> valoresS(Map<String, Object> estadisticos) {
		List<Double> valores = new ArrayList<>();
		Object lista = estadisticos.get("valoresS");
		if (lista instanceof List<?> l) {
			for (Object v : l) {
				valores.add(dbl(v));
			}
		}
		return valores;
	}

	// Un número decimal, o null si el valor no es un número (incluido el caso de que falte la clave).
	// Number cubre Integer, Long y Double a la vez: el JSON puede traer 12 o 12.0 y los dos valen.
	public static Double dbl(Object valor) {
		return valor instanceof Number n ? n.doubleValue() : null;
	}

	// Un entero, o 0 si no hay número. Aquí el 0 sí es un valor por omisión razonable: se usa para n y
	// nTotal, donde "no hay dato" y "ninguno" significan lo mismo al dibujar.
	public static int entero(Object valor) {
		return valor instanceof Number n ? n.intValue() : 0;
	}

	// Boolean.TRUE.equals: true solo si de verdad vale true. Un null o un valor de otro tipo dan false,
	// sin tronar al desempaquetar.
	public static boolean bool(Object valor) {
		return Boolean.TRUE.equals(valor);
	}

	// Un decimal con coma; lo que no existe no se escribe como cero.
	//
	// Las dos decisiones importan. La coma decimal es la convención en español, y el PDF se lee en
	// español. Y devolver null en vez de "0,0" mantiene la regla de GraficasDeResultados: un dato que no
	// se puede calcular no se enseña como cero, porque cero es una afirmación sobre los datos.
	// Locale.ROOT en el format fuerza el punto decimal (de forma predecible en cualquier máquina) y
	// luego se cambia a coma a mano: así el resultado no depende de la configuración del sistema.
	public static String numero(Double valor) {
		return valor == null ? null : String.format(Locale.ROOT, "%.1f", valor).replace('.', ',');
	}

	// «12,3 ± 2,1 s»; sin DE, «12,3 s ± no aplica»; sin media, «sin datos».
	//
	// Los tres casos dicen cosas distintas y conviene distinguirlos:
	//   con media y DE, el resultado completo;
	//   con media pero sin DE, un solo espécimen: hay valor pero no hay dispersión que medir, y "± no
	//     aplica" lo dice sin fingir un 0;
	//   sin media, no hay nada que mostrar.
	public static String mediaDe(Double media, Double de, String unidad) {
		if (media == null) {
			return "sin datos";
		}
		return de == null ? numero(media) + unidad + " ± no aplica" : numero(media) + " ± " + numero(de) + unidad;
	}

	// «n = 3» o, con tandas pendientes, «n = 2 de 4».
	//
	// Es un detalle de honestidad: una media sobre 2 de los 4 especímenes previstos merece que se vea.
	// Sin el "de 4", quien lea la gráfica supondría que el grupo entero está medido.
	public static String textoN(Map<String, Object> grupo) {
		int n = entero(grupo.get("n"));
		int total = entero(grupo.get("nTotal"));
		return n < total ? "n = " + n + " de " + total : "n = " + n;
	}

	// La escala de segundos de G1: 300 s, la ventana del Día 2; crece en múltiplos de 30 s solo si un grupo la rebasa
	// con su media + DE o con un espécimen. Es la misma regla que la pantalla.
	//
	// Por qué un mínimo fijo de 300: es la duración de la ventana medida (ver SegundoId), así que el eje
	// representa siempre la misma escala y dos gráficas de experimentos distintos se pueden comparar a
	// ojo. Una escala que se ajustara siempre al dato mayor haría que una diferencia pequeña pareciera
	// enorme, que es una de las formas clásicas de engañar con una gráfica.
	// Solo crece si algo se sale de verdad, para que nada quede cortado.
	public static double escalaSegundos(Map<String, Object> datos, List<String> conductas) {
		double mayor = 0;
		for (Map<String, Object> g : grupos(datos)) {
			// Los grupos sin datos no influyen en la escala.
			if (!tieneDatos(g)) {
				continue;
			}
			for (String c : conductas) {
				Map<String, Object> e = estadisticos(g, c);
				Double media = dbl(e.get("media"));
				Double de = dbl(e.get("de"));
				// media + DE es el extremo superior de la barra de error, que es lo que no debe salirse.
				if (media != null) {
					mayor = Math.max(mayor, media + (de == null ? 0 : de));
				}
				// También se miran los puntos de cada espécimen: uno puede estar por encima de media + DE
				// y quedaría fuera del dibujo.
				for (Double v : valoresS(e)) {
					if (v != null) {
						mayor = Math.max(mayor, v);
					}
				}
			}
		}
		// Math.ceil(mayor / 30) * 30 redondea HACIA ARRIBA al múltiplo de 30 más cercano: con 312 da 330.
		// Múltiplos de 30 porque son medios minutos, que dan marcas de eje legibles.
		return mayor <= 300 ? 300 : Math.ceil(mayor / 30) * 30;
	}
}
