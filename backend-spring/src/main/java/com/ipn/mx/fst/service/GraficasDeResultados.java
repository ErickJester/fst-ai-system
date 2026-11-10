// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Los dos enums de dominio que se usan: las conductas y el tipo de grupo.
import com.ipn.mx.fst.domain.valores.TipoConducta;
import com.ipn.mx.fst.domain.valores.TipoGrupo;
import java.util.ArrayList;
// Arrays: para recorrer el arreglo que devuelve values() de un enum.
import java.util.Arrays;
// Comparator: para ordenar los grupos por dos criterios encadenados.
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
// TreeMap: mapa ORDENADO por su clave. Aquí ordena las tandas por su ordinal.
import java.util.TreeMap;
import java.util.stream.Stream;

// Datos de las seis gráficas de resultados (M1A en pantalla, M1B en el PDF): por grupo del Día 2, por conducta, la media,
// la desviación estándar, la varianza, el coeficiente de variación, n y los valores por espécimen; y por minuto (1 a 5), la
// media, la DE y n. Es el único cálculo: la pantalla y el PDF consumen este mapa y así sus números no difieren. Sin base de
// datos: ResultadosServiceImpl le pasa los especímenes ya agregados. La especificación está en
// frontend/src/components/resultados/graficas/ESPECIFICACION.md.
//
// Reglas: la DE y la varianza son muestrales (n − 1) y no existen con menos de dos especímenes (null, nunca 0); el
// coeficiente de variación (DE ÷ media) no existe si la media es 0 ni con menos de dos especímenes (null). Un minuto con
// menos de 60 s es incompleto y se marca; sus segundos son los reales, no se completan a 60. Un grupo con niveles de
// clasificación mezclados no se promedia junto: sus estadísticos van null y se dan por tanda en «porNivel».
//
// "Es el único cálculo" es la idea central del archivo: la gráfica de la web y la del PDF salen del MISMO
// mapa. Si cada una calculara por su cuenta, bastaría un redondeo distinto para que la figura del
// artículo no cuadrara con lo que el investigador vio en pantalla.
//
// Las reglas del segundo párrafo son todas de honestidad estadística, y conviene entenderlas una a una:
//   null y nunca 0 con menos de dos especímenes: con un solo individuo no hay dispersión que medir. Un 0
//     afirmaría "no hay variabilidad", que es una conclusión; null dice "no se puede calcular".
//   el CV no existe si la media es 0: sería dividir entre cero. Y un CV sobre una media de cero no
//     significaría nada aunque se pudiera calcular.
//   un minuto incompleto NO se completa a 60: si el video duró 4 min 20 s, el minuto 5 tiene 20 segundos
//     reales. Rellenar hasta 60 inventaría datos; marcarlo como incompleto deja que quien lea decida.
//   un grupo con niveles mezclados no se promedia: un análisis "preciso" distingue nado de escalamiento y
//     uno "agrupado" los junta bajo "conducta activa" (ver NivelClasifAnalisis). Promediar ambos daría un
//     número sin sentido, así que se dan por tanda y se avisa.
// "Sin base de datos" otra vez significa clase pura: se prueba con listas escritas a mano.
public final class GraficasDeResultados {

	public static final int MINUTOS = 5;
	public static final int SEGUNDOS_POR_MINUTO = 60;

	// Las cuatro conductas y «sin ver», con el nombre de la base, en el orden de todas las respuestas.
	//
	// Se construye desde el enum (values()) y no escribiendo los textos a mano: si mañana se añadiera una
	// conducta al dominio, esta lista la recoge sola. SIN_VER se concatena al final porque no es una
	// constante del enum: es el null, que aquí tiene que aparecer como una categoría más para que las
	// barras apiladas sumen el total (ver G4ApiladaPdf).
	public static final List<String> CONDUCTAS = Stream.concat(
			Arrays.stream(TipoConducta.values()).map(TipoConducta::valor), Stream.of(TablaDeReporte.SIN_VER)).toList();

	// Un grupo del experimento, con sus tandas que faltan (como en /results/groups).
	//
	// nTotal es cuántos especímenes DEBERÍA tener el grupo y faltantes qué tandas no se han analizado.
	// Los dos juntos permiten a la gráfica decir "n = 6 de 8" en vez de presentar 6 como si fueran todos:
	// una media sobre menos individuos de los previstos merece un aviso.
	public record Grupo(int idGrupo, String etiqueta, String tipo, String tratamiento, int nTotal, List<Map<String, Object>> faltantes) {
	}

