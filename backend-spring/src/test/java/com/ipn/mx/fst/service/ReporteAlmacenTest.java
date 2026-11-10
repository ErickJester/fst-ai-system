// Carpeta lógica de esta clase: com/ipn/mx/fst/service, dentro de src/test (no de src/main).
// Las pruebas van en un árbol paralelo, con el MISMO paquete que la clase que prueban: así pueden llamar
// a sus métodos de paquete (los que no son public) sin abrirlos a todo el mundo.
package com.ipn.mx.fst.service;

// Los métodos de comprobación de JUnit, traídos con import static para escribirlos sin prefijo.
// assertEquals: falla si los dos valores no son iguales.
import static org.junit.jupiter.api.Assertions.assertEquals;
// assertThrows: falla si el código NO lanza la excepción esperada.
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ipn.mx.fst.domain.valores.FormatoReporte;
// @Test: marca un método como prueba para que JUnit lo ejecute.
import org.junit.jupiter.api.Test;

// La ruta de cada reporte guardado (encargo 34): reportes/{idExperimento}/... según el formato y el alcance. Sin base de
// datos ni Docker.
//
// "Sin base de datos ni Docker" es lo que hace valiosa esta prueba: corre en milisegundos y se puede
// ejecutar en cualquier máquina sin preparar nada. Por eso rutaRelativa está escrito como un método
// estático y puro, sin tocar el disco ni la base (ver ReporteAlmacen).
// La clase no es public, y los métodos tampoco: en JUnit 5 no hace falta, y así no se exponen fuera.
// Los nombres de los métodos van con guiones bajos y describen la regla que comprueban: al fallar una
// prueba, lo primero que se lee es su nombre, y debería explicar qué se rompió sin abrir el archivo.
class ReporteAlmacenTest {

	@Test
	void el_pdf_es_del_experimento() {
		// La combinación válida del PDF: alcance "experimento".
		assertEquals("reportes/5/experimento.pdf", ReporteAlmacen.rutaRelativa(5, FormatoReporte.PDF, "experimento"));
	}

	@Test
	void el_csv_y_el_xlsx_son_del_grupo_o_de_la_tanda() {
		// Las combinaciones válidas de los formatos de tabla, con la extensión en minúsculas.
		assertEquals("reportes/5/grupo-3.csv", ReporteAlmacen.rutaRelativa(5, FormatoReporte.CSV, "grupo-3"));
		assertEquals("reportes/5/tanda-7.xlsx", ReporteAlmacen.rutaRelativa(5, FormatoReporte.XLSX, "tanda-7"));
	}

	@Test
	void el_pdf_no_puede_ser_de_grupo_ni_de_tanda() {
		// La otra mitad de la regla: no basta con que las combinaciones buenas funcionen, las malas tienen
		// que rechazarse. Una prueba que solo mira los casos buenos pasaría aunque el método aceptara todo.
		// La lambda () -> ... es el código que assertThrows ejecuta esperando que falle.
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.PDF, "grupo-3"));
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.PDF, "tanda-7"));
	}

	@Test
	void el_csv_y_el_xlsx_no_pueden_ser_del_experimento() {
		// El caso simétrico del anterior.
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.CSV, "experimento"));
	}

	@Test
	void un_alcance_con_otro_texto_se_rechaza_y_no_puede_salir_de_la_carpeta() {
		// Esta es una prueba de SEGURIDAD, no de formato: el alcance acaba dentro de una ruta de archivo.
		// "../6" saldría de la carpeta del experimento y "grupo-3/x" metería una subcarpeta. Si la
		// expresión regular de ReporteAlmacen se relajara algún día, esta prueba lo detendría.
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.CSV, "../6"));
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.CSV, "grupo-3/x"));
		// Y el null, que es el descuido más fácil de cometer.
		assertThrows(IllegalArgumentException.class, () -> ReporteAlmacen.rutaRelativa(5, FormatoReporte.CSV, null));
	}
}
