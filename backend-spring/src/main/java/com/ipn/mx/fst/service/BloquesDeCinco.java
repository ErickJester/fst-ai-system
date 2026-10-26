package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.valores.TipoConducta;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Bloques de 5 s de un espécimen (R-12, D-53): la regla, sin base de datos. Se calculan al pedirlos desde SEGUNDO y no se guardan
// (D-52). Los nombres de conducta son los de la base ("nado activo", "inmovilidad", "escalamiento", "conducta activa").
//
// Regla de un bloque, sobre los segundos que tiene (null = no se vio al espécimen):
//  - gana la conducta con más segundos;
//  - si hay empate arriba, el bloque es "conducta activa": nado activo contra escalamiento, inmovilidad contra una conducta activa
//    (nado, escalamiento o la propia "conducta activa") o dos activas entre sí;
//  - si ningún segundo se vio, "conducta activa";
//  - matiz = true cuando algún segundo del bloque no se vio (también si ninguno se vio).
// "Conducta activa" en un segundo (el nivel agrupado del clasificador) cuenta como una conducta más: no se suma a nado ni a
// escalamiento.
final class BloquesDeCinco {

	static final int SEGUNDOS_POR_BLOQUE = 5;

	private static final String NADO = TipoConducta.NADO_ACTIVO.valor();
	private static final String INMOVILIDAD = TipoConducta.INMOVILIDAD.valor();
	private static final String ESCALAMIENTO = TipoConducta.ESCALAMIENTO.valor();
	private static final String ACTIVA = TipoConducta.CONDUCTA_ACTIVA.valor();

	private BloquesDeCinco() {
	}

	// La conducta que gana y si lleva matiz.
	record Decision(String conducta, boolean matiz) {
	}

	static Decision decidir(List<String> segundos) {
		int[] cuenta = new int[4];
		boolean sinVer = false;
		for (String conducta : segundos) {
			if (conducta == null) {
				sinVer = true;
			} else if (NADO.equals(conducta)) {
				cuenta[0]++;
			} else if (INMOVILIDAD.equals(conducta)) {
				cuenta[1]++;
			} else if (ESCALAMIENTO.equals(conducta)) {
				cuenta[2]++;
			} else if (ACTIVA.equals(conducta)) {
				cuenta[3]++;
			} else {
				throw new IllegalArgumentException("Conducta desconocida: " + conducta);
			}
		}
		int tope = 0;
		for (int c : cuenta) {
			tope = Math.max(tope, c);
		}
		if (tope == 0) {
			return new Decision(ACTIVA, true);
		}
		String[] nombres = {NADO, INMOVILIDAD, ESCALAMIENTO, ACTIVA};
		String ganadora = null;
		int empatadas = 0;
		for (int i = 0; i < cuenta.length; i++) {
			if (cuenta[i] == tope) {
				ganadora = nombres[i];
				empatadas++;
			}
		}
		return new Decision(empatadas == 1 ? ganadora : ACTIVA, sinVer);
	}

	// Los bloques de un espécimen a partir de la conducta de cada segundo (índice 0 = segundo 1; null = no se vio). El último
	// bloque de un video que no mide un múltiplo de 5 s trae solo los segundos que existen (menos de cinco en "segundos"), se decide
	// con esos y los segundos que no existen NO cuentan como sin ver (no dan matiz).
	static List<Map<String, Object>> armar(List<String> porSegundo) {
		List<Map<String, Object>> bloques = new ArrayList<>();
		for (int desde = 0; desde < porSegundo.size(); desde += SEGUNDOS_POR_BLOQUE) {
			List<String> segundos = new ArrayList<>(
					porSegundo.subList(desde, Math.min(desde + SEGUNDOS_POR_BLOQUE, porSegundo.size())));
			Decision decision = decidir(segundos);
			Map<String, Object> bloque = new LinkedHashMap<>();
			bloque.put("inicio", desde + 1);
			bloque.put("conducta", decision.conducta());
			bloque.put("matiz", decision.matiz());
			bloque.put("segundos", segundos);
			bloques.add(bloque);
		}
		return bloques;
	}

}