	// Un espécimen con análisis completado: su tanda (letra), su nivel, sus segundos por conducta (incluye «sin ver») y por minuto.
	//
	// total y porMinuto llegan YA sumados desde ResultadosServiceImpl: aquí no se cuentan segundos, solo
	// se promedian especímenes. porMinuto es un mapa de minuto a (conducta a segundos).
	public record Especimen(int idGrupo, int ordinal, String letra, int cilindro, String nivel, long duracionS,
			Map<String, Long> total, Map<Integer, Map<String, Long>> porMinuto) {
	}

	// Impide instanciarla: es una caja de métodos estáticos.
	private GraficasDeResultados() {
	}

	public static Map<String, Object> armar(List<Grupo> grupos, List<Especimen> especimenes) {
		List<Map<String, Object>> salida = new ArrayList<>();
		// Se ordenan por tipo y, dentro del mismo tipo, por id. El segundo criterio desempata: sin él, dos
		// grupos experimentales podrían salir en distinto orden entre dos cargas y la gráfica "bailaría".
		// El (Grupo g) explícito es necesario para que Java deduzca el tipo en la cadena de comparadores.
		List<Grupo> ordenados = grupos.stream().sorted(Comparator.comparingInt((Grupo g) -> rango(g.tipo())).thenComparingInt(Grupo::idGrupo)).toList();
		for (Grupo g : ordenados) {
			// A cada grupo se le pasan solo SUS especímenes.
			salida.add(grupo(g, especimenes.stream().filter(e -> e.idGrupo() == g.idGrupo()).toList()));
		}
		Map<String, Object> datos = new LinkedHashMap<>();
		// Siempre el Día 2: el Día 1 es la sesión de entrenamiento y no se mide (ver SesionVideo).
		datos.put("dia", 2);
		// Las categorías y los minutos viajan con los datos para que quien dibuje no los suponga: así, si
		// un día se añade una conducta, la gráfica se adapta sin tocar el código que la pinta.
		datos.put("conductas", CONDUCTAS);
		// Stream.iterate genera 1, 2, 3... y limit lo corta en MINUTOS. Es [1,2,3,4,5].
		datos.put("minutos", Stream.iterate(1, m -> m + 1).limit(MINUTOS).toList());
		datos.put("grupos", salida);
		return datos;
	}

	// Control, luego referencia, luego los experimentales.
	//
	// No es cosmético: es cómo se lee una gráfica de este tipo. El control es la línea base y va primero;
	// la referencia (el fármaco de efecto conocido) valida que el experimento funcionó; los
	// experimentales son lo que se está probando y se comparan contra los dos anteriores.
	// Devolver un número y ordenar por él es más simple que escribir un comparador con casos.
	private static int rango(String tipo) {
		if (TipoGrupo.CONTROL.valor().equals(tipo)) {
			return 0;
		}
		if (TipoGrupo.REFERENCIA.valor().equals(tipo)) {
			return 1;
		}
		return 2;
	}

	private static Map<String, Object> grupo(Grupo g, List<Especimen> esp) {
		// ¿Hay más de un nivel de clasificación distinto entre sus especímenes? distinct() quita
		// repetidos y count() los cuenta: más de uno significa mezcla.
		boolean mixto = esp.stream().map(Especimen::nivel).distinct().count() > 1;
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("idGrupo", g.idGrupo());
		m.put("etiqueta", g.etiqueta());
		m.put("tipo", g.tipo());
		m.put("tratamiento", g.tratamiento());
		// n es cuántos especímenes HAY con resultados; nTotal, cuántos debería haber.
		m.put("n", esp.size());
		m.put("nTotal", g.nTotal());
		m.put("faltantes", g.faltantes());
		m.put("nivelMixto", mixto);
		m.put("especimenes", esp.stream().map(GraficasDeResultados::especimen).toList());
		if (mixto) {
			// Los tres estadísticos agregados van null a propósito: promediar niveles distintos daría un
			// número sin sentido. En su lugar se dan por tanda, que sí son homogéneas.
			// Fíjate en que las cuatro claves SIEMPRE existen, con null o con valor: así quien lo lea no
			// tiene que comprobar si la clave está, solo si vale null.
			m.put("duracionMediaS", null);
			m.put("porConducta", null);
			m.put("porMinuto", null);
			m.put("porNivel", porNivel(esp));
		} else {
			List<Long> duraciones = esp.stream().map(Especimen::duracionS).toList();
			m.put("duracionMediaS", Estadisticos.redondear(Estadisticos.media(duraciones)));
			m.put("porConducta", porConducta(esp));
			m.put("porMinuto", porMinuto(esp));
			m.put("porNivel", null);
		}
		return m;
	}

