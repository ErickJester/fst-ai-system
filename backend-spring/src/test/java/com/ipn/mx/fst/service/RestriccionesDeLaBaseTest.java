package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Pruebas de las restricciones de db/init/001_esquema.sql: cada una intenta violar una regla con SQL directo y espera que
// la base la rechace (DataIntegrityViolationException: 23514 para CHECK y dominios, 23505 para UNIQUE, 23503 para llaves
// foráneas). Después se comprueba que la fila mala no quedó. Cada prueba de rechazo va con una prueba de control que
// inserta lo válido, para asegurar que el SQL está bien armado.
// Mismas condiciones que las demás pruebas de integración: solo corre con FST_TEST_DB_URL apuntando a una base de
// pruebas VACÍA con el esquema de db/init (cada prueba borra todos los datos).
@Tag("trabajo-terminal")
@EnabledIfEnvironmentVariable(named = "FST_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.datasource.url=${FST_TEST_DB_URL}",
		"spring.datasource.username=${FST_TEST_DB_USER:fst}",
		"spring.datasource.password=${FST_TEST_DB_PASSWORD:fst}",
		"app.jwt.secreto=secreto-de-pruebas-secreto-de-pruebas-1234",
		"app.archivos.raiz=${java.io.tmpdir}/fst-pruebas-restricciones"
})
class RestriccionesDeLaBaseTest {

	@Autowired
	private JdbcTemplate jdbc;

	// Borra todo al empezar y al terminar: así no deja filas que rompan las demás pruebas de la base.
	@AfterEach
	void limpia_al_terminar() {
		vaciar();
	}

	@BeforeEach
	void base_limpia() {
		vaciar();
	}

	private void vaciar() {
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
	}

	// ------------------------------------------------------------------ piezas de la cadena válida

	private void usuario(String id, String correo) {
		jdbc.update("INSERT INTO usuario (idinstitucional, nombre, apellidos, correo, contrasenahash, activo, cambiorequerido) "
				+ "VALUES (?, 'Ana', 'Pérez', ?, 'hash-de-prueba', true, false)", id, correo);
	}

	private int experimento(String nombre) {
		jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', ?, current_date)", nombre);
		return jdbc.queryForObject("SELECT idexperimento FROM experimento WHERE nombre = ?", Integer.class, nombre);
	}

