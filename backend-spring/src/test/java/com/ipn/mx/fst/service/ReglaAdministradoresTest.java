package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

// R-16 y la contraseña temporal, sin base de datos ni Docker. Las pruebas con la base (dos peticiones al mismo tiempo, 409 por HTTP)
// están en AdministracionYRecuperacionTest.
class ReglaAdministradoresTest {

	@Test
	void quitar_al_unico_administrador_activo_deja_el_sistema_sin_administrador() {
		assertTrue(ReglaAdministradores.dejaSinAdministrador(true, false, 0));
	}

	@Test
	void con_otro_administrador_activo_si_se_puede() {
		assertFalse(ReglaAdministradores.dejaSinAdministrador(true, false, 1));
		assertFalse(ReglaAdministradores.dejaSinAdministrador(true, false, 5));
	}

	@Test
	void si_la_cuenta_sigue_siendo_administrador_activo_no_hay_problema() {
		assertFalse(ReglaAdministradores.dejaSinAdministrador(true, true, 0));
	}

	@Test
	void una_cuenta_que_no_era_administrador_activo_no_cuenta() {
		assertFalse(ReglaAdministradores.dejaSinAdministrador(false, false, 0));
		assertFalse(ReglaAdministradores.dejaSinAdministrador(false, true, 0));
	}

	@Test
	void la_contrasena_temporal_tiene_12_caracteres_sin_los_que_se_confunden_y_no_se_repite() {
		Set<String> vistas = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			String temporal = AdminServiceImpl.generarTemporal();
			assertEquals(12, temporal.length());
			assertTrue(temporal.matches("[A-HJ-NP-Za-km-np-z2-9]{12}"), temporal);
			vistas.add(temporal);
		}
		assertEquals(200, vistas.size());
		assertNotEquals(AdminServiceImpl.generarTemporal(), AdminServiceImpl.generarTemporal());
	}

}
