// Mismo paquete que la clase que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ipn.mx.fst.service.TablaDeReporte.Estadistico;
import com.ipn.mx.fst.service.TablaDeReporte.FilaDeReporte;
import com.ipn.mx.fst.service.TablaDeReporte.Revisado;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// Encargo 39: el CSV (D-99) armado desde una TablaDeReporte, sin base de datos.
//
// Un CSV es texto plano, así que las pruebas comparan LÍNEAS COMPLETAS: es la forma más directa de fijar
// el formato exacto (separadores, orden de columnas, decimales) que espera quien abra el archivo.
// La segunda mitad de la clase prueba la defensa contra la inyección de fórmulas, que es una cuestión de
// seguridad y no de formato (ver ReporteCsv).
class ReporteCsvTest {

	// El encabezado esperado, en una constante: si alguien cambiara una columna de sitio, esta línea lo
	// delataría de inmediato.
	private static final String ENCABEZADO = "Grupo,Tanda,Espécimen,Tiempo (min:seg),Nado,Escalamiento,Inmovilidad,Conducta activa,Sin ver";

	// Los datos de prueba compartidos. No es private a propósito: ReporteXlsxTest los usa también, y es
	// justo lo que garantiza que los dos formatos se comprueben con los MISMOS números.
	// Los tres parámetros permiten variar lo que cada prueba necesita sin duplicar la tabla.
	static TablaDeReporte tabla(String grupo, boolean mixto, List<Revisado> revisados) {
		return new TablaDeReporte(grupo,
				// Tres filas: dos minutos del espécimen 1 de la tanda A y uno del espécimen 2 de la B.
				// Los cinco números van en el orden de COLUMNAS, con «sin ver» al final.
				List.of(new FilaDeReporte("A", 1, 1, List.of(10L, 20L, 30L, 0L, 0L)),
						new FilaDeReporte("A", 1, 2, List.of(5L, 0L, 40L, 1L, 14L)),
						new FilaDeReporte("B", 2, 1, List.of(0L, 0L, 60L, 0L, 0L))),
				// Cinco estadísticos, uno por columna, con valores distintos para poder distinguirlos.
				List.of(new Estadistico(35.5, 7.1, 50.0), new Estadistico(0.0, 0.0, 0.0), new Estadistico(12.3, 3.0, 9.0),
						new Estadistico(0.5, 0.7, 0.5), new Estadistico(4.7, 8.1, 65.3)),
				mixto, revisados);
	}

	// Las líneas del CSV sin el BOM; el último elemento es el vacío que queda tras el \r\n final.
	//
	// El substring(1) quita el BOM: son tres bytes, pero al leerlos como UTF-8 forman UN solo carácter.
	// El -1 del split conserva el último trozo vacío, que es lo que permite comprobar que el archivo
	// termina con un salto de línea (si se omitiera, split lo descartaría).
	private static List<String> lineas(byte[] csv) {
		String texto = new String(csv, StandardCharsets.UTF_8);
		return Arrays.asList(texto.substring(1).split("\r\n", -1));
	}

	@Test
	void empieza_con_el_BOM_de_UTF8_y_las_lineas_terminan_en_CRLF() {
		byte[] csv = ReporteCsv.armar(tabla("Control", false, List.of()));
		// Los tres bytes del BOM, comprobados uno a uno: sin ellos, Excel en Windows muestra los acentos
		// rotos (ver ReporteCsv).
		assertEquals((byte) 0xEF, csv[0]);
		assertEquals((byte) 0xBB, csv[1]);
		assertEquals((byte) 0xBF, csv[2]);
		String texto = new String(csv, StandardCharsets.UTF_8);
		assertTrue(texto.endsWith("\r\n"));
		// Quitando los \r\n no debe quedar ningún \n suelto: todas las líneas usan el final de RFC 4180.
		assertFalse(texto.replace("\r\n", "").contains("\n"));
		// Los acentos del encabezado salen en UTF-8, no como signos de interrogación.
		assertTrue(texto.contains("Espécimen"));
	}

	@Test
	void el_encabezado_es_exactamente_el_de_D_99() {
		assertEquals(ENCABEZADO, lineas(ReporteCsv.armar(tabla("Control", false, List.of()))).get(0));
	}

