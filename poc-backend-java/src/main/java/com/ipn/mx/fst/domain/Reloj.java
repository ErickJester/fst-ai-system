package com.ipn.mx.fst.domain;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** El backend de Flask guarda datetime.utcnow(), que es UTC con microsegundos.
 *  LocalDateTime.now() daría hora local con nanosegundos: dos diferencias que
 *  el frontend vería en cada fecha. Aquí se igualan las dos. */
public final class Reloj {

	private Reloj() {}

	public static LocalDateTime ahora() {
		return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
	}
}
