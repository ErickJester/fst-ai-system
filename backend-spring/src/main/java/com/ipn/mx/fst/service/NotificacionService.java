// Carpeta lógica de esta interfaz: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

import java.util.List;
import java.util.Map;

// La campanita: las notificaciones de la cuenta de la sesión. Las seis del dominio (guía 11.2) las crea el
// worker; el backend solo las lista y las marca como leídas o no leídas.
//
// Es el servicio más sencillo del proyecto, y el motivo está en esa frase: el backend aquí casi no decide
// nada, porque quien escribe las notificaciones es el worker (ver la entidad Notificacion).
// Fíjate en que los tres métodos reciben idInstitucional como primer parámetro. No es casualidad: es lo
// que garantiza que nadie lea ni marque las notificaciones de otro. El controller lo saca del token, no
// de la URL (ver NotificacionController).
// Ver la explicación de qué es la capa de servicio en VideoService.
public interface NotificacionService {

	// Las más recientes de la cuenta idInstitucional, de la más nueva a la más vieja (con un tope).
	// El tope (cien) existe para que una cuenta con años de avisos no se traiga miles de filas en cada
	// carga de la pantalla. Por eso el conteo de abajo va aparte: si se contara sobre esta lista, una
	// cuenta con 150 sin leer diría 100.
	List<Map<String, Object>> listar(String idInstitucional);

	// Cuántas notificaciones no leídas tiene la cuenta, sin el tope de la lista: {noLeidas: n}. Es lo que muestra la campanita.
	// Devuelve un mapa y no un número suelto para que el JSON salga como {"noLeidas": 3}: así, si mañana
	// hace falta un dato más, se añade sin romper a quien ya lo lee.
	Map<String, Object> contarNoLeidas(String idInstitucional);

	// Marca una notificación de la cuenta como leída o no leída y la devuelve. 404 si no existe o es de otra
	// cuenta (no se revela cuál de las dos).
	//
	// Ese paréntesis es una regla de seguridad, la misma de AuthController.recuperar: responder "existe
	// pero no es tuya" confirmaría que la notificación existe, y con eso se podría ir tanteando qué pasa
	// en las cuentas ajenas. Las dos situaciones dan el mismo 404.
	// Se puede marcar en los dos sentidos (leído y no leído) porque la pantalla deja volver a dejar una
	// notificación como pendiente.
	Map<String, Object> marcar(String idInstitucional, Integer idNotificacion, boolean leido);

}
