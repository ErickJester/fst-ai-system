// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

import org.springframework.beans.factory.annotation.Autowired;
// JdbcTemplate: ejecutar SQL directo, sin pasar por JPA. Se usa aquí porque FOR NO KEY UPDATE es una
// cláusula propia de PostgreSQL que JPA no expone.
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
//
// Este archivo tiene cuatro líneas de código y veinte de comentario, y está bien así: es el más delicado
// del backend. Vale la pena desmenuzar lo de arriba.
//
// Qué es un INTERBLOQUEO (deadlock): dos transacciones que se esperan la una a la otra para siempre. La A
// tiene la fila 1 y quiere la 2; la B tiene la 2 y quiere la 1. Ninguna suelta lo que tiene y ninguna
// avanza. PostgreSQL lo detecta y mata a una de las dos, que es justo "el análisis perdido" del comentario.
// Cómo se evita: fijando un ORDEN. Si todas las transacciones piden los candados en el mismo orden
// (administrador, usuario, experimentos), el círculo no se puede formar. Por eso el orden está escrito
// aquí, en un solo lugar, y no repartido por los servicios. Es también el motivo de la nota de
// UsuarioController sobre "los mismos candados en el mismo orden".
//
// Qué son los modos de bloqueo, de menos a más restrictivo:
//   FOR KEY SHARE     "voy a apuntar a esta fila con una llave foránea; que nadie le cambie la llave".
//                     Es lo que toma PostgreSQL solo al insertar una fila que referencia a otra.
//   FOR NO KEY UPDATE "voy a cambiar esta fila, pero NO su llave primaria".
//   FOR UPDATE        "voy a cambiar esta fila, llave incluida". Choca con todo lo demás.
// La clave del diseño: FOR NO KEY UPDATE y FOR KEY SHARE son compatibles. Editar el nombre de un usuario
// no cambia su idInstitucional, así que el worker puede seguir insertando notificaciones que apuntan a él.
// Con FOR UPDATE se bloquearían entre sí, y ahí aparecería el círculo que describe el comentario.
@Component
public class CandadosDeCuenta {

	@Autowired
	private JdbcTemplate jdbc;

	// Bloquea la fila de la cuenta hasta el final de la transacción. false si no existe. Debe ir ANTES de leer la cuenta con
	// JPA (findById): la lectura que sigue al candado ya ve lo que confirmó quien la tenía bloqueada.
	//
	// Ese orden es obligatorio y fácil de equivocar. Si primero se leyera con JPA y luego se bloqueara, se
	// estaría trabajando con datos que otra transacción pudo cambiar justo en medio: el clásico problema
	// de "leer, decidir y escribir" con información ya caducada.
	// El candado se suelta solo al terminar la transacción (al salir del método @Transactional que lo
	// llamó). No hay que liberarlo a mano, ni se puede.
	public boolean bloquearCuenta(String idInstitucional) {
		// Un SELECT que no busca datos: lo que interesa es el efecto secundario, tomar el candado.
		// Se selecciona la propia llave porque hay que seleccionar algo.
		// queryForList devuelve las filas encontradas; si la lista viene vacía, la cuenta no existe, y el
		// ! lo convierte en el booleano que espera quien llama.
		// El ? es un parámetro, nunca se concatena el valor dentro del SQL: así no hay inyección de SQL.
		return !jdbc.queryForList(
				"SELECT idinstitucional FROM usuario WHERE idinstitucional = ? FOR NO KEY UPDATE",
				String.class, idInstitucional).isEmpty();
	}

}
