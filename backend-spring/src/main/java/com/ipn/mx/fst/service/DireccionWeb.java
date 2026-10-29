package com.ipn.mx.fst.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// La dirección de la web con la que se arman los enlaces de los correos (restablecer contraseña, primer acceso). Sale de WEB_URL;
// si está vacía o no existe, se usa la primera de CORS_ORIGINS, que es desde donde se abre la web.
@Component
public class DireccionWeb {

	@Value("${app.web.url:}")
	private String configurada;

	@Value("${app.cors.origenes}")
	private String origenesCors;

	// Sin diagonal final.
	public String base() {
		String url = configurada == null || configurada.isBlank() ? origenesCors.split(",")[0].trim() : configurada.trim();
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
