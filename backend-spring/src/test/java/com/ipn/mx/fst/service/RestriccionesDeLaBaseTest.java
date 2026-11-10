// Mismo paquete que las clases que prueba, dentro de src/test.
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
// DataIntegrityViolationException: la excepción de Spring que envuelve cualquier violación de una
// restricción de la base (CHECK, UNIQUE, llave foránea). Es lo que estas pruebas esperan que salte.
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Pruebas de las restricciones de db/init/001_esquema.sql: cada una intenta violar una regla con SQL directo y espera que
// la base la rechace (DataIntegrityViolationException: 23514 para CHECK y dominios, 23505 para UNIQUE, 23503 para llaves
// foráneas). Después se comprueba que la fila mala no quedó. Cada prueba de rechazo va con una prueba de control que
// inserta lo válido, para asegurar que el SQL está bien armado.
// Mismas condiciones que las demás pruebas de integración: solo corre con FST_TEST_DB_URL apuntando a una base de
// pruebas VACÍA con el esquema de db/init (cada prueba borra todos los datos).
//
// Esta clase es distinta a todas las demás: NO prueba código Java, prueba el ESQUEMA de la base. Y eso
// es importante porque buena parte de las reglas del modelo viven ahí y no en el código (ver
// application.properties: ddl-auto=validate existe justo porque el SQL trae CHECK y dominios que
// Hibernate no genera).
// Escribe con SQL directo (JdbcTemplate) y no con los servicios, a propósito: así se prueba la base sola,
// sin que una validación de Java se adelante y oculte un hueco en el esquema. La base es la ÚLTIMA defensa
// y tiene que sostenerse aunque el código falle (ver ManejadorErrores.choqueDeDatos).
// Los códigos de la cabecera son los SQLSTATE de PostgreSQL: 23514 (check_violation), 23505
// (unique_violation) y 23503 (foreign_key_violation).
// Por qué hay "controles": una prueba de rechazo puede pasar por la razón equivocada. Si el INSERT tuviera
// un error de sintaxis, también lanzaría excepción y la prueba daría por buena una restricción que quizá
// no existe. El control inserta LO VÁLIDO con el mismo SQL y comprueba que entra, demostrando que lo que
// falla en la prueba de rechazo es la regla y no el SQL.
// Las dos comprobaciones de cada prueba de rechazo (excepción Y que la fila no quedó) cubren dos cosas: que
// la base se negó, y que no dejó la inserción a medias.
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

	// De las hojas hacia arriba, por las llaves foráneas.
	private void vaciar() {
		for (String tabla : List.of("segundo", "observacion", "especimen", "reporte", "analisis", "video", "tanda", "grupo",
				"notificacion", "experimento", "administrador", "usuario")) {
			jdbc.update("DELETE FROM " + tabla);
		}
	}

	// ------------------------------------------------------------------ piezas de la cadena válida
	//
	// Un método por cada tabla, para construir la cadena que pide el modelo (usuario → experimento → grupo →
	// tanda → espécimen y video → análisis → observación). Cada prueba usa solo las piezas que necesita.

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

	// archivo NULL: un video ya borrado, que es una situación válida (ver la entidad Video).
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
	// Un record con todos los ids, para que cada prueba tome el que necesite.
	private record Cadena(int idExperimento, int idGrupo, int idTanda, int idEspecimen, int idVideo, int idAnalisis,
			int idObservacion) {
	}

	// Construye la cadena entera con valores válidos y devuelve sus ids.
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

	// Cuenta las filas de una consulta, envolviéndola en un COUNT(*). Varargs para los parámetros.
	private int cuenta(String sql, Object... argumentos) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") AS c", Integer.class, argumentos);
	}

	// ------------------------------------------------------------------ controles: lo válido sí entra
	//
	// Los controles de las pruebas de rechazo de más abajo. Cubren los BORDES válidos (2 y 4 cilindros,
	// segundos 1 y 300, duración positiva), que son los que deberían quedar justo dentro de cada CHECK.

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
		// 2 y 4 son los DOS extremos permitidos de la regla "de 2 a 4 cilindros".
		tanda(grp, 1, 2);
		tanda(grp, 2, 4);
		assertEquals(2, cuenta("SELECT idtanda FROM tanda WHERE idgrupo = ?", grp));
	}

	@Test
	void control_segundos_1_y_300_son_validos() {
		Cadena c = cadenaValida();
		jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 1, 'nado activo', 'maquina')",
				c.idObservacion());
		// Un segundo con conducta NULL también es válido: es el «sin ver» (ver la entidad Segundo).
		jdbc.update("INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES (?, 300, NULL, 'maquina')",
				c.idObservacion());
		assertEquals(2, cuenta("SELECT segundo FROM segundo WHERE idobservacion = ?", c.idObservacion()));
	}

	@Test
	void control_video_con_duracion_positiva_es_valido() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp video"), "Control"), 1, 4);
		// 0.01 es el valor positivo más pequeño que admite NUMERIC(8,2): el borde de la regla "mayor que cero".
		jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 1', 'videos/1.mp4', 0.01)", tan);
		assertEquals(1, cuenta("SELECT idvideo FROM video WHERE idtanda = ?", tan));
	}

	@Test
	void control_analisis_con_error_y_diagnostico_es_valido() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp diagnostico"), "Control"), 1, 4);
		int vid = video(tan, "Dia 2");
		// El caso permitido de la regla "solo hay diagnóstico si el estado es error".
		jdbc.update("INSERT INTO analisis (idvideo, estado, rutadiagnostico) VALUES (?, 'error', 'diagnostico.pdf')", vid);
		assertEquals(1, cuenta("SELECT idanalisis FROM analisis WHERE idvideo = ? AND estado = 'error'", vid));
	}

	// ------------------------------------------------------------------ CHECK
	//
	// Un CHECK es una condición que cada fila tiene que cumplir. Estas pruebas fijan los valores JUSTO
	// FUERA de cada rango (1 y 5 cilindros, 0 y 301 segundos, duración 0 y negativa).
	// assertThrows recibe una lambda () -> ... con el INSERT que debe fallar.

	@Test
	void tanda_con_uno_cilindro_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int grp = grupo(experimento("Exp ncilindros uno"), "Control");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 1, 1)", grp));
		// Y que la fila mala NO quedó: la base no deja inserciones a medias.
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
		// Los ordinales empiezan en 1 (A). El 0 no es una letra.
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
		// En el cuadro caben cuatro cilindros como máximo.
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, 5)", tan));
		assertEquals(0, cuenta("SELECT idespecimen FROM especimen WHERE idtanda = ?", tan));
	}

	@Test
	void segundo_cero_se_rechaza() {
		Cadena c = cadenaValida();
		// Los segundos van del 1 al 300 (ver SegundoId).
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
		// Es la regla de la columna rutadiagnostico: solo se llena cuando el estado es 'error' (ver la
		// entidad Analisis). Un CHECK que relaciona DOS columnas, a diferencia de los de arriba.
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO analisis (idvideo, estado, rutadiagnostico) VALUES (?, 'en cola', 'diagnostico.pdf')",
						vid));
		assertEquals(0, cuenta("SELECT idanalisis FROM analisis WHERE idvideo = ?", vid));
	}

	// ------------------------------------------------------------------ dominios
	//
	// Un dominio es la lista cerrada de textos que admite una columna (ver ValorDeBase y los enums de
	// domain/valores). Aquí se comprueba que la base rechaza un texto que no está en la lista, que es
	// justo lo que garantiza que los enums de Java y el esquema no puedan descuadrarse en silencio.

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
	//
	// Una restricción UNIQUE impide que dos filas repitan un valor (o una combinación). Son las que
	// ErrorApi y ManejadorErrores traducen a un 409 cuando ningún servicio las comprobó antes.

	@Test
	void correo_de_usuario_repetido_se_rechaza() {
		usuario("1", "repetido@fst.mx");
		// Aquí la excepción salta DENTRO del método de apoyo usuario(), que es el que hace el INSERT.
		assertThrows(DataIntegrityViolationException.class,
				() -> usuario("2", "repetido@fst.mx"));
		assertEquals(0, cuenta("SELECT idinstitucional FROM usuario WHERE idinstitucional = '2'"));
	}

	@Test
	void ordinal_repetido_dentro_del_mismo_grupo_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int grp = grupo(experimento("Exp ordinal repetido"), "Control");
		tanda(grp, 1, 4);
		// Dos tandas A en el mismo grupo: es la restricción que hace que la letra de una tanda sea única.
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (?, 1, 4)", grp));
		assertEquals(1, cuenta("SELECT idtanda FROM tanda WHERE idgrupo = ?", grp));
	}

	@Test
	void sesion_repetida_en_la_misma_tanda_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp sesion repetida"), "Control"), 1, 4);
		video(tan, "Dia 2");
		// Una tanda lleva como mucho UN video por sesión (ver la entidad Video).
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO video (idtanda, sesion, archivo, duracion) VALUES (?, 'Dia 2', NULL, 300)", tan));
		assertEquals(1, cuenta("SELECT idvideo FROM video WHERE idtanda = ?", tan));
	}

	@Test
	void numero_de_cilindro_repetido_en_la_misma_tanda_se_rechaza() {
		usuario("1", "ana@fst.mx");
		int tan = tanda(grupo(experimento("Exp cilindro repetido"), "Control"), 1, 4);
		especimen(tan, 2);
		// Un espécimen se identifica solo por su posición (ver la entidad Especimen): dos en el mismo
		// cilindro serían indistinguibles.
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO especimen (idtanda, numerocilindro) VALUES (?, 2)", tan));
		assertEquals(1, cuenta("SELECT idespecimen FROM especimen WHERE idtanda = ?", tan));
	}

	@Test
	void nombre_de_experimento_repetido_se_rechaza() {
		usuario("1", "ana@fst.mx");
		experimento("Mismo nombre");
		// RF-11: el nombre es único en todo el sistema. ExperimentoServiceImpl.crear lo comprueba antes
		// para dar un 409 claro; esto prueba que, si dos peticiones se cruzan, la base sigue defendiéndolo.
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
		// Un video tiene como mucho UN análisis (por eso la relación de la entidad Analisis es @OneToOne
		// con unique = true). Reanalizar reinicia ese análisis, no crea otro.
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO analisis (idvideo, estado) VALUES (?, 'en cola')", vid));
		assertEquals(1, cuenta("SELECT idanalisis FROM analisis WHERE idvideo = ?", vid));
	}

	// ------------------------------------------------------------------ llaves foráneas con NO ACTION
	//
	// Las llaves foráneas están declaradas ON DELETE NO ACTION: la base se niega a borrar un padre que aún
	// tiene hijos, en vez de borrarlos en cascada. Es una decisión consciente (ver ExperimentoServiceImpl.eliminar):
	// el borrado hay que escribirlo de las hojas hacia arriba, y así queda a la vista todo lo que desaparece.
	// Cada prueba intenta borrar un padre con la cadena completa debajo y espera que la base se niegue.

	@Test
	void no_se_borra_un_usuario_que_tiene_experimentos() {
		cadenaValida();
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("DELETE FROM usuario WHERE idinstitucional = '1'"));
		// Y sigue ahí. Es la razón de que las cuentas se desactiven y nunca se borren (ver Usuario.activo).
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
