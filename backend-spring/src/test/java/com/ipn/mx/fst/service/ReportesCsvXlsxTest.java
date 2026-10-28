package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Encargo 39: GET /experiments/{id}/report/csv y /report/xlsx, la revisión humana en los reportes y la varianza de
// /results/groups contra una PostgreSQL de verdad. Mismas condiciones que AdministracionYRecuperacionTest: solo corre con
// FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el esquema de db/init (cada prueba borra todos los datos). El
// contenido de los archivos se prueba aparte, sin base, en ReporteCsvTest y ReporteXlsxTest.
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-reportes"
})
@AutoConfigureMockMvc
class ReportesCsvXlsxTest {

	private static final String CLAVE = "clave-de-prueba-1";
	private static final String N = "nado activo";
	private static final String I = "inmovilidad";
	private static final String E = "escalamiento";
	private static final Path RAIZ = Path.of(System.getProperty("java.io.tmpdir"), "fst-pruebas-reportes");

	private static final String ENCABEZADO = "Grupo,Tanda,Espécimen,Tiempo (min:seg),Nado,Escalamiento,Inmovilidad,Conducta activa";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private int experimento;
	private int control;
	private int referencia;
	private int tandaA;
	private int tandaB;
	private int analisisA;

	@BeforeEach
	void base_limpia() throws IOException {
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		borrarCarpeta(RAIZ);
		cuenta("1", "creador@fst.mx");
		cuenta("2", "otro@fst.mx");
		experimento = experimento("Exp");
		control = grupo(experimento, "Control");
		referencia = grupo(experimento, "Referencia");

		// Control, tanda A: completada, con dos especímenes. El 1 tiene 5 de nado, 4 de inmovilidad y 2 de escalamiento (y un
		// segundo sin ver); el 2, 5 de inmovilidad.
		tandaA = tanda(control, 1, "completado");
		analisisA = jdbc.queryForObject("SELECT a.idanalisis FROM analisis a JOIN video v ON v.idvideo = a.idvideo WHERE v.idtanda = ?",
				Integer.class, tandaA);
		segundos(observacion(tandaA, 1), N, N, N, I, I, I, I, N, N, E, E, null);
		segundos(observacion(tandaA, 2), I, I, I, I, I);
		// Control, tanda B: completada, con un espécimen de 4 s de nado.
		tandaB = tanda(control, 2, "completado");
		segundos(observacion(tandaB, 1), N, N, N, N);
		// Referencia, tanda A: todavía procesando, no entra en ningún reporte.
		int pendiente = tanda(referencia, 1, "procesando");
		observacion(pendiente, 1);
	}

	// ----------------------------------------------------------------- CSV

	@Test
	void el_csv_del_grupo_trae_las_columnas_las_filas_y_los_estadisticos() throws Exception {
		MvcResult r = mvc.perform(get(ruta("csv", experimento, "grupo=" + control)).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=reporte_experimento_" + experimento
						+ "_grupo-" + control + ".csv"))
				.andReturn();