	private int grupo(int idExperimento, String etiqueta) {
		jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, ?, 'control', 'agua')",
				idExperimento, etiqueta);
		return jdbc.queryForObject("SELECT idgrupo FROM grupo WHERE idexperimento = ? AND etiqueta = ?", Integer.class,
				idExperimento, etiqueta);
	}

	private int tanda(int idGrupo, int ordinal, int ncilindros) {
		jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, ?, ?)", idGrupo, ordinal, ncilindros);
		return jdbc.queryForObject("SELECT idtanda FROM tanda WHERE idgrupo = ? AND ordinal = ?", Integer.class,
				idGrupo, ordinal);
	}

	private int especimen(int idTanda, int numeroCilindro) {
		jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, ?)", idTanda, numeroCilindro);
		return jdbc.queryForObject("SELECT idespecimen FROM especimen WHERE idtanda = ? AND numerocilindro = ?",
				Integer.class, idTanda, numeroCilindro);
	}

	private int video(int idTanda, String sesion) {
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, ?, NULL, 300)", idTanda, sesion);
		return jdbc.queryForObject("SELECT idvideo FROM video WHERE idtanda = ? AND sesion = ?", Integer.class,
				idTanda, sesion);
	}

	private int analisis(int idVideo) {
		jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, 'en cola')", idVideo);
		return jdbc.queryForObject("SELECT idanalisis FROM analisis WHERE idvideo = ?", Integer.class, idVideo);
	}

	private int observacion(int idEspecimen, int idVideo) {
		jdbc.update("INSERT INTO observacion (idespecimen, idvideo) VALUES (?, ?)", idEspecimen, idVideo);
		return jdbc.queryForObject("SELECT idobservacion FROM observacion WHERE idespecimen = ? AND idvideo = ?",
				Integer.class, idEspecimen, idVideo);
	}

	// Una cadena completa y válida: usuario → experimento → grupo → tanda → espécimen y video → análisis → observación.
	private record Cadena(int idExperimento, int idGrupo, int idTanda, int idEspecimen, int idVideo, int idAnalisis,
			int idObservacion) {
	}

	private Cadena cadenaValida() {
		usuario("1", "ana@fst.mx");
		int exp = experimento("Experimento de prueba");
		int grp = grupo(exp, "Control");
		int tan = tanda(grp, 1, 4);
		int esp = especimen(tan, 1);
		int vid = video(tan, "Dia 2");
		int ana = analisis(vid);
		int obs = observacion(esp, vid);
		return new Cadena(exp, grp, tan, esp, vid, ana, obs);
	}

	private int cuenta(String sql, Object... argumentos) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") AS c", Integer.class, argumentos);
	}

	// ------------------------------------------------------------------ controles: lo válido sí entra

	@Test
	void control_la_cadena_completa_y_valida_se_inserta_sin_error() {
		Cadena c = cadenaValida();
		assertEquals(1, cuenta("SELECT idanalisis FROM analisis WHERE idanalisis = ?", c.idAnalisis()));
		assertEquals(1, cuenta("SELECT idobservacion FROM observacion WHERE idobservacion = ?", c.idObservacion()));
	}

	@Test
	void control_tandas_de_dos_y_cuatro_cilindros_son_validas() {
		usuario("1", "ana@fst.mx");
		int grp = grupo(experimento("Exp dos y cuatro"), "Control");
		tanda(grp, 1, 2);
		tanda(grp, 2, 4);
		assertEquals(2, cuenta("SELECT idtanda FROM tanda WHERE idgrupo = ?", grp));
	}

	@Test
	void control_segundos_1_y_300_son_validos() {
		Cadena c = cadenaValida();
		jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 1, 'nado activo', 'maquina')",
				c.idObservacion());
		jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 300, NULL, 'maquina')",
				c.idObservacion());
		assertEquals(2, cuenta("SELECT segundo FROM segundo WHERE idobservacion = ?", c.idObservacion()));
	}

	@Test
	void control_video_con_duracion_positiva_es_valido() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp video"), "Control"), 1, 4);
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 1', 'videos/1.mp4', 0.01)", tan);
		assertEquals(1, cuenta("SELECT idvideo FROM video WHERE idtanda = ?", tan));
	}

	@Test
	void control_analisis_con_error_y_diagnostico_es_valido() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp diagnostico"), "Control"), 1, 4);
		int vid = video(tan, "Dia 2");
		jdbc.update("INSERT INTO analisis (idvideo, estado, rutadiagnostico) VALUES (?, 'error', 'diagnostico.pdf')", vid);
		assertEquals(1, cuenta("SELECT idanalisis FROM analisis WHERE idvideo = ? AND estado = 'error'", vid));
	}

	// ------------------------------------------------------------------ CHECK

	@Test
	void tanda_con_uno_cilindro_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int grp = grupo(experimento("Exp ncilindros uno"), "Control");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 1, 1)", grp));
		assertEquals(0, cuenta("SELECT idtanda FROM tanda WHERE idgrupo = ?", grp));
	}

	@Test
	void tanda_con_cinco_cilindros_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int grp = grupo(experimento("Exp ncilindros cinco"), "Control");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 1, 5)", grp));
		assertEquals(0, cuenta("SELECT idtanda FROM tanda WHERE idgrupo = ?", grp));
	}

	@Test
	void tanda_con_ordinal_cero_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int grp = grupo(experimento("Exp ordinal cero"), "Control");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 0, 4)", grp));
		assertEquals(0, cuenta("SELECT idtanda FROM tanda WHERE idgrupo = ?", grp));
	}

	@Test
	void especimen_con_numero_de_cilindro_cero_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp cilindro cero"), "Control"), 1, 4);
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, 0)", tan));
		assertEquals(0, cuenta("SELECT idespecimen FROM especimen WHERE idtanda = ?", tan));
	}

	@Test
	void especimen_con_numero_de_cilindro_cinco_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp cilindro cinco"), "Control"), 1, 4);
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, 5)", tan));
		assertEquals(0, cuenta("SELECT idespecimen FROM especimen WHERE idtanda = ?", tan));
	}

	@Test
	void segundo_cero_se_rechaza() {
		Cadena c = cadenaValida();
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 0, 'nado activo', 'maquina')",
						c.idObservacion()));
		assertEquals(0, cuenta("SELECT segundo FROM segundo WHERE idobservacion = ?", c.idObservacion()));
	}

	@Test
	void segundo_301_se_rechaza() {
		Cadena c = cadenaValida();
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 301, 'nado activo', 'maquina')",
						c.idObservacion()));
		assertEquals(0, cuenta("SELECT segundo FROM segundo WHERE idobservacion = ?", c.idObservacion()));
	}

	@Test
	void video_con_duracion_cero_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp duracion cero"), "Control"), 1, 4);
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', NULL, 0)", tan));
		assertEquals(0, cuenta("SELECT idvideo FROM video WHERE idtanda = ?", tan));
	}

	@Test
	void video_con_duracion_negativa_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp duracion negativa"), "Control"), 1, 4);
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', NULL, -5)", tan));
		assertEquals(0, cuenta("SELECT idvideo FROM video WHERE idtanda = ?", tan));
	}

	@Test
	void analisis_con_diagnostico_y_estado_distinto_de_error_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp diagnostico en cola"), "Control"), 1, 4);
		int vid = video(tan, "Dia 2");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO analisis (idvideo, estado, rutadiagnostico) VALUES (?, 'en cola', 'diagnostico.pdf')",
						vid));
		assertEquals(0, cuenta("SELECT idanalisis FROM analisis WHERE idvideo = ?", vid));
	}

	// ------------------------------------------------------------------ dominios

	@Test
	void grupo_con_tipo_fuera_del_dominio_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int exp = experimento("Exp tipo de grupo");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) VALUES (?, 'X', 'otro', 'agua')",
						exp));
		assertEquals(0, cuenta("SELECT idgrupo FROM grupo WHERE idexperimento = ?", exp));
	}

	@Test
	void analisis_con_estado_fuera_del_dominio_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp estado"), "Control"), 1, 4);
		int vid = video(tan, "Dia 2");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, 'pendiente')", vid));
		assertEquals(0, cuenta("SELECT idanalisis FROM analisis WHERE idvideo = ?", vid));
	}

	@Test
	void segundo_con_conducta_fuera_del_dominio_se_rechaza() {
		Cadena c = cadenaValida();
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 2, 'volando', 'maquina')",
						c.idObservacion()));
		assertEquals(0, cuenta("SELECT segundo FROM segundo WHERE idobservacion = ?", c.idObservacion()));
	}

	// ------------------------------------------------------------------ UNIQUE

	@Test
	void correo_de_usuario_repetido_se_rechaza() {
		usuario("1", "repetido@fst.mx");
		assertThrows(DataIntegrityViolationException.class,
				() -> usuario("2", "repetido@fst.mx"));
		assertEquals(0, cuenta("SELECT idinstitucional FROM usuario WHERE idinstitucional = '2'"));
	}

	@Test
	void ordinal_repetido_dentro_del_mismo_grupo_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int grp = grupo(experimento("Exp ordinal repetido"), "Control");
		tanda(grp, 1, 4);
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 1, 4)", grp));
		assertEquals(1, cuenta("SELECT idtanda FROM tanda WHERE idgrupo = ?", grp));
	}

	@Test
	void sesion_repetida_en_la_misma_tanda_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp sesion repetida"), "Control"), 1, 4);
		video(tan, "Dia 2");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', NULL, 300)", tan));
		assertEquals(1, cuenta("SELECT idvideo FROM video WHERE idtanda = ?", tan));
	}

	@Test
	void numero_de_cilindro_repetido_en_la_misma_tanda_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp cilindro repetido"), "Control"), 1, 4);
		especimen(tan, 2);
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, 2)", tan));
		assertEquals(1, cuenta("SELECT idespecimen FROM especimen WHERE idtanda = ?", tan));
	}

	@Test
	void nombre_de_experimento_repetido_se_rechaza() {
		usuario("1", "ana@fst.mx");
		experimento("Mismo nombre");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES ('1', 'Mismo nombre', current_date)"));
		assertEquals(1, cuenta("SELECT idexperimento FROM experimento WHERE nombre = 'Mismo nombre'"));
	}

	@Test
	void segundo_analisis_del_mismo_video_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp un analisis"), "Control"), 1, 4);
		int vid = video(tan, "Dia 2");
		analisis(vid);
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, 'en cola')", vid));
		assertEquals(1, cuenta("SELECT idanalisis FROM analisis WHERE idvideo = ?", vid));
	}

	// ------------------------------------------------------------------ llaves foráneas con NO ACTION

	@Test
	void no_se_borra_un_usuario_que_tiene_experimentos() {
		cadenaValida();
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("DELETE FROM usuario WHERE idinstitucional = '1'"));
		assertEquals(1, cuenta("SELECT idinstitucional FROM usuario WHERE idinstitucional = '1'"));
	}

	@Test
	void no_se_borra_un_experimento_que_tiene_grupos() {
		Cadena c = cadenaValida();
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("DELETE FROM experimento WHERE idexperimento = ?", c.idExperimento()));
		assertEquals(1, cuenta("SELECT idexperimento FROM experimento WHERE idexperimento = ?", c.idExperimento()));
	}

	@Test
	void no_se_borra_un_grupo_que_tiene_tandas() {
		Cadena c = cadenaValida();
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("DELETE FROM grupo WHERE idgrupo = ?", c.idGrupo()));
		assertEquals(1, cuenta("SELECT idgrupo FROM grupo WHERE idgrupo = ?", c.idGrupo()));
	}

	@Test
	void no_se_borra_una_tanda_que_tiene_videos() {
		Cadena c = cadenaValida();
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("DELETE FROM tanda WHERE idtanda = ?", c.idTanda()));
		assertEquals(1, cuenta("SELECT idtanda FROM tanda WHERE idtanda = ?", c.idTanda()));
	}

	@Test
	void no_se_borra_un_video_que_tiene_analisis() {
		Cadena c = cadenaValida();
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("DELETE FROM video WHERE idvideo = ?", c.idVideo()));
		assertEquals(1, cuenta("SELECT idvideo FROM video WHERE idvideo = ?", c.idVideo()));
	}
}
