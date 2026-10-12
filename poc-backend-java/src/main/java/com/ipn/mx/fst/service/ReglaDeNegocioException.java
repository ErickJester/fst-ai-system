package com.ipn.mx.fst.service;

import org.springframework.http.HttpStatus;

/** Error de negocio con el código y el mensaje que ya devuelve el backend de Flask. */
public class ReglaDeNegocioException extends RuntimeException {

	private final HttpStatus estado;

	public ReglaDeNegocioException(HttpStatus estado, String mensaje) {
		super(mensaje);
		this.estado = estado;
	}

	public HttpStatus getEstado() { return estado; }
}
