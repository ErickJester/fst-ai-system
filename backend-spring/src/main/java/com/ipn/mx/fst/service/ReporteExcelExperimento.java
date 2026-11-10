// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// ErrorApi: la excepción con el código HTTP dentro.
import com.ipn.mx.fst.utilerias.ErrorApi;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
// @Service: Spring crea esta clase y la inyecta donde se pida.
import org.springframework.stereotype.Service;
// @Transactional: envuelve el método en una transacción de base de datos.
import org.springframework.transaction.annotation.Transactional;

// El Excel de todo el experimento, generado al momento (no se guarda: no pasa por ReporteAlmacen ni por la tabla REPORTE).
// Reúne lo que ya exportan los XLSX de grupo (una tabla por grupo con análisis completado) y los segundos de cada espécimen.
// 409 si el experimento todavía no tiene ningún análisis completado, como los demás reportes de tabla.
//
// Por qué este no se guarda y el PDF sí: el PDF es caro de armar (dibuja gráficas vectoriales) y se
// descarga muchas veces; este es una exportación de datos que se pide de vez en cuando, y guardarlo
// obligaría a invalidarlo en cada corrección de la revisión. No vale la pena la complicación.
// Fíjate en el reparto de trabajo: esta clase es la que DECIDE (qué grupos entran, cuándo fallar), y
// ExcelDelExperimento es la que DIBUJA el libro. Separar las reglas del formato permite cambiar el
// aspecto del archivo sin tocar nada de esto.
@Service
public class ReporteExcelExperimento {

	// Los resultados ya calculados por grupo. No se recalcula nada aquí.
	@Autowired
	private ResultadosService resultados;

	// La otra mitad del libro: el detalle segundo a segundo.
	@Autowired
	private SegundosDeResultados segundos;

	// readOnly = true: se le avisa a Hibernate y a PostgreSQL de que esta transacción no escribe. Permite
	// saltarse comprobaciones de cambios y es más barata.
	// La transacción es necesaria aunque solo se lea: las varias consultas de abajo deben ver la MISMA
	// foto de la base. Sin ella, una corrección hecha a mitad del proceso dejaría el libro incoherente,
	// con la tabla de un grupo calculada antes del cambio y la de otro después.
	@Transactional(readOnly = true)
	public byte[] armar(Integer idExperimento) throws IOException {
		List<TablaDeReporte> tablas = new ArrayList<>();
		// Una tabla por grupo, en el mismo orden en que vienen los resultados.
		for (Map<String, Object> grupo : resultados.resultadosPorGrupo(idExperimento)) {
			// n es cuántos especímenes con resultados tiene el grupo. Si es 0, el grupo no tiene ningún
			// análisis completado y se salta: una hoja con un grupo vacío solo confunde.
			// El doble paso (Number) ... .intValue() es necesario porque el mapa guarda Object y el número
			// puede llegar como Integer o como Long según de dónde salga; Number cubre los dos.
			if (((Number) grupo.get("n")).intValue() == 0) {
				continue;
			}
			// El null final es la tanda: se pide el grupo COMPLETO, no una tanda suelta.
			tablas.add(resultados.tablaDeReporte(idExperimento, (Integer) grupo.get("idGrupo"), null));
		}
		// Si ningún grupo tenía resultados, no hay libro que armar. 409 y no 404: el experimento existe,
		// pero todavía no está en condiciones de exportarse.
		// Al lanzarse dentro de la transacción, esta se deshace; aquí no se había escrito nada, pero es
		// la misma mecánica que describe ExperimentoController para el 409 del CSV.
		if (tablas.isEmpty()) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Todavía no hay análisis completados en el experimento.");
		}
		// Las dos mitades del libro: las tablas por grupo y el detalle segundo a segundo.
		return ExcelDelExperimento.armar(tablas, segundos.delExperimento(idExperimento));
	}
}
