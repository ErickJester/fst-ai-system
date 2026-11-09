package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.valores.TipoConducta;
import com.ipn.mx.fst.domain.valores.TipoGrupo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public final class GraficasDeResultados {

	public static final int MINUTOS = 5;
	public static final int SEGUNDOS_POR_MINUTO = 60;

	// Las cuatro conductas y «sin ver», con el nombre de la base, en el orden de todas las respuestas.
	public static final List<String> CONDUCTAS = Stream.concat(
			Arrays.stream(TipoConducta.values()).map(TipoConducta::valor), Stream.of(TablaDeReporte.SIN_VER)).toList();

	// Un grupo del experimento, con sus tandas que faltan (como en /results/groups).
	public record Grupo(int idGrupo, String etiqueta, String tipo, String tratamiento, int nTotal, List<Map<String, Object>> faltantes) {
	}

	// Un espécimen con análisis completado: su tanda (letra), su nivel, sus segundos por conducta (incluye «sin ver») y por minuto.
	public record Especimen(int idGrupo, int ordinal, String letra, int cilindro, String nivel, long duracionS,
			Map<String, Long> total, Map<Integer, Map<String, Long>> porMinuto) {
	}

	private GraficasDeResultados() {
	}

	public static Map<String, Object> armar(List<Grupo> grupos, List<Especimen> especimenes) {
		List<Map<String, Object>> salida = new ArrayList<>();
		List<Grupo> ordenados = grupos.stream().sorted(Comparator.comparingInt((Grupo g) -> rango(g.tipo())).thenComparingInt(Grupo::idGrupo)).toList();
		for (Grupo g : ordenados) {
			salida.add(grupo(g, especimenes.stream().filter(e -> e.idGrupo() == g.idGrupo()).toList()));
		}
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("dia", 2);
		datos.put("conductas", CONDUCTAS);
		datos.put("minutos", Stream.iterate(1, m -> m + 1).limit(MINUTOS).toList());
		datos.put("grupos", salida);
		return datos;
	}

	// Control, luego referencia, luego los experimentales.
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
		boolean mixto = esp.stream().map(Especimen::nivel).distinct().count() > 1;
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("idGrupo", g.idGrupo());
		m.put("etiqueta", g.etiqueta());
		m.put("tipo", g.tipo());
		m.put("tratamiento", g.tratamiento());
		m.put("n", esp.size());
		m.put("nTotal", g.nTotal());
		m.put("faltantes", g.faltantes());
		m.put("nivelMixto", mixto);
		m.put("especimenes", esp.stream().map(GraficasDeResultados::especimen).toList());
		if (mixto) {
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

	private static Map<String, Object> especimen(Especimen e) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("letra", e.letra());
		m.put("numeroCilindro", e.cilindro());
		m.put("nivelClasif", e.nivel());
		m.put("duracionS", e.duracionS());
		return m;
	}

	// Por conducta, los estadísticos del grupo y los valores de cada espécimen (en el orden de «especimenes»).
	private static Map<String, Object> porConducta(List<Especimen> esp) {
		Map<String, Object> salida = new LinkedHashMap<>();
		for (String conducta : CONDUCTAS) {
			List<Long> valores = esp.stream().map(e -> e.total().getOrDefault(conducta, 0L)).toList();
			Map<String, Object> par = estadisticos(valores);
			par.put("valoresS", valores);
			salida.put(conducta, par);
		}
		return salida;
	}

	// Por tanda (letra) de un grupo con niveles mezclados: la misma forma que porConducta, sin valores por espécimen.
	private static List<Map<String, Object>> porNivel(List<Especimen> esp) {
		Map<Integer, List<Especimen>> porTanda = new TreeMap<>();
		for (Especimen e : esp) {
			porTanda.computeIfAbsent(e.ordinal(), k -> new ArrayList<>()).add(e);
		}
		List<Map<String, Object>> salida = new ArrayList<>();
		for (List<Especimen> delaTanda : porTanda.values()) {
			Map<String, Object> renglon = new LinkedHashMap<>();
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
	private static List<Map<String, Object>> porMinuto(List<Especimen> esp) {
		List<Map<String, Object>> salida = new ArrayList<>();
		for (int minuto = 1; minuto <= MINUTOS; minuto++) {
			final int m = minuto;
			List<Especimen> con = esp.stream().filter(e -> segundosDelMinuto(e, m) > 0).toList();
			List<Long> segundos = con.stream().map(e -> segundosDelMinuto(e, m)).toList();
			Map<String, Object> fila = new LinkedHashMap<>();
			fila.put("minuto", minuto);
			fila.put("n", con.size());
			fila.put("segundosMin", segundos.isEmpty() ? null : segundos.stream().mapToLong(Long::longValue).min().getAsLong());
			fila.put("segundosMax", segundos.isEmpty() ? null : segundos.stream().mapToLong(Long::longValue).max().getAsLong());
			fila.put("incompleto", segundos.stream().anyMatch(s -> s < SEGUNDOS_POR_MINUTO));
			Map<String, Object> porConducta = new LinkedHashMap<>();
			for (String conducta : CONDUCTAS) {
				porConducta.put(conducta, estadisticos(con.stream().map(e -> e.porMinuto().getOrDefault(m, Map.of()).getOrDefault(conducta, 0L)).toList()));
			}
			fila.put("porConducta", porConducta);
			salida.add(fila);
		}
		return salida;
	}

	private static long segundosDelMinuto(Especimen e, int minuto) {
		return e.porMinuto().getOrDefault(minuto, Map.of()).values().stream().mapToLong(Long::longValue).sum();
	}

	// n, media, DE, varianza y coeficiente de variación de una lista de valores. La media y la DE a un decimal; el CV a tres
	// decimales (fracción: 0,250 = 25 %). Lo que no existe va null.
	static Map<String, Object> estadisticos(List<Long> valores) {
		int n = valores.size();
		Double media = Estadisticos.media(valores);
		Double varianza = n < 2 ? null : Estadisticos.varianza(valores);
		Double de = n < 2 ? null : Estadisticos.desviacion(valores);
		Double cv = (de == null || media == null || media == 0) ? null : de / media;
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("n", n);
		m.put("media", Estadisticos.redondear(media));
		m.put("de", Estadisticos.redondear(de));
		m.put("varianza", Estadisticos.redondear(varianza));
		m.put("cv", cv == null ? null : Math.round(cv * 1000.0) / 1000.0);
		return m;
	}

}
