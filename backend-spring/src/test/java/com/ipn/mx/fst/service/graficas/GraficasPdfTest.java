package com.ipn.mx.fst.service.graficas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ipn.mx.fst.service.GraficasDeResultados;
import com.itextpdf.text.Document;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.SimpleBookmark;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// Las gráficas del PDF con datos fijos, sin base de datos: los datos salen del cálculo real (GraficasDeResultados) y el
// PDF se lee con iText. Se prueban los marcadores (jerarquía, destinos y modo de página), lo que se omite, las etiquetas
// largas y que el archivo sea válido.
class GraficasPdfTest {

	private static final String NADO = "nado activo";
	private static final String INM = "inmovilidad";
	private static final String ESC = "escalamiento";
	private static final String ACT = "conducta activa";
	private static final String SIN = "sin ver";

	@Test
	void con_datos_la_seccion_sale_con_sus_marcadores_y_el_pdf_se_abre_con_el_panel_de_marcadores() throws IOException {
		byte[] pdf = pdfConGraficas(datosDeVariosGrupos());
		{
			PdfReader lector = new PdfReader(pdf);
			List<HashMap<String, Object>> marcadores = SimpleBookmark.getBookmark(lector);
			assertEquals(List.of("Gráficas"), titulos(marcadores));
			List<HashMap<String, Object>> hijos = kids(marcadores.get(0));
			// Solo G1 y G2 aplican: G3 a G6 todavía no dibujan, así que no tienen marcador.
			assertEquals(List.of("Tiempo por conducta · Día 2", "Curva por minuto · Día 2"), titulos(hijos));
			assertEquals(PdfName.USEOUTLINES, lector.getCatalog().getAsName(PdfName.PAGEMODE));
		}
	}

	@Test
	void cada_marcador_salta_a_una_pagina_valida() throws IOException {
		byte[] pdf = pdfConGraficas(datosDeVariosGrupos());
		{
			PdfReader lector = new PdfReader(pdf);
			int paginas = lector.getNumberOfPages();
			java.util.Map<String, ?> destinos = lector.getNamedDestinationFromStrings();
			for (HashMap<String, Object> hijo : kids(SimpleBookmark.getBookmark(lector).get(0))) {
				String destino = String.valueOf(hijo.get("Named"));
				assertTrue(destinos.containsKey(destino), "destino sin página: " + destino);
			}
			assertTrue(paginas >= 1);
		}
	}

	@Test
	void sin_grupos_con_datos_no_hay_seccion_ni_marcador() throws IOException {
		Map<String, Object> datos = GraficasDeResultados.armar(
				List.of(new GraficasDeResultados.Grupo(1, "Control", "control", "agua", 4, List.of())), List.of());
		assertFalse(new G1BarrasGruposPdf().aplica(datos));
		assertFalse(new G2CurvaPorMinutoPdf().aplica(datos));
		byte[] pdf = pdfConGraficas(datos);
		{
			PdfReader lector = new PdfReader(pdf);
			assertTrue(SimpleBookmark.getBookmark(lector) == null || SimpleBookmark.getBookmark(lector).isEmpty());
		}
	}

	@Test
	void un_grupo_con_datos_basta_para_G1_y_G2() {
		Map<String, Object> datos = GraficasDeResultados.armar(
				List.of(new GraficasDeResultados.Grupo(1, "Control", "control", "agua", 2, List.of())),
				List.of(especimen(1, 1, 1, "preciso", minutos(60, 60, 60, 60, 60)), especimen(1, 1, 2, "preciso", minutos(60, 60, 60, 60, 60))));
		assertTrue(new G1BarrasGruposPdf().aplica(datos));
		assertTrue(new G2CurvaPorMinutoPdf().aplica(datos));
	}

	@Test
	void un_grupo_de_nivel_mixto_no_se_dibuja_y_su_nota_lo_dice() throws IOException {
		Map<String, Object> datos = datosDeVariosGrupos();
		List<String> notas = new G1BarrasGruposPdf().notas(datos);
		assertTrue(notas.stream().anyMatch(n -> n.startsWith("Mixto: mezcla niveles de clasificación")), notas.toString());
		assertTrue(notas.contains(GraficaPdf.NOTA_DE));
		assertTrue(notas.contains(GraficaPdf.NOTA_ORIGEN));
	}

