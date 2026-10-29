package com.ipn.mx.fst.service;

import com.ipn.mx.fst.utilerias.ErrorApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// R-16: siempre debe quedar al menos una cuenta de Administrador activa. Se rechaza (409) desactivar la última o quitarle el rol.
// Va aparte del resto de la administración para poder probarla sola.
//
// Dos peticiones al mismo tiempo (dos Administradores que se desactivan entre sí): las dos leerían «hay otro activo» y las dos
// cambiarían, y no quedaría ninguno. Por eso toda comprobación empieza bloqueando TODAS las filas de ADMINISTRADOR en orden
// de id (FOR UPDATE ... ORDER BY): la segunda espera a que la primera confirme y entonces cuenta de nuevo.
@Component
public class ReglaAdministradores {

	static final String MENSAJE = "Debe existir al menos un Administrador activo.";

	@Autowired
	private JdbcTemplate jdbc;

	// ¿El cambio deja al sistema sin Administradores activos? Pura, sin base de datos: la cuenta era Administrador activo, deja
	// de serlo y no hay otro.
	static boolean dejaSinAdministrador(boolean eraAdministradorActivo, boolean seguiraSiendoAdministradorActivo,
			long otrosAdministradoresActivos) {
		return eraAdministradorActivo && !seguiraSiendoAdministradorActivo && otrosAdministradoresActivos == 0;
	}

	// Primer paso de toda transacción que pueda quitar un Administrador (ver el orden en CandadosDeCuenta). Repetirlo no cuesta.
	@Transactional(propagation = Propagation.MANDATORY)
	public void bloquearAdministradores() {
		jdbc.queryForList("SELECT idinstitucional FROM administrador ORDER BY idinstitucional FOR UPDATE", String.class);
	}

	// 409 si el cambio de la cuenta deja al sistema sin Administradores activos. Debe llamarse DENTRO de la transacción que hace
	// el cambio (MANDATORY), con los datos de la cuenta tal como están antes de cambiarlos.
	@Transactional(propagation = Propagation.MANDATORY)
	public void exigirAlMenosUno(String idInstitucional, boolean eraAdministradorActivo,
			boolean seguiraSiendoAdministradorActivo) {
		if (!eraAdministradorActivo || seguiraSiendoAdministradorActivo) {
			return;
		}
		bloquearAdministradores();
		Long otros = jdbc.queryForObject(
				"SELECT COUNT(*) FROM administrador a JOIN usuario u ON u.idinstitucional = a.idinstitucional "
						+ "WHERE u.activo AND a.idinstitucional <> ?", Long.class, idInstitucional);
		if (dejaSinAdministrador(eraAdministradorActivo, seguiraSiendoAdministradorActivo, otros == null ? 0 : otros)) {
			throw new ErrorApi(HttpStatus.CONFLICT, MENSAJE);
		}
	}

}
