// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// ErrorApi: la excepción con el código HTTP dentro.
import com.ipn.mx.fst.utilerias.ErrorApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
// JdbcTemplate: SQL directo. Hace falta porque FOR UPDATE es de PostgreSQL y JPA no lo expone.
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
// Propagation: cómo se comporta un método respecto a la transacción de quien lo llama.
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// R-16: siempre debe quedar al menos una cuenta de Administrador activa. Se rechaza (409) desactivar la última o quitarle el rol.
// Va aparte del resto de la administración para poder probarla sola.
//
// Dos peticiones al mismo tiempo (dos Administradores que se desactivan entre sí): las dos leerían «hay otro activo» y las dos
// cambiarían, y no quedaría ninguno. Por eso toda comprobación empieza bloqueando TODAS las filas de ADMINISTRADOR en orden
// de id (FOR UPDATE ... ORDER BY): la segunda espera a que la primera confirme y entonces cuenta de nuevo.
//
// El problema que describe tiene nombre: una condición de carrera. Lo esencial es que comprobar y actuar
// no son un solo paso, y entre los dos cabe otra transacción. El remedio es el candado: mientras una
// comprueba, la otra no puede ni leer.
// Por qué importa tanto: un sistema sin administradores activos es un sistema muerto. Nadie puede crear
// cuentas, ascender a nadie ni reactivar a los desactivados. Solo se sale tocando la base a mano.
// Es la misma idea que AdminInicial resuelve al arrancar, aplicada a no perder el acceso después.
@Component
public class ReglaAdministradores {

	// El texto está en una constante y no suelto porque las pruebas lo comprueban: así, cambiar el mensaje
	// no rompe la prueba ni deja dos versiones distintas del mismo texto.
	// Sin "private" a propósito: lo ve el resto del paquete, incluidas las pruebas.
	static final String MENSAJE = "Debe existir al menos un Administrador activo.";

	@Autowired
	private JdbcTemplate jdbc;

	// ¿El cambio deja al sistema sin Administradores activos? Pura, sin base de datos: la cuenta era Administrador activo, deja
	// de serlo y no hay otro.
	//
	// "Pura" quiere decir que solo depende de sus parámetros: con los mismos valores siempre da lo mismo,
	// y no toca nada de fuera. Separar la decisión del acceso a la base permite probar los ocho casos
	// posibles con ocho líneas, sin levantar PostgreSQL (lo hace ReglaAdministradoresTest).
	// La condición se lee tal cual: era administrador activo Y deja de serlo Y no queda ningún otro.
	// Si cualquiera de las tres falla, el cambio es inofensivo.
	static boolean dejaSinAdministrador(boolean eraAdministradorActivo, boolean seguiraSiendoAdministradorActivo,
			long otrosAdministradoresActivos) {
		return eraAdministradorActivo && !seguiraSiendoAdministradorActivo && otrosAdministradoresActivos == 0;
	}

	// Primer paso de toda transacción que pueda quitar un Administrador (ver el orden en CandadosDeCuenta). Repetirlo no cuesta.
	//
	// Propagation.MANDATORY significa "exijo que ya exista una transacción abierta; si no la hay, lanza
	// una excepción". Es una red de protección: un candado fuera de transacción se soltaría de inmediato
	// y no protegería nada. Así, usar mal este método falla al instante en vez de fallar en silencio.
	// "Repetirlo no cuesta" porque, dentro de la misma transacción, volver a pedir un candado que ya se
	// tiene no hace nada.
	@Transactional(propagation = Propagation.MANDATORY)
	public void bloquearAdministradores() {
		// Otro SELECT cuyo resultado se tira: lo que importa es el efecto secundario de tomar el candado.
		// ORDER BY idinstitucional es imprescindible, no cosmético: si dos transacciones bloquearan las
		// mismas filas en orden distinto podrían quedarse esperándose. Con un orden fijo, eso no ocurre.
		// Es el primer nivel del orden de candados que documenta CandadosDeCuenta.
		jdbc.queryForList("SELECT idinstitucional FROM administrador ORDER BY idinstitucional FOR UPDATE", String.class);
	}

	// 409 si el cambio de la cuenta deja al sistema sin Administradores activos. Debe llamarse DENTRO de la transacción que hace
	// el cambio (MANDATORY), con los datos de la cuenta tal como están antes de cambiarlos.
	//
	// "Antes de cambiarlos" importa: la comprobación compara el estado actual con el futuro, así que si se
	// llamara después de escribir, los dos serían el mismo y nunca detectaría nada.
	@Transactional(propagation = Propagation.MANDATORY)
	public void exigirAlMenosUno(String idInstitucional, boolean eraAdministradorActivo,
			boolean seguiraSiendoAdministradorActivo) {
		// Salida rápida: si la cuenta no era administrador activo, o va a seguir siéndolo, el cambio no
		// puede dejar al sistema sin ninguno. Se evita así el candado, que es caro y bloquea a los demás.
		if (!eraAdministradorActivo || seguiraSiendoAdministradorActivo) {
			return;
		}
		// Solo en el caso peligroso se toma el candado, y SIEMPRE antes de contar.
		bloquearAdministradores();
		// Se cuentan los OTROS administradores activos. Las dos condiciones son necesarias: el JOIN con
		// usuario para mirar si están activos (tener fila en administrador no basta, puede estar dado de
		// baja), y el <> ? para no contarse a sí mismo, que es justo el que va a dejar de serlo.
		Long otros = jdbc.queryForObject(
				"SELECT COUNT(*) FROM administrador a JOIN usuario u ON u.idinstitucional = a.idinstitucional "
						+ "WHERE u.activo AND a.idinstitucional <> ?", Long.class, idInstitucional);
		// La decisión se delega en el método puro de arriba. El "otros == null ? 0 : otros" es prudencia:
		// un COUNT(*) nunca devuelve null, pero el tipo lo permite y así no hay forma de tropezar.
		if (dejaSinAdministrador(eraAdministradorActivo, seguiraSiendoAdministradorActivo, otros == null ? 0 : otros)) {
			// 409 (CONFLICT): la petición se entiende, pero choca con el estado del sistema.
			// Al lanzarse dentro de la transacción, esta se deshace entera y no queda nada a medias.
			throw new ErrorApi(HttpStatus.CONFLICT, MENSAJE);
		}
	}

}
