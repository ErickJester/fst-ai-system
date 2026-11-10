// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// El servicio que arma el libro de Excel en memoria.
import com.ipn.mx.fst.service.ReporteExcelExperimento;
// IOException: armar el archivo puede fallar al escribir. Sube a ManejadorErrores como 500.
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
// ContentDisposition: la cabecera que dice si el archivo se muestra o se descarga, y con qué nombre.
import org.springframework.http.ContentDisposition;
// HttpHeaders: el conjunto de cabeceras de la respuesta.
import org.springframework.http.HttpHeaders;
// MediaType: el tipo de contenido (aquí, el de un .xlsx).
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
// GET, la variable de la ruta y el prefijo común.
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// El Excel de todo el experimento: hojas «Resultados» y «Segundo a segundo». Es un archivo, así que no lleva el envoltorio
// y se descarga (attachment). Se genera al momento; no se guarda.
//
// Es una de las pocas rutas que se saltan el envoltorio {data, error, status}, y por un motivo evidente:
// lo que devuelve son los bytes de un archivo, no un JSON. Los ERRORES de esta ruta sí llevan envoltorio,
// porque los arma ManejadorErrores: un 404 por un id que no existe sale con la forma de siempre.
// "No se guarda" la distingue del PDF: aquel pasa por ReporteAlmacen y queda en disco para no rehacerlo
// (ver la entidad Reporte); este se arma en memoria en cada descarga.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/experiments")
public class ExcelExperimentoController {

	// El tipo de contenido de un .xlsx. Es largo y se escribe una sola vez como constante.
	// Se usa parseMediaType porque MediaType no trae una constante para este formato.
	private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	@Autowired
	private ReporteExcelExperimento reporte;

	// GET /experiments/{id}/report/excel. Devuelve byte[] y no un Map: son los bytes del archivo.
	@GetMapping("/{id}/report/excel")
	public ResponseEntity<byte[]> excelDelExperimento(@PathVariable Integer id) throws IOException {
		HttpHeaders headers = new HttpHeaders();
		// attachment() hace que el navegador DESCARGUE el archivo en vez de intentar mostrarlo. Compárese
		// con el PDF, que usa inline() para abrirse en la pestaña: un .xlsx no se puede mostrar en el
		// navegador, así que descargarlo es lo único sensato.
		// filename fija el nombre con el que se guarda; sin él, el navegador inventaría uno a partir de
		// la URL y saldría algo como "excel".
		headers.setContentDisposition(ContentDisposition.attachment().filename("reporte_experimento_" + id + ".xlsx").build());
		// Se arma la respuesta encadenando: código 200, las cabeceras, el tipo de contenido y los bytes.
		// El servicio se llama DENTRO del body(...): si lanza un ErrorApi (porque el experimento no existe
		// o no tiene análisis completados), la excepción atraviesa el método y responde ManejadorErrores,
		// sin que aquí haga falta un try/catch.
		return ResponseEntity.ok().headers(headers).contentType(XLSX).body(reporte.armar(id));
	}
}
