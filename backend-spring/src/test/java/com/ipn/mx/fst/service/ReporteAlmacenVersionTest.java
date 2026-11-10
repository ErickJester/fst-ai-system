// Mismo paquete que la clase que prueba, dentro de src/test.
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
// Mockito, la biblioteca de objetos SIMULADOS (ver la explicación en el encabezado de la clase):
// anyString: "cualquier texto", al comprobar una llamada.
import static org.mockito.ArgumentMatchers.anyString;
// mock: crea el objeto simulado.
import static org.mockito.Mockito.mock;
// never: "esta llamada no debe haber ocurrido".
import static org.mockito.Mockito.never;
// verify: comprueba que una llamada ocurrió (o no).
import static org.mockito.Mockito.verify;
// when: programa qué hace el simulado ante una llamada.
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
// La excepción de Spring para "no se pudo llegar a la base".
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

// Un PDF guardado de otra versión no debe llegar a nadie: al arrancar, si la marca del volumen no es la versión actual,
// se borran los experimento.pdf y sus filas, y los CSV y XLSX no se tocan. Sin base de datos: el JdbcTemplate es simulado.
//
// Qué es un objeto SIMULADO (mock): un doble que se hace pasar por el real y que no hace nada salvo lo que
// se le programe. Aquí sustituye a JdbcTemplate, de modo que la prueba comprueba el comportamiento
// completo (qué archivos se borran, qué SQL se lanza, qué pasa si la base falla) sin PostgreSQL de por
// medio: corre en milisegundos y en cualquier máquina.
// Y permite probar algo que con una base de verdad sería incómodo: que si la base falla, el arranque se
// detenga. Con el simulado basta con decirle que lance una excepción.
class ReporteAlmacenVersionTest {

	// Una carpeta temporal nueva por prueba, que JUnit borra al terminar.
	@TempDir
	Path raiz;

	// El almacén construido a mano, con la raíz temporal y el JdbcTemplate que se le pase.
	private ReporteAlmacen almacen(JdbcTemplate jdbc) {
		ReporteAlmacen almacen = new ReporteAlmacen();
		ReflectionTestUtils.setField(almacen, "raizConfigurada", raiz.toString());
		ReflectionTestUtils.setField(almacen, "jdbc", jdbc);
		return almacen;
	}

	// La carpeta de reportes de un experimento, como la crearía el sistema.
	private Path carpetaDelExperimento() throws IOException {
		Path carpeta = raiz.resolve("reportes").resolve("3");
		Files.createDirectories(carpeta);
		return carpeta;
	}

	@Test
	void sin_marca_borra_los_pdf_viejos_y_deja_los_csv_y_escribe_la_marca_actual() throws IOException {
		// Se prepara el escenario: un PDF de la versión anterior y un CSV, sin archivo de marca (como una
		// instalación que viene de una versión previa del PDF).
		Path carpeta = carpetaDelExperimento();
		Files.writeString(carpeta.resolve("experimento.pdf"), "pdf de la versión anterior");
		Files.writeString(carpeta.resolve("grupo-1.csv"), "csv");
		JdbcTemplate jdbc = mock(JdbcTemplate.class);

		// prepararRaiz es lo que Spring llama al arrancar (@PostConstruct).
		almacen(jdbc).prepararRaiz();

		// Las tres comprobaciones, cada una con su motivo:
		// 1. el PDF viejo desaparece, que es el objetivo;
		assertFalse(Files.exists(carpeta.resolve("experimento.pdf")));
		// 2. el CSV NO se toca: no depende de la versión del PDF, y borrarlo sería trabajo de más;
		assertTrue(Files.exists(carpeta.resolve("grupo-1.csv")));
		// 3. la marca queda escrita con la versión actual, para que el próximo arranque no repita nada.
		assertEquals(ReporteAlmacen.VERSION_PDF, Files.readString(raiz.resolve("reportes").resolve("version-pdf.txt")));
		// Y que además se borraron las filas de la base: verify comprueba que se llamó a update. No se
		// mira el SQL exacto (anyString) para que la prueba no se rompa al reformular la consulta.
		verify(jdbc).update(anyString());
	}

	@Test
	void con_la_marca_de_la_version_actual_no_toca_nada() throws IOException {
		// El caso normal: la marca ya coincide, así que el arranque no debe hacer nada.
		Path carpeta = carpetaDelExperimento();
		Files.writeString(carpeta.resolve("experimento.pdf"), "pdf vigente");
		Files.writeString(raiz.resolve("reportes").resolve("version-pdf.txt"), ReporteAlmacen.VERSION_PDF);
		JdbcTemplate jdbc = mock(JdbcTemplate.class);

		almacen(jdbc).prepararRaiz();

		assertTrue(Files.exists(carpeta.resolve("experimento.pdf")));
		// verify con never(): la base NI SIQUIERA se toca. Es importante porque prepararRaiz corre en
		// CADA arranque, y borrar los PDF de todos los experimentos cada vez sería desastroso.
		verify(jdbc, never()).update(anyString());
	}

	@Test
	void si_la_base_falla_al_invalidar_el_arranque_se_detiene() throws IOException {
		carpetaDelExperimento();
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		// Se programa el simulado para que falle: when(...).thenThrow(...) es "cuando te llamen así,
		// lanza esto". Reproducir esto con una base real exigiría apagarla a media prueba.
		when(jdbc.update(anyString())).thenThrow(new DataAccessResourceFailureException("sin base"));
		ReporteAlmacen almacen = almacen(jdbc);

		// La decisión que se comprueba: si no se pueden invalidar los PDF, NO se arranca. Servir un PDF
		// de la versión anterior sería peor, porque nadie sabría que lo es.
		// almacen::prepararRaiz es una referencia a método, equivalente a () -> almacen.prepararRaiz().
		assertThrows(IllegalStateException.class, almacen::prepararRaiz);
	}
}