	// Los datos identificativos de un espécimen. letra y numeroCilindro juntos lo identifican, ya que no
	// tiene nombre propio (ver la entidad Especimen).
	private static Map<String, Object> especimen(Especimen e) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("letra", e.letra());
		m.put("numeroCilindro", e.cilindro());
		m.put("nivelClasif", e.nivel());
		m.put("duracionS", e.duracionS());
		return m;
	}

	// Por conducta, los estadísticos del grupo y los valores de cada espécimen (en el orden de «especimenes»).
	//
	// "En el orden de especimenes" permite a la gráfica dibujar los puntos individuales encima de la barra
	// y saber a qué espécimen corresponde cada uno, sin repetir sus datos en cada valor.
	private static Map<String, Object> porConducta(List<Especimen> esp) {
		Map<String, Object> salida = new LinkedHashMap<>();
		for (String conducta : CONDUCTAS) {
			// getOrDefault(conducta, 0L): un espécimen que nunca tuvo esa conducta no aparece en su mapa.
			// Aquí 0 SÍ es correcto, al revés que en los estadísticos: "no hizo nada de eso" es un dato
			// real, y omitirlo sacaría al espécimen del promedio como si no se hubiera medido.
			List<Long> valores = esp.stream().map(e -> e.total().getOrDefault(conducta, 0L)).toList();
			Map<String, Object> par = estadisticos(valores);
			par.put("valoresS", valores);
			salida.put(conducta, par);
		}
		return salida;
	}

	// Por tanda (letra) de un grupo con niveles mezclados: la misma forma que porConducta, sin valores por espécimen.
	private static List<Map<String, Object>> porNivel(List<Especimen> esp) {
		// TreeMap y no LinkedHashMap: ordena por la clave (el ordinal), así las tandas salen A, B, C
		// aunque los especímenes llegaran en otro orden.
		Map<Integer, List<Especimen>> porTanda = new TreeMap<>();
		for (Especimen e : esp) {
			porTanda.computeIfAbsent(e.ordinal(), k -> new ArrayList<>()).add(e);
		}
		List<Map<String, Object>> salida = new ArrayList<>();
		for (List<Especimen> delaTanda : porTanda.values()) {
			Map<String, Object> renglon = new LinkedHashMap<>();
			// get(0) es seguro: computeIfAbsent solo crea la lista al añadir el primero, nunca está vacía.
			// La letra y el nivel son los mismos para toda la tanda, de ahí que baste el primero.
			renglon.put("letra", delaTanda.get(0).letra());
			renglon.put("nivelClasif", delaTanda.get(0).nivel());
			renglon.put("n", delaTanda.size());
			Map<String, Object> porConducta = new LinkedHashMap<>();
			for (String conducta : CONDUCTAS) {
				porConducta.put(conducta, estadisticos(delaTanda.stream().map(e -> e.total().getOrDefault(conducta, 0L)).toList()));
			}
			renglon.put("porConducta", porConducta);
			salida.add(renglon);
		}
		return salida;
	}

	// Por minuto (1 a 5): solo cuentan los especímenes con segundos en ese minuto (un video corto no aporta al 4 ni al 5).
	// Para cada conducta, media, DE y n de esos especímenes; los segundos reales del minuto van en su mínimo y máximo.
	//
	// El filtro es la decisión importante: un video de 4 minutos no tiene minuto 5, y meter a ese
	// espécimen con ceros hundiría la media de ese minuto. Excluirlo y bajar la n es lo honesto.
	private static List<Map<String, Object>> porMinuto(List<Especimen> esp) {
		List<Map<String, Object>> salida = new ArrayList<>();
		for (int minuto = 1; minuto <= MINUTOS; minuto++) {
			// final int m: una lambda solo puede usar variables que no cambian, y la del for sí cambia.
			// Copiarla a una final es el modo habitual de resolverlo.
			final int m = minuto;
			// Los especímenes que SÍ tienen segundos en este minuto.
			List<Especimen> con = esp.stream().filter(e -> segundosDelMinuto(e, m) > 0).toList();
			List<Long> segundos = con.stream().map(e -> segundosDelMinuto(e, m)).toList();
			Map<String, Object> fila = new LinkedHashMap<>();
			fila.put("minuto", minuto);
			fila.put("n", con.size());
			// El mínimo y el máximo de segundos reales dejan ver de un vistazo si los videos cuadran:
			// con todos completos, los dos valen 60.
			fila.put("segundosMin", segundos.isEmpty() ? null : segundos.stream().mapToLong(Long::longValue).min().getAsLong());
			fila.put("segundosMax", segundos.isEmpty() ? null : segundos.stream().mapToLong(Long::longValue).max().getAsLong());
			// La marca que pide la regla: basta UN espécimen con menos de 60 s para avisar de que el
			// minuto no es comparable con los demás.
			fila.put("incompleto", segundos.stream().anyMatch(s -> s < SEGUNDOS_POR_MINUTO));
			Map<String, Object> porConducta = new LinkedHashMap<>();
			for (String conducta : CONDUCTAS) {
				// Doble getOrDefault: el minuto puede no estar en el mapa del espécimen, y la conducta
				// puede no estar en el mapa del minuto. Map.of() es un mapa vacío inmutable.
				porConducta.put(conducta, estadisticos(con.stream().map(e -> e.porMinuto().getOrDefault(m, Map.of()).getOrDefault(conducta, 0L)).toList()));
			}
			fila.put("porConducta", porConducta);
			salida.add(fila);
		}
		return salida;
	}

	// Cuántos segundos tiene un espécimen en un minuto: la suma de todas sus conductas en él.
	// Como «sin ver» también es una categoría, la suma da los segundos REALES del minuto, incluidos
	// aquellos en los que no se veía al espécimen. Por eso sirve de filtro: 0 significa que ese minuto
	// no existe para ese video, no que no se viera nada.
	private static long segundosDelMinuto(Especimen e, int minuto) {
		return e.porMinuto().getOrDefault(minuto, Map.of()).values().stream().mapToLong(Long::longValue).sum();
	}

	// n, media, DE, varianza y coeficiente de variación de una lista de valores. La media y la DE a un decimal; el CV a tres
	// decimales (fracción: 0,250 = 25 %). Lo que no existe va null.
	//
	// Es donde se aplican las reglas del encabezado. Fíjate en que la media SÍ se calcula con un solo
	// valor (la media de un número es ese número), pero la DE y la varianza no.
	// El CV (desviación entre media) sirve para comparar variabilidad entre cosas de magnitud distinta:
	// una DE de 10 s es mucho sobre una media de 20 y poco sobre una de 200.
	static Map<String, Object> estadisticos(List<Long> valores) {
		int n = valores.size();
		Double media = Estadisticos.media(valores);
		// El n < 2 es la regla: con un solo espécimen no hay dispersión que estimar. Estadisticos
		// devolvería 0.0 en ese caso, y aquí se prefiere null, que no afirma nada.
		Double varianza = n < 2 ? null : Estadisticos.varianza(valores);
		Double de = n < 2 ? null : Estadisticos.desviacion(valores);
		// Las tres condiciones: sin DE no hay CV; sin media tampoco; y con media 0 sería dividir entre cero.
		Double cv = (de == null || media == null || media == 0) ? null : de / media;
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("n", n);
		// La versión de redondear que acepta Double y deja pasar el null.
		m.put("media", Estadisticos.redondear(media));
		m.put("de", Estadisticos.redondear(de));
		m.put("varianza", Estadisticos.redondear(varianza));
		// El CV lleva tres decimales y no uno: es una fracción pequeña (0,250 = 25 %), y con un decimal se
		// perdería casi toda la información. Mismo truco de redondeo, con 1000 en vez de 10.
		m.put("cv", cv == null ? null : Math.round(cv * 1000.0) / 1000.0);
		return m;
	}

}
