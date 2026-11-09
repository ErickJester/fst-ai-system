package com.ipn.mx.fst.controller;

import com.ipn.mx.fst.service.ReporteExcelExperimento;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// El Excel de todo el experimento: hojas «Resultados» y «Segundo a segundo». Es un archivo, así que no lleva el envoltorio
// y se descarga (attachment). Se genera al momento; no se guarda.
@RestController
@RequestMapping("/experiments")
public class ExcelExperimentoController {

	private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	@Autowired
	private ReporteExcelExperimento reporte;

	@GetMapping("/{id}/report/excel")
	public ResponseEntity<byte[]> excelDelExperimento(@PathVariable Integer id) throws IOException {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentDisposition(ContentDisposition.attachment().filename("reporte_experimento_" + id + ".xlsx").build());
		return ResponseEntity.ok().headers(headers).contentType(XLSX).body(reporte.armar(id));
	}
}
