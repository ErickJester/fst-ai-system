package com.ipn.mx.fst.utilerias;

import org.springframework.http.HttpStatus;

// Los servicios la lanzan cuando una regla de negocio rechaza la petición (404, 409, 403...). El controller
// no la atrapa: ManejadorErrores la convierte en el envoltorio {data, error, status} con este código HTTP.
public class ErrorApi extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final HttpStatus status;

	public ErrorApi(HttpStatus status, String mensaje) {
		super(mensaje);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

}