	@Test
	void sin_ver_es_la_ultima_columna_despues_de_conducta_activa() {
		// La columna «sin ver» es la que más fácil se olvida, y sin ella los minutos no sumarían 60.
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", false, List.of())));
		assertTrue(lineas.get(0).endsWith("Conducta activa,Sin ver"));
		// En una fila de datos (los 14 segundos sin ver del segundo minuto)...
		assertTrue(lineas.get(2).endsWith(",14"));
		// ...y en una de estadísticos (la varianza de esa columna).
		assertTrue(lineas.get(6).endsWith(",65.3"));
	}

	@Test
	void una_fila_por_especimen_y_minuto_con_segundos_enteros() {
		// Las tres líneas completas: fija el formato del tiempo (0:00-1:00), el nombre del espécimen y
		// que los segundos salgan como enteros, sin decimales.
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", false, List.of())));
		assertEquals("Control,A,Espécimen 1,0:00-1:00,10,20,30,0,0", lineas.get(1));
		assertEquals("Control,A,Espécimen 1,1:00-2:00,5,0,40,1,14", lineas.get(2));
		assertEquals("Control,B,Espécimen 2,0:00-1:00,0,0,60,0,0", lineas.get(3));
	}

	@Test
	void despues_de_las_filas_van_media_desviacion_y_varianza_con_un_decimal_y_punto() {
		// Las tres comas iniciales son las columnas vacías que dejan la etiqueta bajo "Espécimen".
		// El PUNTO decimal es lo importante: con coma, el separador de columnas rompería el archivo
		// (de ahí el Locale.ROOT de ReporteCsv).
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", false, List.of())));
		assertEquals(",,Media,,35.5,0.0,12.3,0.5,4.7", lineas.get(4));
		assertEquals(",,Desviación estándar,,7.1,0.0,3.0,0.7,8.1", lineas.get(5));
		assertEquals(",,Varianza,,50.0,0.0,9.0,0.5,65.3", lineas.get(6));
	}

	@Test
	void sin_revision_la_ultima_fila_dice_No_y_sin_mezcla_no_hay_aviso() {
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", false, List.of())));
		assertEquals(",,Revisión humana,No", lineas.get(7));
		assertEquals(9, lineas.size());      // encabezado + 3 filas + 3 estadísticos + revisión + el vacío final
		// Contar las líneas comprueba que NO sobra ninguna: sin esto, una fila de aviso de más pasaría
		// desapercibida.
		assertEquals("", lineas.get(8));
	}

