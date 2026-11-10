// Mismo paquete que las clases que prueba, pero dentro de src/test.
package com.ipn.mx.fst.service;

// Los métodos de comprobación de JUnit: iguales, falso, distintos y verdadero.
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
// @Tag: una etiqueta para poder ejecutar solo un subconjunto de pruebas.
import org.junit.jupiter.api.Tag;

// R-16 y la contraseña temporal, sin base de datos ni Docker. Las pruebas con la base (dos peticiones al mismo tiempo, 409 por HTTP)
// están en AdministracionYRecuperacionTest.
//
// El reparto está bien pensado: aquí la DECISIÓN pura (los cuatro casos de la regla), y en la otra prueba
// el comportamiento completo con base de datos y concurrencia. Separarlas permite probar los cuatro casos
// en milisegundos, y el día que uno falle se sabe de inmediato si el problema es la regla o su entorno.
// Es también lo que justifica que dejaSinAdministrador sea un método aparte y puro (ver ReglaAdministradores).
class ReglaAdministradoresTest {

	// @Tag("trabajo-terminal") marca las pruebas que se citan en el documento del trabajo terminal: así se
	// pueden ejecutar solas para mostrarlas en la defensa.
	@Test
	@Tag("trabajo-terminal")
	void quitar_al_unico_administrador_activo_deja_el_sistema_sin_administrador() {
		// Los tres argumentos: era administrador activo, NO va a seguir siéndolo, y no queda ningún otro.
		// Es el único caso en que la regla debe saltar.
		assertTrue(ReglaAdministradores.dejaSinAdministrador(true, false, 0));
	}

	@Test
	@Tag("trabajo-terminal")
	void con_otro_administrador_activo_si_se_puede() {
		// El mismo cambio, pero quedando otros: se permite. Se prueba con 1 (el límite) y con 5 (un caso
		// cualquiera), que es la costumbre: el valor frontera y uno normal.
		assertFalse(ReglaAdministradores.dejaSinAdministrador(true, false, 1));
		assertFalse(ReglaAdministradores.dejaSinAdministrador(true, false, 5));
	}

	@Test
	void si_la_cuenta_sigue_siendo_administrador_activo_no_hay_problema() {
		// Cambiarle el nombre al único administrador no lo quita: el segundo argumento sigue en true.
		assertFalse(ReglaAdministradores.dejaSinAdministrador(true, true, 0));
	}

	@Test
	void una_cuenta_que_no_era_administrador_activo_no_cuenta() {
		// Si no era administrador activo, nada de lo que se le haga puede dejar al sistema sin ninguno.
		// Con estos cuatro métodos quedan cubiertas las ocho combinaciones posibles de los tres argumentos.
		assertFalse(ReglaAdministradores.dejaSinAdministrador(false, false, 0));
		assertFalse(ReglaAdministradores.dejaSinAdministrador(false, true, 0));
	}

	@Test
	void la_contrasena_temporal_tiene_12_caracteres_sin_los_que_se_confunden_y_no_se_repite() {
		// Doscientas contraseñas seguidas: lo justo para detectar un generador que repita o que se salga
		// del alfabeto, sin que la prueba tarde.
		Set<String> vistas = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			String temporal = AdminServiceImpl.generarTemporal();
			assertEquals(12, temporal.length());
			// La expresión regular comprueba el alfabeto sin caracteres ambiguos de AdminServiceImpl:
			// A-HJ-N salta la I, P-Z salta la O, a-k m-n p-z saltan la l y la o, y 2-9 salta el 0 y el 1.
			// El segundo argumento de assertTrue es el mensaje que se imprime si falla: aquí la propia
			// contraseña, para poder ver qué salió mal.
			assertTrue(temporal.matches("[A-HJ-NP-Za-km-np-z2-9]{12}"), temporal);
			vistas.add(temporal);
		}
		// Un Set descarta los repetidos: si el tamaño sigue siendo 200, las doscientas eran distintas.
		// Con 54 caracteres elevado a 12 la probabilidad de una repetición es despreciable, así que una
		// colisión aquí significaría que el generador está roto (por ejemplo, con semilla fija).
		assertEquals(200, vistas.size());
		// Y una comprobación directa de que dos llamadas seguidas no dan lo mismo.
		assertNotEquals(AdminServiceImpl.generarTemporal(), AdminServiceImpl.generarTemporal());
	}

}
