// Mismo paquete que las clases que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Encargo 41, parte C: los filtros estado, fecha y tratamiento de GET /experiments contra una PostgreSQL de verdad. Mismas
// condiciones que AdministracionYRecuperacionTest: solo corre con FST_TEST_DB_URL apuntando a una base de pruebas VACÍA con el
// esquema de db/init (cada prueba borra todos los datos).
//
// Prueba ExperimentoServiceImpl.listar, el método más complejo del backend: filtra y pagina EN JAVA (no
// en SQL) y calcula un resumen de TODOS los experimentos. Por eso esta clase es de las más extensas:
// hay que cubrir cada filtro solo, todos combinados, la paginación y el resumen.
// La estructura de una prueba de integración está explicada en SegundosYExcelTest.
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-lista"
})
@AutoConfigureMockMvc
class ListaDeExperimentosTest {

	private static final String CLAVE = "clave-de-prueba-1";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// Cinco experimentos con un estado cada uno, y 25 más de relleno ("Masivo", sin videos) para probar las páginas.
	//   Alfa     2026-10-20  Acupuntura           completado
	//   Beta     2026-10-25  Ácido Fólico         en cola
	//   Gamma    2025-03-10  Control              sin videos
	//   Delta    2026-10-25  Control              en espera del Día 2 (solo el Día 1)
	//   Epsilon  2026-01-02  Acupuntura y Control error
	//   Relleno  2024-05-05  Masivo               sin videos (25)
	//
	// La tabla de arriba es el MAPA para leer todas las pruebas: cada una elige una combinación de
	// filtros y comprueba qué experimentos salen. Los datos están diseñados con intención:
	//   Alfa y Epsilon comparten el tratamiento "Acupuntura" (un filtro devuelve dos);
	//   Beta y Delta comparten fecha (para probar el desempate por id);
	//   "Ácido Fólico" lleva acentos y mayúsculas (para probar la búsqueda sin acentos);
	//   Epsilon tiene DOS grupos (se encuentra por cualquiera de los dos tratamientos);
	//   los 25 "Relleno" existen solo para pasar de las 20 filas de una página.
	@BeforeEach
	void base_limpia() {
		// De las hojas hacia arriba, por las llaves foráneas.
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES ('1', 'Nombre', 'Apellidos', 'creador@fst.mx', ?, true, false)", passwordEncoder.encode(CLAVE));

		int alfa = experimento("Alfa", "2026-10-20");
		conAnalisis(grupo(alfa, "Acupuntura"), "completado");
		int beta = experimento("Beta", "2026-10-25");
		conAnalisis(grupo(beta, "Ácido Fólico"), "en cola");
		// Gamma: una tanda pero ningún video, así que su estado es "sin videos".
		int gamma = experimento("Gamma", "2025-03-10");
		tanda(grupo(gamma, "Control"));
		// Delta: un video del Día 1 pero ninguno del Día 2, que no se analiza: "en espera del Día 2".
		int delta = experimento("Delta", "2026-10-25");
		int tandaDelta = tanda(grupo(delta, "Control"));
		video(tandaDelta, "Dia 1");
		int epsilon = experimento("Epsilon", "2026-01-02");
		conAnalisis(grupo(epsilon, "Acupuntura"), "error");
		tanda(grupo(epsilon, "Control"));
		// Los 25 de relleno. El cero de relleno ("Relleno 05" y no "Relleno 5") permite que ordenados por
		// nombre salgan en su orden natural.
		for (int i = 1; i <= 25; i++) {
			tanda(grupo(experimento("Relleno " + (i < 10 ? "0" : "") + i, "2024-05-05"), "Masivo"));
		}
	}