	@Test
	void con_revision_lista_los_especimenes_revisados() {
		// El texto lo arma TablaDeReporte.textoDeRevision, con el "; " entre especímenes.
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", false,
				List.of(new Revisado("A", 2), new Revisado("B", 1)))));
		assertEquals(",,Revisión humana,Sí: Tanda A · Espécimen 2; Tanda B · Espécimen 1", lineas.get(7));
	}

	@Test
	void con_niveles_mezclados_agrega_la_fila_de_aviso() {
		// El aviso de que el grupo mezcla niveles de clasificación, y que por tanto sus columnas no son
		// comparables entre sí.
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", true, List.of())));
		assertEquals(",,Aviso,Mezcla tandas de clasificación precisa y agrupada", lineas.get(8));
		assertEquals(10, lineas.size());
	}

	@Test
	void entrecomilla_y_escapa_segun_RFC_4180() {
		// Las tres reglas de escape de la norma, una por línea:
		// un texto normal no se toca;
		assertEquals("normal", ReporteCsv.celda("normal"));
		// con coma, va entre comillas (si no, partiría la fila en dos columnas);
		assertEquals("\"con, coma\"", ReporteCsv.celda("con, coma"));
		// las comillas de dentro se DUPLICAN y el todo va entre comillas;
		assertEquals("\"dice \"\"hola\"\"\"", ReporteCsv.celda("dice \"hola\""));
		// y un salto de línea también obliga a entrecomillar.
		assertEquals("\"dos\nlineas\"", ReporteCsv.celda("dos\nlineas"));

		// Y el caso real: un nombre de grupo con coma y comillas, dentro del archivo completo.
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Grupo, \"uno\"", false, List.of())));
		assertEquals("\"Grupo, \"\"uno\"\"\",A,Espécimen 1,0:00-1:00,10,20,30,0,0", lineas.get(1));
	}

	// ----------------------------------------------------------------- fórmulas (encargo 40)
	//
	// A partir de aquí, la defensa contra la INYECCIÓN DE FÓRMULAS: Excel evalúa una celda de CSV que
	// empiece por =, +, -, @, tabulador o retorno, y el nombre del grupo lo escribe el usuario. Con
	// fórmulas elaboradas se puede llegar a ejecutar cosas en la máquina de quien abre el archivo.
	// La defensa es un apóstrofo delante, que obliga a Excel a tratar la celda como texto.

	@Test
	void un_grupo_que_empieza_con_igual_mas_menos_arroba_tabulador_o_retorno_lleva_apostrofo() {
		// Los seis caracteres peligrosos, uno por línea.
		assertEquals("'=Control", ReporteCsv.texto("=Control"));
		assertEquals("'+1", ReporteCsv.texto("+1"));
		assertEquals("'-x", ReporteCsv.texto("-x"));
		assertEquals("'@SUMA", ReporteCsv.texto("@SUMA"));
		assertEquals("'\tx", ReporteCsv.texto("\tx"));
		// El retorno obliga además a las comillas de RFC 4180.
		// Las dos protecciones se combinan: el apóstrofo DENTRO de las comillas.
		assertEquals("\"'\rx\"", ReporteCsv.texto("\rx"));

		// Y en el archivo completo, que es lo que de verdad llega al usuario.
		assertEquals("'=Control,A,Espécimen 1,0:00-1:00,10,20,30,0,0", lineas(ReporteCsv.armar(tabla("=Control", false, List.of()))).get(1));
		assertEquals("'@SUMA,A,Espécimen 1,0:00-1:00,10,20,30,0,0", lineas(ReporteCsv.armar(tabla("@SUMA", false, List.of()))).get(1));
	}

	@Test
	void un_texto_normal_o_con_igual_en_medio_no_cambia() {
		// La contraparte: solo importa el PRIMER carácter. Poner el apóstrofo a todo ensuciaría el
		// archivo y haría que "a=b" se leyera como "'a=b".
		assertEquals("Control", ReporteCsv.texto("Control"));
		assertEquals("a=b", ReporteCsv.texto("a=b"));
		assertEquals("x+y-z@w", ReporteCsv.texto("x+y-z@w"));
		// El texto vacío tampoco: charAt(0) sobre un texto vacío lanzaría una excepción, y por eso
		// ReporteCsv.texto comprueba isEmpty primero.
		assertEquals("", ReporteCsv.texto(""));
		assertEquals("a=b,A,Espécimen 1,0:00-1:00,10,20,30,0,0", lineas(ReporteCsv.armar(tabla("a=b", false, List.of()))).get(1));
	}

	@Test
	void el_apostrofo_va_antes_de_las_comillas() {
		// El ORDEN de las dos protecciones. Al revés ("'\"=A,1\"") el apóstrofo quedaría fuera de la
		// celda y Excel evaluaría la fórmula igualmente.
		assertEquals("\"'=A,1\"", ReporteCsv.texto("=A,1"));
		// Y con las dos cosas a la vez: fórmula, coma y comillas internas.
		assertEquals("\"'=\"\"x\"\",1\"", ReporteCsv.texto("=\"x\",1"));
		assertEquals("\"'=A,1\",A,Espécimen 1,0:00-1:00,10,20,30,0,0", lineas(ReporteCsv.armar(tabla("=A,1", false, List.of()))).get(1));
	}

	@Test
	void los_numeros_nunca_llevan_apostrofo() {
		// La otra mitad de la defensa: un número negativo empieza por "-", y con apóstrofo dejaría de ser
		// un número para Excel. Por eso ReporteCsv distingue por CÓMO se arma la celda, no por su contenido.
		List<String> lineas = lineas(ReporteCsv.armar(tabla("=Control", true, List.of(new Revisado("A", 2)))));
		// Segundos y estadísticos sin apóstrofo, y las etiquetas de la columna Espécimen (texto) intactas porque no empiezan así.
		assertEquals("'=Control,A,Espécimen 1,0:00-1:00,10,20,30,0,0", lineas.get(1));
		assertEquals(",,Media,,35.5,0.0,12.3,0.5,4.7", lineas.get(4));
		assertEquals(",,Varianza,,50.0,0.0,9.0,0.5,65.3", lineas.get(6));
		// Y un barrido de TODAS las celdas numéricas (de la columna 4 en adelante) de las seis primeras
		// líneas: ninguna debe empezar con apóstrofo. El segundo argumento de assertFalse imprime la línea
		// entera si falla, para poder verla.
		for (int i = 1; i <= 6; i++) {
			String[] celdas = lineas.get(i).split(",");
			for (int c = 4; c < celdas.length; c++) {
				assertFalse(celdas[c].startsWith("'"), lineas.get(i));
			}
		}
	}

	@Test
	void ninguna_celda_de_texto_del_archivo_empieza_como_formula() {
		// Con un grupo "=Control" y todas las filas finales (revisión y aviso incluidos), las cuatro primeras columnas son texto
		// y las demás números.
		//
		// Es la prueba de BARRIDO, la más valiosa de la sección: en vez de comprobar celdas concretas,
		// recorre TODAS las de texto del archivo y exige que ninguna empiece por un carácter peligroso.
		// Si alguien añadiera una fila nueva olvidando pasarla por texto(), esta prueba la atraparía
		// aunque nadie escribiera una comprobación para ella.
		List<String> lineas = lineas(ReporteCsv.armar(tabla("=Control", true, List.of(new Revisado("A", 2)))));
		// subList deja fuera la última línea, que es el vacío del CRLF final.
		for (String linea : lineas.subList(0, lineas.size() - 1)) {
			String[] celdas = linea.split(",", -1);
			for (int c = 0; c < Math.min(4, celdas.length); c++) {
				assertFalse(!celdas[c].isEmpty() && "=+-@\t\r".indexOf(celdas[c].charAt(0)) >= 0, linea);
			}
		}
	}

	// Modo desarrollador: con qué modelo se hizo cada análisis, una fila por análisis al final.
	// Se parte de la tabla compartida y se reconstruye con la lista de modelos: así solo cambia eso.
	private static TablaDeReporte conModelos(List<String> modelos) {
		TablaDeReporte base = tabla("Control", false, List.of());
		return new TablaDeReporte(base.grupo(), base.filas(), base.estadisticos(), false, base.revisados(), modelos);
	}

	@Test
	void sin_modelos_el_archivo_no_trae_filas_de_modelo() {
		// Fuera del modo desarrollador, el CSV sale exactamente como siempre.
		List<String> lineas = lineas(ReporteCsv.armar(tabla("Control", false, List.of())));
		assertFalse(lineas.stream().anyMatch(linea -> linea.startsWith(",,Modelo,")));
	}

	@Test
	void en_modo_desarrollador_cada_modelo_va_en_su_fila_despues_de_la_revision() {
		List<String> lineas = lineas(ReporteCsv.armar(conModelos(
				List.of("Tanda A: fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5", "Tanda B: sin registro"))));
		// Se busca el índice de la revisión y se comprueba lo que viene DESPUÉS, en vez de escribir
		// números de línea fijos: así la prueba no se rompe si se añade una fila antes.
		int revision = lineas.indexOf(",,Revisión humana,No");
		assertEquals(",,Modelo,Tanda A: fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5", lineas.get(revision + 1));
		// Una fila por análisis, incluido el que no tiene registro del modelo.
		assertEquals(",,Modelo,Tanda B: sin registro", lineas.get(revision + 2));
		assertEquals("", lineas.get(revision + 3));      // lo último es el vacío que deja el CRLF final
	}

	@Test
	void el_aviso_de_niveles_mixtos_va_antes_que_los_modelos() {
		// El orden del final: revisión, aviso y después los modelos.
		TablaDeReporte base = tabla("Control", true, List.of());
		List<String> lineas = lineas(ReporteCsv.armar(new TablaDeReporte(base.grupo(), base.filas(), base.estadisticos(), true,
				base.revisados(), List.of("Tanda A: sin registro"))));
		int aviso = lineas.indexOf(",,Aviso," + TablaDeReporte.TEXTO_AVISO);
		assertEquals(",,Modelo,Tanda A: sin registro", lineas.get(aviso + 1));
	}

}