		byte[] cuerpo = r.getResponse().getContentAsByteArray();
		assertEquals((byte) 0xEF, cuerpo[0]);
		assertEquals((byte) 0xBB, cuerpo[1]);
		assertEquals((byte) 0xBF, cuerpo[2]);
		List<String> lineas = lineas(cuerpo);
		assertEquals(ENCABEZADO, lineas.get(0));
		// N, escalamiento, inmovilidad, conducta activa; un minuto cada espécimen (duran menos de 60 s).
		assertEquals("Control,A,Espécimen 1,0:00-1:00,5,2,4,0", lineas.get(1));
		assertEquals("Control,A,Espécimen 2,0:00-1:00,0,0,5,0", lineas.get(2));
		assertEquals("Control,B,Espécimen 1,0:00-1:00,4,0,0,0", lineas.get(3));
		// Totales por espécimen: nado 5, 0, 4; escalamiento 2, 0, 0; inmovilidad 4, 5, 0; conducta activa 0, 0, 0.
		assertEquals(",,Media,,3.0,0.7,3.0,0.0", lineas.get(4));
		assertEquals(",,Desviación estándar,,2.6,1.2,2.6,0.0", lineas.get(5));
		assertEquals(",,Varianza,,7.0,1.3,7.0,0.0", lineas.get(6));
		assertEquals(",,Revisión humana,No", lineas.get(7));
		assertEquals(9, lineas.size());      // y el vacío que queda tras el último \r\n
	}

	@Test
	void el_csv_de_una_tanda_solo_trae_esa_tanda_y_se_nombra_con_su_letra() throws Exception {
		MvcResult r = mvc.perform(get(ruta("csv", experimento, "grupo=" + control + "&tanda=b")).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=reporte_experimento_" + experimento
						+ "_grupo-" + control + "_tanda-B.csv"))
				.andReturn();

		List<String> lineas = lineas(r.getResponse().getContentAsByteArray());
		assertEquals("Control,B,Espécimen 1,0:00-1:00,4,0,0,0", lineas.get(1));
		// Un solo espécimen: la desviación y la varianza son 0.
		assertEquals(",,Media,,4.0,0.0,0.0,0.0", lineas.get(2));
		assertEquals(",,Desviación estándar,,0.0,0.0,0.0,0.0", lineas.get(3));
		assertEquals(",,Varianza,,0.0,0.0,0.0,0.0", lineas.get(4));
		assertEquals(",,Revisión humana,No", lineas.get(5));
		assertEquals(7, lineas.size());
	}

	@Test
	void con_tandas_de_niveles_distintos_agrega_el_aviso() throws Exception {
		jdbc.update("UPDATE analisis SET nivelclasif = 'agrupado' WHERE idanalisis = ?", analisisA);
		List<String> lineas = lineas(mvc.perform(get(ruta("csv", experimento, "grupo=" + control)).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
		assertEquals(",,Aviso,Mezcla tandas de clasificación precisa y agrupada", lineas.get(8));
	}

	// ----------------------------------------------------------------- XLSX

	@Test
	void el_xlsx_trae_lo_mismo_con_numeros_de_verdad() throws Exception {
		MvcResult r = mvc.perform(get(ruta("xlsx", experimento, "grupo=" + control)).header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=reporte_experimento_" + experimento
						+ "_grupo-" + control + ".xlsx"))
				.andReturn();

		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
			Sheet hoja = libro.getSheet("Resultados");
			assertEquals("Grupo", hoja.getRow(0).getCell(0).getStringCellValue());
			assertEquals("Control", hoja.getRow(1).getCell(0).getStringCellValue());
			assertEquals(5.0, hoja.getRow(1).getCell(4).getNumericCellValue());
			assertEquals("Varianza", hoja.getRow(6).getCell(2).getStringCellValue());
			assertEquals(7.0, hoja.getRow(6).getCell(4).getNumericCellValue());
			assertEquals("No", hoja.getRow(7).getCell(3).getStringCellValue());
		}
		assertTrue(Files.isRegularFile(RAIZ.resolve("reportes/" + experimento + "/grupo-" + control + ".xlsx")));
	}

	// ----------------------------------------------------------------- errores y permisos

	@Test
	void sin_grupo_400_y_una_tanda_que_no_es_una_letra_400() throws Exception {
		String creador = login("creador@fst.mx");
		for (String formato : List.of("csv", "xlsx")) {
			mvc.perform(get(ruta(formato, experimento, null)).header("Authorization", "Bearer " + creador)).andExpect(status().isBadRequest());
			mvc.perform(get(ruta(formato, experimento, "grupo=abc")).header("Authorization", "Bearer " + creador)).andExpect(status().isBadRequest());
			for (String tanda : List.of("AB", "1", "%C3%91")) {
				mvc.perform(get(ruta(formato, experimento, "grupo=" + control + "&tanda=" + tanda)).header("Authorization", "Bearer " + creador))
						.andExpect(status().isBadRequest());
			}
		}
		assertEquals(0, filasDeReporte());
	}

	@Test
	void un_grupo_de_otro_experimento_una_tanda_que_no_existe_o_un_experimento_que_no_existe_404() throws Exception {
		String creador = login("creador@fst.mx");
		int otroExperimento = experimento("Otro");
		int ajeno = grupo(otroExperimento, "Ajeno");
		for (String formato : List.of("csv", "xlsx")) {
			mvc.perform(get(ruta(formato, experimento, "grupo=" + ajeno)).header("Authorization", "Bearer " + creador)).andExpect(status().isNotFound());
			mvc.perform(get(ruta(formato, experimento, "grupo=99999")).header("Authorization", "Bearer " + creador)).andExpect(status().isNotFound());
			mvc.perform(get(ruta(formato, experimento, "grupo=" + control + "&tanda=C")).header("Authorization", "Bearer " + creador))
					.andExpect(status().isNotFound());
			mvc.perform(get(ruta(formato, 99999, "grupo=" + control)).header("Authorization", "Bearer " + creador)).andExpect(status().isNotFound());
		}
		assertEquals(0, filasDeReporte());
	}

	@Test
	void sin_analisis_completados_409_y_no_queda_ni_fila_ni_archivo() throws Exception {
		String creador = login("creador@fst.mx");
		for (String formato : List.of("csv", "xlsx")) {
			mvc.perform(get(ruta(formato, experimento, "grupo=" + referencia)).header("Authorization", "Bearer " + creador)).andExpect(status().isConflict());
			mvc.perform(get(ruta(formato, experimento, "grupo=" + referencia + "&tanda=A")).header("Authorization", "Bearer " + creador))
					.andExpect(status().isConflict());
		}
		assertEquals(0, filasDeReporte());
		assertFalse(Files.exists(RAIZ.resolve("reportes").resolve(String.valueOf(experimento))), "el 409 no debe dejar archivos");
	}

	@Test
	void sin_sesion_401_y_cualquier_cuenta_con_sesion_puede_descargarlos() throws Exception {
		mvc.perform(get(ruta("csv", experimento, "grupo=" + control))).andExpect(status().isUnauthorized());
		mvc.perform(get(ruta("xlsx", experimento, "grupo=" + control))).andExpect(status().isUnauthorized());
		// Como el PDF y /results (R-07): lectura para cualquier usuario autenticado.
		mvc.perform(get(ruta("csv", experimento, "grupo=" + control)).header("Authorization", "Bearer " + login("otro@fst.mx"))).andExpect(status().isOk());
		mvc.perform(get(ruta("xlsx", experimento, "grupo=" + control)).header("Authorization", "Bearer " + login("otro@fst.mx"))).andExpect(status().isOk());
	}

	// ----------------------------------------------------------------- almacén y revisión

	@Test
	void la_segunda_descarga_sale_del_archivo_guardado() throws Exception {
		String creador = login("creador@fst.mx");
		byte[] primera = mvc.perform(get(ruta("csv", experimento, "grupo=" + control)).header("Authorization", "Bearer " + creador))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

		String relativa = "reportes/" + experimento + "/grupo-" + control + ".csv";
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reporte WHERE ruta = ? AND formato = 'CSV'", Integer.class, relativa));
		Path archivo = RAIZ.resolve(relativa);
		assertArrayEquals(primera, Files.readAllBytes(archivo));

		// Si la segunda descarga se volviera a generar, saldría la primera; sale lo que dice el archivo.
		byte[] marca = "marca del archivo guardado".getBytes(StandardCharsets.UTF_8);
		Files.write(archivo, marca);
		byte[] segunda = mvc.perform(get(ruta("csv", experimento, "grupo=" + control)).header("Authorization", "Bearer " + creador))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		assertArrayEquals(marca, segunda);
		assertEquals(1, filasDeReporte());
	}

	@Test
	void despues_de_corregir_segundos_el_reporte_se_regenera_y_marca_la_revision() throws Exception {
		String creador = login("creador@fst.mx");
		mvc.perform(get(ruta("csv", experimento, "grupo=" + control)).header("Authorization", "Bearer " + creador)).andExpect(status().isOk());
		mvc.perform(get(ruta("xlsx", experimento, "grupo=" + control)).header("Authorization", "Bearer " + creador)).andExpect(status().isOk());
		byte[] pdfAntes = mvc.perform(get("/experiments/" + experimento + "/report/pdf").header("Authorization", "Bearer " + creador))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		assertEquals(3, filasDeReporte());
		assertTrue(textoDelPdf(pdfAntes).contains("Revisión humana: No"));
		assertFalse(textoDelPdf(pdfAntes).contains("revisado"));

		// El segundo 12 del espécimen 1 (sin ver) pasa a nado: ese espécimen queda revisado.
		mvc.perform(corregir(creador, "{\"especimenes\":[{\"numeroCilindro\":1,\"segundos\":"
				+ "[{\"segundo\":12,\"conducta\":\"nado activo\",\"origen\":\"corregida\"}]}]}")).andExpect(status().isOk());
		assertEquals(0, filasDeReporte(), "corregir invalida los tres reportes");

		List<String> lineas = lineas(mvc.perform(get(ruta("csv", experimento, "grupo=" + control)).header("Authorization", "Bearer " + creador))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
		assertEquals("Control,A,Espécimen 1,0:00-1:00,6,2,4,0", lineas.get(1));
		assertEquals(",,Revisión humana,Sí: Tanda A · Espécimen 1", lineas.get(7));
		assertEquals(1, filasDeReporte());

		// La tanda B no se tocó, pero su reporte también se regenera y dice que no hubo revisión en ella.
		List<String> deLaTandaB = lineas(mvc.perform(get(ruta("csv", experimento, "grupo=" + control + "&tanda=B")).header("Authorization", "Bearer " + creador))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
		assertEquals(",,Revisión humana,No", deLaTandaB.get(5));

		// Y el PDF lo dice en los datos del experimento y en la etiqueta del espécimen.
		byte[] pdfDespues = mvc.perform(get("/experiments/" + experimento + "/report/pdf").header("Authorization", "Bearer " + creador))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		String texto = textoDelPdf(pdfDespues);
		assertTrue(texto.contains("Revisión humana: Sí (1 espécimen con segundos corregidos)"), texto);
		assertTrue(texto.contains("revisado"), texto);
	}

	@Test
	void un_segundo_marcado_dudoso_sin_conducta_tambien_cuenta_como_revision() throws Exception {
		String creador = login("creador@fst.mx");
		mvc.perform(corregir(creador, "{\"especimenes\":[{\"numeroCilindro\":1,\"segundos\":"
				+ "[{\"segundo\":12,\"conducta\":null,\"origen\":\"dudosa\"}]}]}")).andExpect(status().isOk());
		List<String> lineas = lineas(mvc.perform(get(ruta("csv", experimento, "grupo=" + control)).header("Authorization", "Bearer " + creador))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
		assertEquals(",,Revisión humana,Sí: Tanda A · Espécimen 1", lineas.get(7));
		String texto = textoDelPdf(mvc.perform(get("/experiments/" + experimento + "/report/pdf").header("Authorization", "Bearer " + creador))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
		assertTrue(texto.contains("Revisión humana: Sí (1 espécimen"), texto);
	}

	// ----------------------------------------------------------------- varianza en /results/groups

	@Test
	void results_groups_trae_la_varianza_junto_a_la_media_y_la_desviacion() throws Exception {
		MvcResult r = mvc.perform(get("/experiments/" + experimento + "/results/groups").header("Authorization", "Bearer " + login("creador@fst.mx")))
				.andExpect(status().isOk()).andReturn();
		String json = r.getResponse().getContentAsString();
		// Los grupos salen por idgrupo: Control y después Referencia.
		Map<String, Object> nado = JsonPath.read(json, "$.data[0].promedios['nado activo']");
		assertEquals(3.0, ((Number) nado.get("media")).doubleValue());
		assertEquals(2.6, ((Number) nado.get("de")).doubleValue());
		assertEquals(7.0, ((Number) nado.get("varianza")).doubleValue());
		// Un grupo sin especímenes completados: los tres en null.
		Map<String, Object> vacio = JsonPath.read(json, "$.data[1].promedios['nado activo']");
		assertEquals(null, vacio.get("media"));
		assertEquals(null, vacio.get("de"));
		assertEquals(null, vacio.get("varianza"));
	}

	// ----------------------------------------------------------------- apoyo

	private static String ruta(String formato, int id, String consulta) {
		return "/experiments/" + id + "/report/" + formato + (consulta == null ? "" : "?" + consulta);
	}

	private MockHttpServletRequestBuilder corregir(String sesion, String cuerpo) {
		return put("/experiments/" + experimento + "/analyses/" + analisisA + "/segundos").header("Authorization", "Bearer " + sesion)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	// Las líneas del CSV sin el BOM; el último elemento es el vacío que queda tras el \r\n final.
	private static List<String> lineas(byte[] csv) {
		return Arrays.asList(new String(csv, StandardCharsets.UTF_8).substring(1).split("\r\n", -1));
	}

	private static String textoDelPdf(byte[] pdf) throws IOException {
		PdfReader lector = new PdfReader(pdf);
		try {
			StringBuilder texto = new StringBuilder();
			for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
				texto.append(PdfTextExtractor.getTextFromPage(lector, pagina)).append('\n');
			}
			return texto.toString();
		} finally {
			lector.close();
		}
	}

	private int filasDeReporte() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM reporte", Integer.class);
	}

	private static void borrarCarpeta(Path carpeta) throws IOException {
		if (!Files.exists(carpeta)) {
			return;
		}
		try (var arbol = Files.walk(carpeta)) {
			for (Path ruta : arbol.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.delete(ruta);
			}
		}
	}

	private void cuenta(String id, String correo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Nombre', 'Apellidos', ?, ?, true, false)", id, correo, passwordEncoder.encode(CLAVE));
	}

	private int experimento(String nombre) {
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', ?, current_date)", nombre);
		return jdbc.queryForObject("SELECT idexperimento FROM experimento WHERE nombre = ?", Integer.class, nombre);
	}

	private int grupo(int idExperimento, String etiqueta) {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, 'control', 'agua')", idExperimento, etiqueta);
		return jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE etiqueta = ?", Integer.class, etiqueta);
	}

	private int tanda(int grupo, int ordinal, String estado) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, 2)", grupo, ordinal);
		int tanda = jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class, grupo, ordinal);
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', 'videos/x.mp4', 12)", tanda);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, ?::estado_analisis)", video, estado);
		return tanda;
	}

	// Un espécimen del cilindro dado con su observación del video de la tanda; devuelve el id de la observación.
	private int observacion(int tanda, int cilindro) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, ?)", tanda, cilindro);
		int especimen = jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = ?",
				Integer.class, tanda, cilindro);
		int video = jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ?", Integer.class, tanda);
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", especimen, video);
		return jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idespecimen = ?", Integer.class, especimen);
	}

	private void segundos(int observacion, String... conductas) {
		for (int i = 0; i < conductas.length; i++) {
			jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, ?, ?::tipo_conducta, 'maquina')",
					observacion, i + 1, conductas[i]);
		}
	}

	private String login(String correo) throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

}
