package com.ipn.mx.fst.service;

import com.ipn.mx.fst.utilerias.ErrorApi;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// El Excel de todo el experimento, generado al momento (no se guarda: no pasa por ReporteAlmacen ni por la tabla REPORTE).
// Reúne lo que ya exportan los XLSX de grupo (una tabla por grupo con análisis completado) y los segundos de cada espécimen.
// 409 si el experimento todavía no tiene ningún análisis completado, como los demás reportes de tabla.
@Service
public class ReporteExcelExperimento {

	@Autowired
	private ResultadosService resultados;

	@Autowired
	private SegundosDeResultados segundos;

	@Transactional(readOnly = true)
	public byte[] armar(Integer idExperimento) throws IOException {
		List<TablaDeReporte> tablas = new ArrayList<>();
		for (Map<String, Object> grupo : resultados.resultadosPorGrupo(idExperimento)) {
			if (((Number) grupo.get("n")).intValue() == 0) {
				continue;
			}
			tablas.add(resultados.tablaDeReporte(idExperimento, (Integer) grupo.get("idGrupo"), null));
		}
		if (tablas.isEmpty()) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Todavía no hay análisis completados en el experimento.");
		}
		return ExcelDelExperimento.armar(tablas, segundos.delExperimento(idExperimento));
	}
}
