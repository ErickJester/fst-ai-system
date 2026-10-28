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
	@BeforeEach
	void base_limpia() {
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
		int gamma = experimento("Gamma", "2025-03-10");
		tanda(grupo(gamma, "Control"));
		int delta = experimento("Delta", "2026-10-25");
		int tandaDelta = tanda(grupo(delta, "Control"));
		video(tandaDelta, "Dia 1");
		int epsilon = experimento("Epsilon", "2026-01-02");
		conAnalisis(grupo(epsilon, "Acupuntura"), "error");
		tanda(grupo(epsilon, "Control"));
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
				.andExpect(jsonPath("$.data.items.length()").value(20))
				.andReturn();
		// Más reciente primero; a igual fecha, el de id más alto (Delta se creó después que Beta).
		assertEquals(List.of("Delta", "Beta", "Alfa", "Epsilon", "Gamma"), nombres(r).subList(0, 5));
		mvc.perform(pedir("pagina=2", login()))
				.andExpect(jsonPath("$.data.items.length()").value(10)).andExpect(jsonPath("$.data.total").value(30));
		mvc.perform(pedir("pagina=3", login()))
				.andExpect(jsonPath("$.data.items.length()").value(0)).andExpect(jsonPath("$.data.total").value(30));
		// Filtros en blanco son sin filtro.
		mvc.perform(pedir("estado=&fecha=%20&tratamiento=", login()))
				.andExpect(jsonPath("$.data.total").value(30)).andExpect(jsonPath("$.data.items.length()").value(20));
	}

	@Test
	void cada_estado_filtra_los_experimentos_que_tienen_ese_estado() throws Exception {
		assertEquals(List.of("Alfa"), nombres(lista("estado=completado")));
		assertEquals(List.of("Beta"), nombres(lista("estado=en%20cola")));
		assertEquals(List.of("Epsilon"), nombres(lista("estado=error")));
		assertEquals(List.of("Delta"), nombres(lista("estado=en%20espera%20del%20D%C3%ADa%202")));
		assertEquals(26, (int) JsonPath.read(lista("estado=sin%20videos").getResponse().getContentAsString(), "$.data.total"));
		// Estados válidos sin ningún experimento: lista vacía, total 0.
		MvcResult r = lista("estado=procesando");
		assertEquals(List.of(), nombres(r));
		assertEquals(0, (int) JsonPath.read(r.getResponse().getContentAsString(), "$.data.total"));
	}

	@Test
	void la_fecha_puede_ser_un_anio_o_un_dia() throws Exception {
		assertEquals(List.of("Delta", "Beta", "Alfa", "Epsilon"), nombres(lista("fecha=2026")));
		assertEquals(List.of("Gamma"), nombres(lista("fecha=2025")));
		assertEquals(List.of("Delta", "Beta"), nombres(lista("fecha=2026-10-25")));
		assertEquals(List.of("Alfa"), nombres(lista("fecha=2026-10-20")));
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
		assertEquals(List.of("Beta"), nombres(lista("tratamiento=%C3%81CIDO%20F%C3%93LICO")));
		assertEquals(List.of(), nombres(lista("tratamiento=Acup")));      // el tratamiento completo, no un pedazo
		assertEquals(List.of(), nombres(lista("tratamiento=nada")));
	}

	@Test
	void los_filtros_combinados_se_cumplen_todos() throws Exception {
		assertEquals(List.of("Beta"), nombres(lista("estado=en%20cola&fecha=2026&tratamiento=acido%20folico")));
		assertEquals(List.of("Epsilon"), nombres(lista("estado=error&tratamiento=control")));
		assertEquals(List.of("Delta"), nombres(lista("fecha=2026-10-25&tratamiento=Control")));
		assertEquals(List.of(), nombres(lista("estado=completado&tratamiento=control")));
		assertEquals(List.of(), nombres(lista("estado=completado&fecha=2025")));
	}

	@Test
	void con_filtro_el_total_son_los_que_pasan_y_las_paginas_se_cortan_de_20_en_20() throws Exception {
		MvcResult primera = lista("tratamiento=masivo");
		assertEquals(25, (int) JsonPath.read(primera.getResponse().getContentAsString(), "$.data.total"));
		assertEquals(20, nombres(primera).size());
		assertEquals(1, (int) JsonPath.read(primera.getResponse().getContentAsString(), "$.data.pagina"));
		MvcResult segunda = lista("tratamiento=masivo&pagina=2");
		assertEquals(5, nombres(segunda).size());
		assertEquals(25, (int) JsonPath.read(segunda.getResponse().getContentAsString(), "$.data.total"));
		// Las dos páginas juntas son los 25, sin repetir, del más reciente al más antiguo (id descendente a igual fecha).
		List<String> juntos = new java.util.ArrayList<>(nombres(primera));
		juntos.addAll(nombres(segunda));
		assertEquals(25, juntos.stream().distinct().count());
		assertEquals("Relleno 25", juntos.get(0));
		assertEquals("Relleno 01", juntos.get(24));

		// Una página fuera de rango: lista vacía con el total correcto.
		MvcResult fuera = lista("tratamiento=masivo&pagina=3");
		assertEquals(List.of(), nombres(fuera));
		assertEquals(25, (int) JsonPath.read(fuera.getResponse().getContentAsString(), "$.data.total"));
		assertEquals(3, (int) JsonPath.read(fuera.getResponse().getContentAsString(), "$.data.pagina"));
		MvcResult muyLejos = lista("tratamiento=masivo&pagina=99999999");
		assertEquals(List.of(), nombres(muyLejos));
		assertEquals(25, (int) JsonPath.read(muyLejos.getResponse().getContentAsString(), "$.data.total"));
	}

	@Test
	void con_filtro_cada_item_trae_los_mismos_campos_que_sin_filtro() throws Exception {
		String sinFiltro = mvc.perform(pedir("", login())).andReturn().getResponse().getContentAsString();
		String conFiltro = lista("estado=completado").getResponse().getContentAsString();
		List<java.util.Map<String, Object>> filtrado = JsonPath.read(conFiltro, "$.data.items");
		List<java.util.Map<String, Object>> deSiempre = JsonPath.read(sinFiltro, "$.data.items[?(@.nombre=='Alfa')]");
		assertEquals(1, filtrado.size());
		assertEquals(deSiempre.get(0), filtrado.get(0));
		assertEquals("completado", filtrado.get(0).get("estado"));
		assertEquals(List.of("Acupuntura"), filtrado.get(0).get("tratamientos"));
	}

	@Test
	void un_valor_que_no_es_valido_da_400_en_cada_filtro() throws Exception {
		String sesion = login();
		for (String consulta : List.of("estado=terminado", "estado=Completado", "estado=en%20espera", "fecha=26", "fecha=20261",
				"fecha=2026-10", "fecha=2026-13-01", "fecha=2026-02-30", "fecha=abcd", "fecha=2026/10/25", "fecha=25-10-2026",
				"pagina=0&estado=error", "pagina=x&fecha=2026")) {
			mvc.perform(pedir(consulta, sesion))
					.andExpect(status().isBadRequest());
		}
		// Y una sesión sigue siendo obligatoria.
		mvc.perform(get("/experiments?estado=error")).andExpect(status().isUnauthorized());
	}

	// ----------------------------------------------------------------- apoyo

	private MvcResult lista(String consulta) throws Exception {
		return mvc.perform(pedir(consulta, login())).andExpect(status().isOk()).andReturn();
	}

	// La consulta ya viene codificada como en una URL («%20»): se separa y se decodifica aquí, porque MockMvc volvería a
	// codificar el «%» si la recibiera en la URL.
	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedir(String consulta, String sesion) {
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido = get("/experiments")
				.header("Authorization", "Bearer " + sesion);
		for (String par : consulta.isEmpty() ? new String[0] : consulta.split("&")) {
			int igual = par.indexOf('=');
			String nombre = igual < 0 ? par : par.substring(0, igual);
			String valor = igual < 0 ? "" : par.substring(igual + 1);
			pedido.param(java.net.URLDecoder.decode(nombre, java.nio.charset.StandardCharsets.UTF_8),
					java.net.URLDecoder.decode(valor, java.nio.charset.StandardCharsets.UTF_8));
		}
		return pedido;
	}

	private static List<String> nombres(MvcResult r) throws Exception {
		return JsonPath.read(r.getResponse().getContentAsString(), "$.data.items[*].nombre");
	}

	private int experimento(String nombre, String fecha) {
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', ?, ?::date)", nombre, fecha);
		return jdbc.queryForObject("SELECT idexperimento FROM experimento WHERE nombre = ?", Integer.class, nombre);
	}

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
