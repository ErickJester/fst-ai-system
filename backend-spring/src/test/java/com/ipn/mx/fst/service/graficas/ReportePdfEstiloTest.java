package com.ipn.mx.fst.service.graficas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ipn.mx.fst.domain.Experimento;
import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.repository.ExperimentoRepository;
import com.ipn.mx.fst.repository.FilaEspecimenRevisado;
import com.ipn.mx.fst.repository.FilaResultado;
import com.ipn.mx.fst.repository.SegundoRepository;
import com.ipn.mx.fst.service.ExperimentoServiceImpl;
import com.ipn.mx.fst.service.ResultadosService;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.SimpleBookmark;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// El reporte de resultados completo con el estilo del sistema, armado con datos inventados y sin base de datos: la tipografía
// (Archivo incrustada, sin Helvetica), el encabezado y el pie de cada página, las etiquetas de los datos del experimento y los
// marcadores. Las pruebas con la base de datos real están en ReportesCsvXlsxTest.
class ReportePdfEstiloTest {

	private static byte[] pdf;

	private static FilaResultado fila(String grupo, int ordinal, int cilindro, int minuto, String conducta, double segundos) {
		FilaResultado f = mock(FilaResultado.class);
		when(f.getGrupo()).thenReturn(grupo);
		when(f.getOrdinal()).thenReturn(ordinal);
		when(f.getCilindro()).thenReturn(cilindro);
		when(f.getMinuto()).thenReturn(minuto);
		when(f.getConducta()).thenReturn(conducta);
		when(f.getSegundos()).thenReturn(BigDecimal.valueOf(segundos));
		return f;
	}

	private static Map<String, Object> mapa(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}

	@BeforeAll
	static void armarElReporte() {
		Usuario creador = new Usuario();
		creador.setIdInstitucional("2022630188");
		creador.setNombre("Vanesa");
		creador.setApellidos("Rodriguez Verdin");
		Experimento experimento = new Experimento();
		experimento.setNombre("Prueba Acu-YBN");
		experimento.setFecha(LocalDate.of(2026, 11, 8));
		experimento.setNotas("pruebas");
		experimento.setUsuario(creador);

		List<FilaResultado> filas = new ArrayList<>();
		String[] grupos = {"Control", "acu yn"};
		String[] conductas = {"nado activo", "escalamiento", "inmovilidad", "conducta activa"};
		for (String g : grupos) {
			for (int cil = 1; cil <= 2; cil++) {
				for (int minuto = 1; minuto <= 5; minuto++) {
					double[] v = {20 + cil + minuto, 5 + cil, 28 - minuto, 4};
					for (int c = 0; c < conductas.length; c++) {
						filas.add(fila(g, 1, cil, minuto, conductas[c], v[c]));
					}
				}
			}
		}
		FilaEspecimenRevisado revisado = mock(FilaEspecimenRevisado.class);
		when(revisado.getGrupo()).thenReturn("Control");
		when(revisado.getOrdinal()).thenReturn(1);
		when(revisado.getCilindro()).thenReturn(1);

		ExperimentoRepository experimentos = mock(ExperimentoRepository.class);
		when(experimentos.findById(anyInt())).thenReturn(Optional.of(experimento));
		SegundoRepository segundos = mock(SegundoRepository.class);
		when(segundos.buscarResultados(anyInt())).thenReturn(filas);
		when(segundos.buscarRevisados(anyInt())).thenReturn(List.of(revisado));

		List<Map<String, Object>> especimenes = new ArrayList<>();
		for (String g : grupos) {
			for (int cil = 1; cil <= 2; cil++) {
				especimenes.add(mapa("tanda", g + " · Tanda A", "numeroCilindro", cil, "totales", mapa(
						"nado activo", mapa("segundos", 127, "porcentaje", 42.3),
						"inmovilidad", mapa("segundos", 120, "porcentaje", 40.0),
						"escalamiento", mapa("segundos", 40, "porcentaje", 13.3),
						"conducta activa", mapa("segundos", 13, "porcentaje", 4.3),
						"sin ver", mapa("segundos", 0, "porcentaje", 0.0))));
			}
		}
		List<Map<String, Object>> tiempos = List.of(
				mapa("grupo", "Control", "tanda", "A", "nombreVideo", "PCPA-FISIOL-5min.MOV", "duracionVideo", 301,
						"tiempos", mapa("totalSegundos", 95, "etapas", List.of(
								mapa("etapa", "preprocesamiento", "segundos", 12), mapa("etapa", "cilindros", "segundos", 20),
								mapa("etapa", "clasificacion", "segundos", 63)),
								"equipo", mapa("nombre", "PC de Erick", "nucleos", 8),
								"modelo", mapa("archivo", "fst_cnn_seg.pt", "version", "5.0.0",
										"sha256", "11f044185734f8494415549defa872f1b923886081857331c685f0231cffa32e",
										"tamanoMB", 119.6, "modificado", "2026-11-09T01:50:00-06:00"))),
				mapa("grupo", "acu yn", "tanda", "A", "nombreVideo", "AcuYN1.MOV", "duracionVideo", 359, "tiempos", null));

		ResultadosService resultados = mock(ResultadosService.class);
		when(resultados.videosAnalizados(anyInt())).thenReturn(List.of(
				mapa("grupo", "Control", "tanda", "A", "nombreVideo", "PCPA-FISIOL-5min.MOV", "duracionVideo", 301),
				mapa("grupo", "acu yn", "tanda", "A", "nombreVideo", "AcuYN1.MOV", "duracionVideo", 359)));
		when(resultados.graficas(anyInt())).thenReturn(GraficasPdfTest.datosDeVariosGrupos());
		when(resultados.resultados(anyInt())).thenReturn(mapa("especimenes", especimenes));
		when(resultados.tiemposDelExperimento(anyInt())).thenReturn(tiempos);

		ExperimentoServiceImpl servicio = new ExperimentoServiceImpl();
		ReflectionTestUtils.setField(servicio, "experimentoRepository", experimentos);
		ReflectionTestUtils.setField(servicio, "segundoRepository", segundos);
		ReflectionTestUtils.setField(servicio, "resultadosService", resultados);
		ReflectionTestUtils.setField(servicio, "versionApp", "1.16.0");
		pdf = servicio.generarReporteDeResultados(7).pdf();
	}

