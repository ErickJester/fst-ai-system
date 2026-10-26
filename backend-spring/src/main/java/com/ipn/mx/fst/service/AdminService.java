package com.ipn.mx.fst.service;

import com.ipn.mx.fst.dto.CrearCuentaPeticion;
import com.ipn.mx.fst.dto.ModificarCuentaPeticion;
import java.util.List;
import java.util.Map;

// Administración (sección 3 del checklist, R-07 y R-16). Solo la usa AdminController, que solo deja pasar a un Administrador.
public interface AdminService {

	// Las cuentas con su rol, su estado y cuántos experimentos crearon. rol ("Investigador" o "Administrador") y estado
	// ("activa" o "inactiva") son filtros opcionales; 400 si traen otro valor.
	List<Map<String, Object>> listar(String rol, String estado);

	// Crea la cuenta con una contraseña temporal (cambioRequerido = true), la manda por correo y la devuelve UNA vez en
	// passwordTemporal. 409 si el identificador o el correo ya existen.
	Map<String, Object> crear(CrearCuentaPeticion peticion);

	// Cambia datos, rol o activo de la cuenta. 404 si no existe, 409 si el correo es de otra cuenta o si deja al sistema sin
	// Administradores activos (R-16), 400 si intenta cambiar el identificador. Nunca borra.
	Map<String, Object> modificar(String id, ModificarCuentaPeticion peticion);

	// Otra contraseña temporal para la cuenta: mismas reglas que al crearla (una vez, por correo, cambioRequerido = true). La
	// anterior y cualquier enlace de restablecer pendiente dejan de servir. 404 si no existe, 409 si la cuenta está desactivada.
	Map<String, Object> nuevaTemporal(String id);

	// Disco, conductas y cola de análisis, en orden de llegada (el que se procesa primero).
	Map<String, Object> sistema();

}