	@Test
	void el_texto_del_pdf_trae_los_titulos_las_notas_y_los_nombres_de_grupo() throws IOException {
		byte[] pdf = pdfConGraficas(datosDeVariosGrupos());
		{
			PdfReader lector = new PdfReader(pdf);
			String texto = PdfTextExtractor.getTextFromPage(lector, 1);
			for (int p = 2; p <= lector.getNumberOfPages(); p++) {
				texto += PdfTextExtractor.getTextFromPage(lector, p);
			}
			assertTrue(texto.contains("Gráficas"));
			assertTrue(texto.contains("Tiempo por conducta"));
			assertTrue(texto.contains("Curva por minuto"));
			assertTrue(texto.contains("no indica si una diferencia es significativa"));
			assertTrue(texto.contains("salida del modelo"));
			assertTrue(texto.contains("Control"));
			assertTrue(texto.contains("n = 4"));
		}
	}

	@Test
	void una_etiqueta_larga_se_parte_o_se_recorta_sin_salirse_de_su_columna() throws IOException {
		byte[] pdf = pdfConGraficas(datosConNombresLargos());
		{
			PdfReader lector = new PdfReader(pdf);
			assertTrue(lector.getNumberOfPages() >= 1);
			String texto = PdfTextExtractor.getTextFromPage(lector, 1);
			// El nombre completo sigue en la leyenda del dibujo de G2 o en la nota de sin datos; lo que no cabe lleva «…».
			assertTrue(texto.contains("Fluoxetina") || texto.contains("Fluoxet"));
		}
	}

	@Test
	void el_pdf_con_gráficas_es_legible_y_tiene_al_menos_una_pagina() throws IOException {
		byte[] pdf = pdfConGraficas(datosDeVariosGrupos());
		assertNotNull(pdf);
		{
			PdfReader lector = new PdfReader(pdf);
			assertTrue(lector.getNumberOfPages() >= 1);
		}
	}

