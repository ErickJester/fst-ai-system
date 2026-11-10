// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// El enum de las cuatro conductas; se usa para sacar sus textos exactos de la base.
import com.ipn.mx.fst.domain.valores.TipoConducta;
import java.util.ArrayList;
// LinkedHashMap: conserva el orden de las claves, para que el JSON salga ordenado.
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
// "Conducta activa" en un segundo (el nivel agrupado: sale de la regla de 0.80 o de la revisión humana) cuenta como una conducta
// más: no se suma a nado ni a escalamiento.
//
// De dónde viene esto: la prueba de nado forzado se puntúa tradicionalmente a mano en bloques de cinco
// segundos, apuntando la conducta predominante de cada bloque. El sistema mide segundo a segundo, que es
// más fino, pero tiene que poder dar también el formato clásico para que los resultados se puedan
// comparar con la literatura y con lo que el laboratorio hacía antes.
// Por qué el empate se resuelve a "conducta activa": es la opción conservadora. Ante la duda entre nado y
// escalamiento, afirmar cualquiera de los dos sería inventar precisión que no se tiene; "conducta activa"
// dice lo único que consta, que el animal se estaba moviendo. Y el matiz avisa de que el bloque tenía
// segundos sin ver, para que quien lo lea sepa que es menos firme que los demás.
// "La regla, sin base de datos" otra vez significa clase pura: se prueba con una lista escrita a mano
// (lo hace BloquesDeCincoTest), sin levantar PostgreSQL.
final class BloquesDeCinco {

	static final int SEGUNDOS_POR_BLOQUE = 5;

	// Los textos exactos de la base, sacados del enum y no escritos a mano: si el dominio cambiara, esto
	// cambia con él y no se queda una cadena suelta desfasada.
	private static final String NADO = TipoConducta.NADO_ACTIVO.valor();
	private static final String INMOVILIDAD = TipoConducta.INMOVILIDAD.valor();
	private static final String ESCALAMIENTO = TipoConducta.ESCALAMIENTO.valor();
	private static final String ACTIVA = TipoConducta.CONDUCTA_ACTIVA.valor();

	// Impide instanciarla: es una caja de métodos estáticos.
	private BloquesDeCinco() {
	}

	// La conducta que gana y si lleva matiz.
	// Dos datos que salen juntos de la misma decisión, de ahí que vayan en un record en vez de calcularse
	// por separado recorriendo dos veces la lista.
	record Decision(String conducta, boolean matiz) {
	}

	static Decision decidir(List<String> segundos) {
		// Un arreglo de cuatro contadores, uno por conducta, en vez de un mapa: son cuatro posiciones
		// fijas y conocidas, y así el conteo es un simple incremento.
		int[] cuenta = new int[4];
		boolean sinVer = false;
		for (String conducta : segundos) {
			// null no es una conducta: es "no se vio al espécimen". No se cuenta, solo se recuerda.
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
				// Un texto que no es ninguna de las cuatro significa que la base y el código ya no
				// coinciden. Es un error de programación, no del usuario, y por eso se lanza en vez de
				// ignorarlo: contarlo como nada falsearía el resultado en silencio.
				throw new IllegalArgumentException("Conducta desconocida: " + conducta);
			}
		}
		// El conteo más alto de los cuatro.
		int tope = 0;
		for (int c : cuenta) {
			tope = Math.max(tope, c);
		}
		// tope == 0 significa que no se contó ni un segundo, es decir, que todos eran null.
		// El matiz es true porque, en efecto, hubo segundos sin ver.
		if (tope == 0) {
			return new Decision(ACTIVA, true);
		}
		// Los nombres en el MISMO orden que los contadores de arriba; el índice los relaciona.
		String[] nombres = {NADO, INMOVILIDAD, ESCALAMIENTO, ACTIVA};
		String ganadora = null;
		int empatadas = 0;
		// Se recorre contando cuántas conductas llegaron al tope. Si solo una, esa gana.
		for (int i = 0; i < cuenta.length; i++) {
			if (cuenta[i] == tope) {
				ganadora = nombres[i];
				empatadas++;
			}
		}
		// Aquí se aplican las dos reglas de una vez: con una sola en el tope, gana ella; con dos o más
		// empatadas, "conducta activa", que es la opción que no afirma de más.
		return new Decision(empatadas == 1 ? ganadora : ACTIVA, sinVer);
	}

	// Los bloques de un espécimen a partir de la conducta de cada segundo (índice 0 = segundo 1; null = no se vio). El último
	// bloque de un video que no mide un múltiplo de 5 s trae solo los segundos que existen (menos de cinco en "segundos"), se decide
	// con esos y los segundos que no existen NO cuentan como sin ver (no dan matiz).
	//
	// Esa última distinción es fina y correcta: un segundo que NO EXISTE (el video se acabó) no es lo
	// mismo que un segundo en el que no se vio al espécimen. Solo el segundo caso es información sobre la
	// medición; el primero es solo el final del video, y marcar matiz por eso sería un aviso falso.
	static List<Map<String, Object>> armar(List<String> porSegundo) {
		List<Map<String, Object>> bloques = new ArrayList<>();
		// El bucle avanza de cinco en cinco.
		for (int desde = 0; desde < porSegundo.size(); desde += SEGUNDOS_POR_BLOQUE) {
			// Math.min corta el último bloque en el final de la lista, que es lo que hace que un bloque
			// incompleto traiga solo los segundos que existen y no se pida fuera de rango.
			// Se copia a un ArrayList nuevo porque subList devuelve una vista sobre la lista original:
			// guardarla dejaría los bloques atados a ella.
			List<String> segundos = new ArrayList<>(
					porSegundo.subList(desde, Math.min(desde + SEGUNDOS_POR_BLOQUE, porSegundo.size())));
			Decision decision = decidir(segundos);
			Map<String, Object> bloque = new LinkedHashMap<>();
			// +1 porque el índice empieza en 0 y los segundos se numeran desde 1 (ver SegundoId).
			bloque.put("inicio", desde + 1);
			bloque.put("conducta", decision.conducta());
			bloque.put("matiz", decision.matiz());
			// Los segundos originales viajan también: la pantalla puede enseñar el detalle de cada bloque
			// sin pedir nada más, y se puede comprobar de dónde salió la decisión.
			bloque.put("segundos", segundos);
			bloques.add(bloque);
		}
		return bloques;
	}

}
