package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

// Un PDF guardado de otra versión no debe llegar a nadie: al arrancar, si la marca del volumen no es la versión actual,
// se borran los experimento.pdf y sus filas, y los CSV y XLSX no se tocan. Sin base de datos: el JdbcTemplate es simulado.
class ReporteAlmacenVersionTest {

	@TempDir
	Path raiz;

	private ReporteAlmacen almacen(JdbcTemplate jdbc) {
		ReporteAlmacen almacen = new ReporteAlmacen();
		ReflectionTestUtils.setField(almacen, "raizConfigurada", raiz.toString());
		ReflectionTestUtils.setField(almacen, "jdbc", jdbc);
		return almacen;
	}

	private Path carpetaDelExperimento() throws IOException {
		Path carpeta = raiz.resolve("reportes").resolve("3");
		Files.createDirectories(carpeta);
		return carpeta;
	}

	@Test
	void sin_marca_borra_los_pdf_viejos_y_deja_los_csv_y_escribe_la_marca_actual() throws IOException {
		Path carpeta = carpetaDelExperimento();
		Files.writeString(carpeta.resolve("experimento.pdf"), "pdf de la versión anterior");
		Files.writeString(carpeta.resolve("grupo-1.csv"), "csv");
		JdbcTemplate jdbc = mock(JdbcTemplate.class);

		almacen(jdbc).prepararRaiz();

		assertFalse(Files.exists(carpeta.resolve("experimento.pdf")));
		assertTrue(Files.exists(carpeta.resolve("grupo-1.csv")));
		assertEquals(ReporteAlmacen.VERSION_PDF, Files.readString(raiz.resolve("reportes").resolve("version-pdf.txt")));
		verify(jdbc).update(anyString());
	}

	@Test
	void con_la_marca_de_la_version_actual_no_toca_nada() throws IOException {
		Path carpeta = carpetaDelExperimento();
		Files.writeString(carpeta.resolve("experimento.pdf"), "pdf vigente");
		Files.writeString(raiz.resolve("reportes").resolve("version-pdf.txt"), ReporteAlmacen.VERSION_PDF);
		JdbcTemplate jdbc = mock(JdbcTemplate.class);

		almacen(jdbc).prepararRaiz();

		assertTrue(Files.exists(carpeta.resolve("experimento.pdf")));
		verify(jdbc, never()).update(anyString());
	}

	@Test
	void si_la_base_falla_al_invalidar_el_arranque_se_detiene() throws IOException {
		carpetaDelExperimento();
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.update(anyString())).thenThrow(new DataAccessResourceFailureException("sin base"));
		ReporteAlmacen almacen = almacen(jdbc);

		assertThrows(IllegalStateException.class, almacen::prepararRaiz);
	}
}