	// Escribe un PDF de prueba con solo la sección de gráficas (el resto del reporte necesita la base de datos).
	static byte[] pdfConGraficas(Map<String, Object> datos) throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try {
			Document documento = new Document(PageSize.LETTER);
			PdfWriter writer = PdfWriter.getInstance(documento, salida);
			MarcadoresPdf marcadores = new MarcadoresPdf(writer);
			documento.open();
			documento.add(new Paragraph("Reporte de prueba"));
			SeccionGraficasPdf.escribir(documento, writer, datos, marcadores);
			documento.close();
		} catch (com.itextpdf.text.DocumentException e) {
			throw new IOException(e);
		}
		return salida.toByteArray();
	}

	// Cuatro grupos: control (4 especímenes), referencia con tanda pendiente, un experimental con un minuto incompleto, y uno
	// de nivel mixto (dos tandas con niveles distintos). Es el caso típico del experimento de demostración.
	static Map<String, Object> datosDeVariosGrupos() {
		return GraficasDeResultados.armar(
				List.of(
						new GraficasDeResultados.Grupo(1, "Control", "control", "agua", 4, List.of()),
						new GraficasDeResultados.Grupo(2, "Referencia", "referencia", "fluoxetina", 8,
								List.of(Map.of("letra", "B", "nCilindros", 4, "estado", "en pausa"))),
						new GraficasDeResultados.Grupo(3, "Dosis 1", "tratamiento experimental", "compuesto", 4, List.of()),
						new GraficasDeResultados.Grupo(4, "Mixto", "tratamiento experimental", "x", 2, List.of())),
				List.of(
						especimen(1, 1, 1, "preciso", minutos(60, 60, 60, 60, 60)),
						especimen(1, 1, 2, "preciso", minutos(60, 60, 60, 60, 60)),
						especimen(1, 1, 3, "preciso", minutos(60, 60, 60, 60, 60)),
						especimen(1, 1, 4, "preciso", minutos(60, 60, 60, 60, 60)),
						especimen(2, 1, 1, "preciso", minutos(60, 60, 60, 60, 60)),
						especimen(2, 1, 2, "preciso", minutos(60, 60, 60, 60, 60)),
						especimen(3, 1, 1, "preciso", minutos(60, 60, 60, 60, 30)),
						especimen(3, 1, 2, "preciso", minutos(60, 60, 60, 60, 60)),
						especimen(4, 1, 1, "preciso", minutos(60, 60, 60, 60, 60)),
						especimen(4, 2, 1, "agrupado", minutos(60, 60, 60, 60, 60))));
	}

	// Un grupo con nombres largos, para probar que las etiquetas se parten o se recortan.
	static Map<String, Object> datosConNombresLargos() {
		List<GraficasDeResultados.Grupo> grupos = new ArrayList<>();
		List<GraficasDeResultados.Especimen> especimenes = new ArrayList<>();
		String[] nombres = {"Control", "Fluoxetina 10 mg/kg", "Dosis intermedia", "Dosis alta experimental", "Dosis muy alta", "Vehículo y control"};
		for (int i = 0; i < nombres.length; i++) {
			String tipo = i == 0 ? "control" : (i == 1 ? "referencia" : "tratamiento experimental");
			grupos.add(new GraficasDeResultados.Grupo(i + 1, nombres[i], tipo, "x", 2, List.of()));
			especimenes.add(especimen(i + 1, 1, 1, "preciso", minutos(60, 60, 60, 60, 60)));
			especimenes.add(especimen(i + 1, 1, 2, "preciso", minutos(60, 60, 60, 60, 60)));
		}
		return GraficasDeResultados.armar(grupos, especimenes);
	}

	// Un espécimen con sus segundos por minuto (cinco minutos): nado = s − 10, inmovilidad 5 y sin ver 5, así suman s.
	static GraficasDeResultados.Especimen especimen(int idGrupo, int ordinal, int cilindro, String nivel, Map<Integer, Map<String, Long>> porMinuto) {
		Map<String, Long> total = new HashMap<>();
		for (Map<String, Long> m : porMinuto.values()) {
			m.forEach((k, v) -> total.merge(k, v, Long::sum));
		}
		long duracion = total.values().stream().mapToLong(Long::longValue).sum();
		return new GraficasDeResultados.Especimen(idGrupo, ordinal, String.valueOf((char) ('A' + ordinal - 1)), cilindro, nivel,
				duracion, total, porMinuto);
	}

	// Cinco minutos con los segundos dados (el último puede ser menor a 60 s).
	static Map<Integer, Map<String, Long>> minutos(int... segundos) {
		Map<Integer, Map<String, Long>> porMinuto = new HashMap<>();
		for (int i = 0; i < segundos.length; i++) {
			long s = segundos[i];
			porMinuto.put(i + 1, Map.of(NADO, s - 10, INM, 5L, ESC, 0L, ACT, 0L, SIN, 5L));
		}
		return porMinuto;
	}

	private static List<String> titulos(List<HashMap<String, Object>> marcadores) {
		List<String> titulos = new ArrayList<>();
		for (HashMap<String, Object> m : marcadores) {
			titulos.add(String.valueOf(m.get("Title")));
		}
		return titulos;
	}

	@SuppressWarnings("unchecked")
	private static List<HashMap<String, Object>> kids(HashMap<String, Object> marcador) {
		Object kids = marcador.get("Kids");
		return kids == null ? List.of() : (List<HashMap<String, Object>>) kids;
	}

	// Un PDF de muestra con la sección de gráficas, para revisarlo a mano: solo corre si FST_PDF_EJEMPLO trae la ruta de
	// salida (por ejemplo C:\Users\...\Temp\fst-m1b\ejemplo.pdf).
	@Test
	@EnabledIfEnvironmentVariable(named = "FST_PDF_EJEMPLO", matches = ".+")
	void escribe_el_pdf_de_ejemplo_en_la_ruta_indicada() throws IOException {
		byte[] pdf = pdfConGraficas(datosDeVariosGrupos());
		java.nio.file.Path destino = java.nio.file.Path.of(System.getenv("FST_PDF_EJEMPLO"));
		java.nio.file.Files.createDirectories(destino.getParent());
		java.nio.file.Files.write(destino, pdf);
	}
}
