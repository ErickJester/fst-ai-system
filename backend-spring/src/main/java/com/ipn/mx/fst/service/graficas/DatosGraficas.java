package com.ipn.mx.fst.service.graficas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Lectura del mapa de GraficasDeResultados (el mismo que consume la pantalla). Aquí no se calcula nada: solo se leen los
// números y se escriben con coma decimal, como en la pantalla.
public final class DatosGraficas {

	public static final List<String> CONDUCTAS_TRES = List.of("nado activo", "inmovilidad", "escalamiento");
	public static final String CONDUCTA_ACTIVA = "conducta activa";
	public static final int MINUTOS = 5;

	private DatosGraficas() {
	}

	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> grupos(Map<String, Object> datos) {
		Object grupos = datos == null ? null : datos.get("grupos");
		return grupos == null ? List.of() : (List<Map<String, Object>>) grupos;
	}

	// Un grupo tiene datos si no mezcla niveles y tiene especímenes con Día 2.
	public static boolean tieneDatos(Map<String, Object> grupo) {
		return !bool(grupo.get("nivelMixto")) && mapa(grupo.get("porConducta")) != null && entero(grupo.get("n")) > 0;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> mapa(Object valor) {
		return valor instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
	}

	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> lista(Object valor) {
		return valor instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
	}

	// Los estadísticos de una conducta de un grupo (o de un minuto): n, media, de, varianza, cv.
	public static Map<String, Object> estadisticos(Map<String, Object> grupoOMinuto, String conducta) {
		Map<String, Object> porConducta = mapa(grupoOMinuto.get("porConducta"));
		return porConducta == null ? Map.of() : mapa(porConducta.get(conducta));
	}

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

	public static Double dbl(Object valor) {
		return valor instanceof Number n ? n.doubleValue() : null;
	}

	public static int entero(Object valor) {
		return valor instanceof Number n ? n.intValue() : 0;
	}

	public static boolean bool(Object valor) {
		return Boolean.TRUE.equals(valor);
	}

	// Un decimal con coma; lo que no existe no se escribe como cero.
	public static String numero(Double valor) {
		return valor == null ? null : String.format(Locale.ROOT, "%.1f", valor).replace('.', ',');
	}

	// «12,3 ± 2,1 s»; sin DE, «12,3 s ± no aplica»; sin media, «sin datos».
	public static String mediaDe(Double media, Double de, String unidad) {
		if (media == null) {
			return "sin datos";
		}
		return de == null ? numero(media) + unidad + " ± no aplica" : numero(media) + " ± " + numero(de) + unidad;
	}

	// «n = 3» o, con tandas pendientes, «n = 2 de 4».
	public static String textoN(Map<String, Object> grupo) {
		int n = entero(grupo.get("n"));
		int total = entero(grupo.get("nTotal"));
		return n < total ? "n = " + n + " de " + total : "n = " + n;
	}

	// La escala de segundos de G1: 300 s, la ventana del Día 2; crece en múltiplos de 30 s solo si un grupo la rebasa
	// con su media + DE o con un espécimen. Es la misma regla que la pantalla.
	public static double escalaSegundos(Map<String, Object> datos, List<String> conductas) {
		double mayor = 0;
		for (Map<String, Object> g : grupos(datos)) {
			if (!tieneDatos(g)) {
				continue;
			}
			for (String c : conductas) {
				Map<String, Object> e = estadisticos(g, c);
				Double media = dbl(e.get("media"));
				Double de = dbl(e.get("de"));
				if (media != null) {
					mayor = Math.max(mayor, media + (de == null ? 0 : de));
				}
				for (Double v : valoresS(e)) {
					if (v != null) {
						mayor = Math.max(mayor, v);
					}
				}
			}
		}
		return mayor <= 300 ? 300 : Math.ceil(mayor / 30) * 30;
	}
}
