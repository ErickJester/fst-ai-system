package com.ipn.mx.fst.service;

import java.util.List;
import java.util.Map;

// La campanita: las notificaciones de la cuenta de la sesión. Las seis del dominio (guía 11.2) las crea el
// worker; el backend solo las lista y las marca como leídas o no leídas.
public interface NotificacionService {

	// Las más recientes de la cuenta idInstitucional, de la más nueva a la más vieja (con un tope).
	List<Map<String, Object>> listar(String idInstitucional);

	// Marca una notificación de la cuenta como leída o no leída y la devuelve. 404 si no existe o es de otra
	// cuenta (no se revela cuál de las dos).
	Map<String, Object> marcar(String idInstitucional, Integer idNotificacion, boolean leido);

}