	private static String texto(int pagina) throws Exception {
		PdfReader lector = new PdfReader(pdf);
		try {
			return PdfTextExtractor.getTextFromPage(lector, pagina);
		} finally {
			lector.close();
		}
	}

	private static String todoElTexto() throws Exception {
		PdfReader lector = new PdfReader(pdf);
		try {
			StringBuilder sb = new StringBuilder();
			for (int p = 1; p <= lector.getNumberOfPages(); p++) {
				sb.append(PdfTextExtractor.getTextFromPage(lector, p)).append('\n');
			}
			return sb.toString();
		} finally {
			lector.close();
		}
	}

	@Test
	void toda_la_letra_es_archivo_incrustada_y_no_queda_helvetica() throws Exception {
		PdfReader lector = new PdfReader(pdf);
		try {
			Set<String> fuentes = new TreeSet<>();
			for (int p = 1; p <= lector.getNumberOfPages(); p++) {
				PdfDictionary recursos = lector.getPageN(p).getAsDict(PdfName.RESOURCES);
				PdfDictionary lista = recursos == null ? null : recursos.getAsDict(PdfName.FONT);
				if (lista == null) {
					continue;
				}
				for (PdfName nombre : lista.getKeys()) {
					PdfName base = lista.getAsDict(nombre).getAsName(PdfName.BASEFONT);
					fuentes.add(base == null ? "?" : base.toString());
				}
			}
			assertFalse(fuentes.isEmpty());
			for (String fuente : fuentes) {
				assertTrue(fuente.contains("Archivo"), "una fuente que no es Archivo: " + fuente + " en " + fuentes);
			}
		} finally {
			lector.close();
		}
	}

	@Test
	void los_datos_del_experimento_siguen_diciendo_etiqueta_dos_puntos_valor() throws Exception {
		String texto = texto(1);
		assertTrue(texto.contains("Reporte de resultados"), texto);
		assertTrue(texto.contains("Experimento: Prueba Acu-YBN"), texto);
		assertTrue(texto.contains("Fecha: 2026-11-08"), texto);
		assertTrue(texto.contains("Creado por: Vanesa Rodriguez Verdin (2022630188)"), texto);
		assertTrue(texto.contains("Notas: pruebas"), texto);
		assertTrue(texto.contains("Revisión humana: Sí (1 espécimen con segundos corregidos)"), texto);
		assertTrue(texto.contains("Generado: "), texto);
	}

	@Test
	void cada_pagina_lleva_fst_las_migas_la_version_y_su_numero_con_el_total() throws Exception {
		PdfReader lector = new PdfReader(pdf);
		int paginas = lector.getNumberOfPages();
		lector.close();
		assertTrue(paginas >= 3, "se esperaban varias páginas: " + paginas);
		for (int p = 1; p <= paginas; p++) {
			String texto = texto(p);
			assertTrue(texto.contains("FST"), "página " + p + ": " + texto);
			assertTrue(texto.contains("Experimentos / Prueba Acu-YBN / Resultados"), "página " + p + ": " + texto);
			assertTrue(texto.contains("Reporte de resultados · Prueba Acu-YBN"), "página " + p + ": " + texto);
			assertTrue(texto.contains("v1.16.0"), "página " + p + ": " + texto);
			assertTrue(texto.contains("Página " + p + " de " + paginas), "página " + p + " de " + paginas + ": " + texto);
		}
	}

	@Test
	void las_secciones_y_tablas_conservan_sus_textos_y_los_marcadores_siguen_en_orden() throws Exception {
		String texto = todoElTexto();
		for (String parte : new String[] {"Videos analizados", "Resultados por grupo", "Tabla por espécimen", "Desglose por minuto",
				"Modelo del análisis", "Tiempos de análisis", "Modelo con el que se hizo cada análisis", "Sin ver", "revisado",
				"sin registro", "fst_cnn_seg.pt", "11f044185734f849", "no indica si una diferencia es significativa"}) {
			assertTrue(texto.contains(parte), "falta «" + parte + "»");
		}
		PdfReader lector = new PdfReader(pdf);
		try {
			List<HashMap<String, Object>> marcadores = SimpleBookmark.getBookmark(lector);
			List<String> titulos = new ArrayList<>();
			for (HashMap<String, Object> m : marcadores) {
				titulos.add(String.valueOf(m.get("Title")));
			}
			assertEquals(List.of("Resumen", "Resultados por grupo", "Gráficas", "Tabla por espécimen", "Desglose por minuto",
					"Modelo del análisis"), titulos);
		} finally {
			lector.close();
		}
	}

	@Test
	void el_titulo_de_graficas_no_queda_solo_al_pie_de_una_pagina() throws Exception {
		PdfReader lector = new PdfReader(pdf);
		int paginas = lector.getNumberOfPages();
		lector.close();
		for (int p = 1; p <= paginas; p++) {
			String texto = texto(p);
			if (texto.contains("Gráficas") && !texto.contains("Curva por minuto") && !texto.contains("Tiempo por conducta")) {
				throw new AssertionError("«Gráficas» sin ninguna gráfica en la misma página " + p + ": " + texto);
			}
		}
	}
}
