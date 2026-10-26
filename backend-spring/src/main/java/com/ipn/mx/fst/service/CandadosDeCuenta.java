package com.ipn.mx.fst.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Los candados de las transacciones que cambian una cuenta. Orden FIJO de bloqueo en todo el backend, para que dos
// transacciones nunca se esperen en círculo:
//   1. las filas de ADMINISTRADOR (solo si cambia el rol o se desactiva una cuenta; ver ReglaAdministradores),
//   2. la fila de USUARIO (bloquearCuenta),
//   3. los experimentos de la cuenta, en orden de idexperimento (ReporteAlmacen.invalidarDeLaCuenta).
//
// La cuenta se bloquea con FOR NO KEY UPDATE y NO con FOR UPDATE. El worker inserta NOTIFICACION (que apunta a la cuenta
// por llave foránea) con el experimento ya bloqueado, y esa inserción toma FOR KEY SHARE sobre la fila de USUARIO. FOR NO KEY
// UPDATE no choca con FOR KEY SHARE, así que el worker sigue creando sus notificaciones mientras se edita la cuenta; con
// FOR UPDATE el worker (con el experimento en la mano) esperaría a la cuenta y la transacción de la cuenta esperaría al
// experimento: un interbloqueo y un análisis perdido. Dos cambios de la misma cuenta sí se esperan entre sí (NO KEY UPDATE choca
// con NO KEY UPDATE), que es lo que se busca.
@Component
public class CandadosDeCuenta {

	@Autowired
	private JdbcTemplate jdbc;

	// Bloquea la fila de la cuenta hasta el final de la transacción. false si no existe. Debe ir ANTES de leer la cuenta con
	// JPA (findById): la lectura que sigue al candado ya ve lo que confirmó quien la tenía bloqueada.
	public boolean bloquearCuenta(String idInstitucional) {
		return !jdbc.queryForList(
				"SELECT idinstitucional FROM usuario WHERE idinstitucional = ? FOR NO KEY UPDATE",
				String.class, idInstitucional).isEmpty();
	}

}