	@Test
	void sin_filtros_la_respuesta_es_la_de_siempre() throws Exception {
		MvcResult r = mvc.perform(pedir("", login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.pagina").value(1))
				.andExpect(jsonPath("$.data.total").value(30))
				// 30 experimentos pero una página de 20 (POR_PAGINA de ExperimentoServiceImpl).
				.andExpect(jsonPath("$.data.items.length()").value(20))
				.andReturn();
		// Más reciente primero; a igual fecha, el de id más alto (Delta se creó después que Beta).
		// Es el ORDER BY de ExperimentoRepository.buscarTodos: fecha descendente y luego id descendente.
		assertEquals(List.of("Delta", "Beta", "Alfa", "Epsilon", "Gamma"), nombres(r).subList(0, 5));
		// La segunda página trae los 10 que sobran, y el total no cambia.
		mvc.perform(pedir("pagina=2", login()))
				.andExpect(jsonPath("$.data.items.length()").value(10)).andExpect(jsonPath("$.data.total").value(30));
		// Una página fuera de rango: lista vacía, no error.
		mvc.perform(pedir("pagina=3", login()))
				.andExpect(jsonPath("$.data.items.length()").value(0)).andExpect(jsonPath("$.data.total").value(30));
		// Filtros en blanco son sin filtro.
		// %20 es un espacio: comprueba que un filtro de solo espacios cuenta como ausente (ver sinEspacios).
		mvc.perform(pedir("estado=&fecha=%20&tratamiento=", login()))
				.andExpect(jsonPath("$.data.total").value(30)).andExpect(jsonPath("$.data.items.length()").value(20));
	}

	@Test
	@Tag("trabajo-terminal")
	void cada_estado_filtra_los_experimentos_que_tienen_ese_estado() throws Exception {
		assertEquals(List.of("Alfa"), nombres(lista("estado=completado")));
		assertEquals(List.of("Beta"), nombres(lista("estado=en%20cola")));
		assertEquals(List.of("Epsilon"), nombres(lista("estado=error")));
		// %C3%AD es la "í" codificada en UTF-8: así llega "en espera del Día 2" en una URL real.
		assertEquals(List.of("Delta"), nombres(lista("estado=en%20espera%20del%20D%C3%ADa%202")));
		// Gamma y los 25 de relleno: 26 sin videos. Se comprueba el total porque la lista se corta en 20.
		assertEquals(26, (int) JsonPath.read(lista("estado=sin%20videos").getResponse().getContentAsString(), "$.data.total"));
		// Estados válidos sin ningún experimento: lista vacía, total 0.
		MvcResult r = lista("estado=procesando");
		assertEquals(List.of(), nombres(r));
		assertEquals(0, (int) JsonPath.read(r.getResponse().getContentAsString(), "$.data.total"));
	}

	@Test
	void la_fecha_puede_ser_un_anio_o_un_dia() throws Exception {
		// Solo el año: todos los de 2026, del más reciente al más antiguo.
		assertEquals(List.of("Delta", "Beta", "Alfa", "Epsilon"), nombres(lista("fecha=2026")));
		assertEquals(List.of("Gamma"), nombres(lista("fecha=2025")));
		// La fecha completa: solo los de ese día exacto.
		assertEquals(List.of("Delta", "Beta"), nombres(lista("fecha=2026-10-25")));
		assertEquals(List.of("Alfa"), nombres(lista("fecha=2026-10-20")));
		// Fechas válidas sin ningún experimento: lista vacía, no error.
		assertEquals(List.of(), nombres(lista("fecha=2026-10-21")));
		assertEquals(List.of(), nombres(lista("fecha=1999")));
	}

	@Test
	void el_tratamiento_coincide_con_cualquier_grupo_sin_distinguir_mayusculas_ni_acentos() throws Exception {
		assertEquals(List.of("Alfa", "Epsilon"), nombres(lista("tratamiento=Acupuntura")));
		assertEquals(List.of("Alfa", "Epsilon"), nombres(lista("tratamiento=ACUPUNTURA")));
		assertEquals(List.of("Delta", "Epsilon", "Gamma"), nombres(lista("tratamiento=control")));
		// Epsilon tiene dos grupos: el filtro encuentra el experimento por cualquiera de los dos.
		assertEquals(List.of("Beta"), nombres(lista("tratamiento=acido%20folico")));
		// El mismo tratamiento con acentos y mayúsculas, codificado en UTF-8.
		assertEquals(List.of("Beta"), nombres(lista("tratamiento=%C3%81CIDO%20F%C3%93LICO")));
		// Aquí el filtro es EXACTO (equals, no contains): sale de un menú con valores completos.
		assertEquals(List.of(), nombres(lista("tratamiento=Acup")));      // el tratamiento completo, no un pedazo
		assertEquals(List.of(), nombres(lista("tratamiento=nada")));
	}

	@Test
	void los_filtros_combinados_se_cumplen_todos() throws Exception {
		// Los filtros se combinan con Y: un experimento tiene que cumplir TODOS los que se pidan.
		assertEquals(List.of("Beta"), nombres(lista("estado=en%20cola&fecha=2026&tratamiento=acido%20folico")));
		assertEquals(List.of("Epsilon"), nombres(lista("estado=error&tratamiento=control")));
		assertEquals(List.of("Delta"), nombres(lista("fecha=2026-10-25&tratamiento=Control")));
		// Y combinaciones que no cumple ninguno.
		assertEquals(List.of(), nombres(lista("estado=completado&tratamiento=control")));
		assertEquals(List.of(), nombres(lista("estado=completado&fecha=2025")));
	}

	@Test
	void con_filtro_el_total_son_los_que_pasan_y_las_paginas_se_cortan_de_20_en_20() throws Exception {
		MvcResult primera = lista("tratamiento=masivo");
		// total son los que PASAN el filtro (25), no los de la página (20).
		assertEquals(25, (int) JsonPath.read(primera.getResponse().getContentAsString(), "$.data.total"));
		assertEquals(20, nombres(primera).size());
		assertEquals(1, (int) JsonPath.read(primera.getResponse().getContentAsString(), "$.data.pagina"));
		MvcResult segunda = lista("tratamiento=masivo&pagina=2");
		assertEquals(5, nombres(segunda).size());
		assertEquals(25, (int) JsonPath.read(segunda.getResponse().getContentAsString(), "$.data.total"));
		// Las dos páginas juntas son los 25, sin repetir, del más reciente al más antiguo (id descendente a igual fecha).
		List<String> juntos = new java.util.ArrayList<>(nombres(primera));
		juntos.addAll(nombres(segunda));
		// distinct().count() cuenta los elementos sin repetir: si la paginación repitiera o se saltara
		// alguno, no saldrían 25.
		assertEquals(25, juntos.stream().distinct().count());
		assertEquals("Relleno 25", juntos.get(0));
		assertEquals("Relleno 01", juntos.get(24));

		// Una página fuera de rango: lista vacía con el total correcto.
		MvcResult fuera = lista("tratamiento=masivo&pagina=3");
		assertEquals(List.of(), nombres(fuera));
		assertEquals(25, (int) JsonPath.read(fuera.getResponse().getContentAsString(), "$.data.total"));
		assertEquals(3, (int) JsonPath.read(fuera.getResponse().getContentAsString(), "$.data.pagina"));
		// Un número de página enorme, que desbordaría un int al multiplicar por 20: es la razón del (long)
		// en ExperimentoServiceImpl.listar. Debe responder normal, no reventar.
		MvcResult muyLejos = lista("tratamiento=masivo&pagina=99999999");
		assertEquals(List.of(), nombres(muyLejos));
		assertEquals(25, (int) JsonPath.read(muyLejos.getResponse().getContentAsString(), "$.data.total"));
	}

	@Test
	void con_filtro_cada_item_trae_los_mismos_campos_que_sin_filtro() throws Exception {
		String sinFiltro = mvc.perform(pedir("", login())).andReturn().getResponse().getContentAsString();
		String conFiltro = lista("estado=completado").getResponse().getContentAsString();
		List<java.util.Map<String, Object>> filtrado = JsonPath.read(conFiltro, "$.data.items");
		// El filtro [?(@.nombre=='Alfa')] saca de la respuesta sin filtro solo el item de Alfa.
		List<java.util.Map<String, Object>> deSiempre = JsonPath.read(sinFiltro, "$.data.items[?(@.nombre=='Alfa')]");
		assertEquals(1, filtrado.size());
		// Los dos mapas son IGUALES campo por campo: filtrar no cambia la forma de lo que se devuelve,
		// solo cuáles salen.
		assertEquals(deSiempre.get(0), filtrado.get(0));
		assertEquals("completado", filtrado.get(0).get("estado"));
		assertEquals(List.of("Acupuntura"), filtrado.get(0).get("tratamientos"));
	}

	@Test
	void un_valor_que_no_es_valido_da_400_en_cada_filtro() throws Exception {
		String sesion = login();
		// Una lista de consultas inválidas, cada una por un motivo distinto: un estado que no existe, con
		// mayúscula (los estados son exactos), una fecha de longitud rara, un mes 13, un 30 de febrero
		// (que LocalDate rechaza), un separador equivocado y un número de página que no lo es.
		for (String consulta : List.of("estado=terminado", "estado=Completado", "estado=en%20espera", "fecha=26", "fecha=20261",
				"fecha=2026-10", "fecha=2026-13-01", "fecha=2026-02-30", "fecha=abcd", "fecha=2026/10/25", "fecha=25-10-2026",
				"pagina=0&estado=error", "pagina=x&fecha=2026")) {
			mvc.perform(pedir(consulta, sesion))
					.andExpect(status().isBadRequest());
		}
		// Y una sesión sigue siendo obligatoria.
		mvc.perform(get("/experiments?estado=error")).andExpect(status().isUnauthorized());
	}

	// ----------------------------------------------------------- q y resumen (encargo 43)
	//
	// La búsqueda libre q mira en el NOMBRE y en el TRATAMIENTO de cualquier grupo, con coincidencia
	// parcial (contains) y sin distinguir mayúsculas ni acentos. A diferencia del filtro tratamiento, que
	// es exacto.

	@Test
	void q_busca_por_nombre_sin_distinguir_mayusculas_y_por_un_pedazo() throws Exception {
		assertEquals(List.of("Alfa"), nombres(lista("q=alfa")));
		assertEquals(List.of("Alfa"), nombres(lista("q=ALF")));
		assertEquals(List.of("Alfa"), nombres(lista("q=%20lfa%20")));          // recorta espacios
		assertEquals(25, (int) JsonPath.read(lista("q=relleno").getResponse().getContentAsString(), "$.data.total"));
		assertEquals(List.of("Relleno 25"), nombres(lista("q=relleno%2025")));
		assertEquals(List.of(), nombres(lista("q=zzz")));
		// Vacío o en blanco es sin filtro.
		assertEquals(30, (int) JsonPath.read(lista("q=").getResponse().getContentAsString(), "$.data.total"));
		assertEquals(30, (int) JsonPath.read(lista("q=%20").getResponse().getContentAsString(), "$.data.total"));
	}

	@Test
	void q_busca_en_el_tratamiento_de_cualquier_grupo_sin_acentos() throws Exception {
		assertEquals(List.of("Alfa", "Epsilon"), nombres(lista("q=acupun")));            // un pedazo del tratamiento
		assertEquals(List.of("Beta"), nombres(lista("q=acido")));                       // «Ácido Fólico» sin acento
		assertEquals(List.of("Beta"), nombres(lista("q=%C3%81CIDO%20F%C3%93LICO")));    // con acentos y mayúsculas
		assertEquals(List.of("Beta"), nombres(lista("q=folico")));
		assertEquals(List.of("Delta", "Epsilon", "Gamma"), nombres(lista("q=control")));
		assertEquals(25, (int) JsonPath.read(lista("q=masiv").getResponse().getContentAsString(), "$.data.total"));
		// El texto no cruza el nombre y el tratamiento: «Alfa Acupuntura» no está en ninguno de los dos por separado.
		// Es una limitación consciente: cada uno se busca por su lado (ver coincideTexto).
		assertEquals(List.of(), nombres(lista("q=alfa%20acupuntura")));
	}

	@Test
	void q_se_combina_con_los_otros_filtros_y_con_las_paginas() throws Exception {
		assertEquals(List.of("Epsilon"), nombres(lista("q=control&estado=error")));
		assertEquals(List.of("Delta"), nombres(lista("q=control&fecha=2026-10-25")));
		assertEquals(List.of("Alfa", "Epsilon"), nombres(lista("q=acupuntura&fecha=2026")));
		assertEquals(List.of("Epsilon"), nombres(lista("q=acupuntura&tratamiento=control")));
		assertEquals(List.of(), nombres(lista("q=acupuntura&estado=en%20cola")));
		MvcResult segunda = lista("q=masivo&pagina=2");
		assertEquals(5, nombres(segunda).size());
		assertEquals(25, (int) JsonPath.read(segunda.getResponse().getContentAsString(), "$.data.total"));
	}

	@Test
	void el_resumen_cuenta_todos_los_experimentos_con_o_sin_filtros() throws Exception {
		String sinFiltros = mvc.perform(pedir("", login())).andReturn().getResponse().getContentAsString();
		java.util.Map<String, Object> resumen = JsonPath.read(sinFiltros, "$.data.resumen");
		assertEquals(30, resumen.get("total"));
		assertEquals(java.util.Map.of("completado", 1, "en cola", 1, "error", 1, "en espera del Día 2", 1, "sin videos", 26),
				resumen.get("porEstado"));
		// Años del más reciente al más antiguo, sin repetir (el TreeSet con orden inverso).
		assertEquals(List.of("2026", "2025", "2024"), resumen.get("anios"));
		// Sin repetir y ordenados como en español (el acento no cambia el lugar de la letra): «Ácido» va antes que «Acupuntura».
		// Es el Collator español de resumenDeLista: un sort normal pondría "Ácido" al final, porque la
		// "Á" tiene un código mayor que cualquier letra sin acento.
		assertEquals(List.of("Ácido Fólico", "Acupuntura", "Control", "Masivo"), resumen.get("tratamientos"));
		assertEquals(List.of(), resumen.get("avisos"));

		// Con filtros que dejan uno, dos o ninguno, el resumen es el mismo.
		// Es la propiedad CLAVE del resumen (ver resumenDeLista): ignora los filtros, para que las opciones
		// de los menús no desaparezcan al filtrar. Se comprueba con siete consultas muy distintas, incluida
		// una página fuera de rango y una búsqueda que no encuentra nada.
		for (String consulta : List.of("estado=error", "fecha=2025", "tratamiento=masivo&pagina=2", "q=acupuntura", "q=zzz",
				"estado=completado&q=control", "pagina=9")) {
			String filtrada = lista(consulta).getResponse().getContentAsString();
			// El tercer argumento imprime la consulta que falló.
			assertEquals(resumen, JsonPath.read(filtrada, "$.data.resumen"), consulta);
		}
	}

	@Test
	@Tag("trabajo-terminal")
	void el_resumen_trae_los_avisos_de_vencimiento() throws Exception {
		// El análisis de Alfa se completó hace 25 días: al video le quedan 5 de los 30 de retención (R-05).
		// Los videos se borran a los 30 días de un análisis completado, y con 7 días o menos la lista avisa.
		jdbc.update("UPDATE analisis SET fechaanalisis = now() - interval '25 days' WHERE estado = 'completado'");
		int alfa = jdbc.queryForObject("SELECT idexperimento FROM experimento WHERE nombre = 'Alfa'", Integer.class);
		String respuesta = lista("estado=error").getResponse().getContentAsString();    // un filtro que NO deja a Alfa
		// Aun así el aviso de Alfa sale: los avisos son parte del resumen, que ignora los filtros.
		List<java.util.Map<String, Object>> avisos = JsonPath.read(respuesta, "$.data.resumen.avisos");
		assertEquals(1, avisos.size());
		assertEquals(alfa, avisos.get(0).get("idExperimento"));
		assertEquals("Alfa", avisos.get(0).get("nombre"));
		// La fecha de borrado es hoy más los 5 días que quedan, en la zona de México (la del sistema).
		assertEquals(java.time.LocalDate.now(java.time.ZoneId.of("America/Mexico_City")).plusDays(5).toString(),
				avisos.get(0).get("fechaBorrado"));
		// Y el item de Alfa en la lista trae el mismo aviso.
		java.util.Map<String, Object> avisoDelItem = JsonPath.read(lista("q=alfa").getResponse().getContentAsString(),
				"$.data.items[0].avisoVencimiento");
		assertEquals(avisos.get(0).get("fechaBorrado"), avisoDelItem.get("fechaBorrado"));
	}

	@Test
	void sin_experimentos_el_resumen_viene_vacio() throws Exception {
		// Se vacía todo MENOS el usuario, que hace falta para iniciar sesión.
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"experimento")) {
			jdbc.update("DELETE FROM " + tabla);
		}
		MvcResult r = lista("q=algo");
		assertEquals(0, (int) JsonPath.read(r.getResponse().getContentAsString(), "$.data.total"));
		assertEquals(0, (int) JsonPath.read(r.getResponse().getContentAsString(), "$.data.resumen.total"));
		java.util.Map<String, Object> resumen = JsonPath.read(r.getResponse().getContentAsString(), "$.data.resumen");
		// Todo vacío pero PRESENTE: las claves existen con listas y mapas vacíos, no con null. Es el caso
		// que arma armarItems cuando recibe una lista vacía (el return anticipado).
		assertEquals(java.util.Map.of(), resumen.get("porEstado"));
		assertEquals(List.of(), resumen.get("anios"));
		assertEquals(List.of(), resumen.get("tratamientos"));
		assertEquals(List.of(), resumen.get("avisos"));
	}

	// ----------------------------------------------------------------- apoyo

	// Pide la lista con una consulta (ya con sesión) y comprueba que responda 200.
	private MvcResult lista(String consulta) throws Exception {
		return mvc.perform(pedir(consulta, login())).andExpect(status().isOk()).andReturn();
	}

	// La consulta ya viene codificada como en una URL («%20»): se separa y se decodifica aquí, porque MockMvc volvería a
	// codificar el «%» si la recibiera en la URL.
	//
	// Es un detalle práctico: escribir get("/experiments?q=%20x") haría que MockMvc codificara OTRA vez el
	// "%", y el servidor recibiría "%2520". Por eso se parte la consulta en pares nombre=valor, se
	// decodifica cada uno y se pasan como parámetros, que MockMvc codifica una sola vez.
	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedir(String consulta, String sesion) {
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido = get("/experiments")
				.header("Authorization", "Bearer " + sesion);
		// Sin consulta, split("&") daría un arreglo con un texto vacío; de ahí la comprobación.
		for (String par : consulta.isEmpty() ? new String[0] : consulta.split("&")) {
			int igual = par.indexOf('=');
			String nombre = igual < 0 ? par : par.substring(0, igual);
			String valor = igual < 0 ? "" : par.substring(igual + 1);
			pedido.param(java.net.URLDecoder.decode(nombre, java.nio.charset.StandardCharsets.UTF_8),
					java.net.URLDecoder.decode(valor, java.nio.charset.StandardCharsets.UTF_8));
		}
		return pedido;
	}

	// Los nombres de los experimentos de la respuesta, en el orden en que salieron. [*] recorre todos los
	// items. Comparar listas de nombres es la forma más legible de comprobar qué experimentos pasaron un
	// filtro y en qué orden.
	private static List<String> nombres(MvcResult r) throws Exception {
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.items[*].nombre");
	}

	// Un experimento de la cuenta '1' con esa fecha. ?::date convierte el texto a fecha de PostgreSQL.
	private int experimento(String nombre, String fecha) {
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', ?, ?::date)", nombre, fecha);
		return jdbc.queryForObject("SELECT idexperimento FROM experimento WHERE nombre = ?", Integer.class, nombre);
	}

	// Un grupo con ese tratamiento. La etiqueta se arma a partir de él ("G Acupuntura") para no repetirla.
	private int grupo(int experimento, String tratamiento) {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, 'control', ?)",
				experimento, "G " + tratamiento, tratamiento);
		return jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE idexperimento = ? AND tratamiento = ?", Integer.class,
				experimento, tratamiento);
	}

	private int tanda(int grupo) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 1, 2)", grupo);
		return jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ?", Integer.class, grupo);
	}

	private int video(int tanda, String sesion) {
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, ?, 'videos/x.mp4', 12)", tanda, sesion);
		return jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ? AND sesion = ?", Integer.class, tanda, sesion);
	}

	// Una tanda con su video del Día 2 y un análisis en ese estado.
	private void conAnalisis(int grupo, String estado) {
		int video = video(tanda(grupo), "Dia 2");
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, ?::estado_analisis)", video, estado);
	}

	private String login() throws Exception {
		MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"creador@fst.mx\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
	}

}
