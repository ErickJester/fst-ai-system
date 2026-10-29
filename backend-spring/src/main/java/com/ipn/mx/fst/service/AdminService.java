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

	// Crea la cuenta como Investigador con una contraseña temporal (cambioRequerido = true), la manda por correo y la devuelve
	// UNA vez en passwordTemporal. Toda alta nace como Investigador: el rol no se pide aquí. 409 si el identificador o el
	// correo ya existen; 400 si el identificador no son solo dígitos (1 a 10).
	Map<String, Object> crear(CrearCuentaPeticion peticion);

	// Cambia datos, rol o activo de la cuenta. idSesion es el idInstitucional del Administrador que lo pide. 404 si no existe;
	// 409 si el correo es de otra cuenta, si deja al sistema sin Administradores activos (R-16) o si el Administrador se
	// desactiva o se quita el rol a sí mismo («usa Mi perfil»); 400 si intenta cambiar el identificador. Nunca borra.
	Map<String, Object> modificar(String id, ModificarCuentaPeticion peticion, String idSesion);

	// Otra contraseña temporal para la cuenta: mismas reglas que al crearla (una vez, por correo, cambioRequerido = true). La
	// anterior y cualquier enlace de restablecer pendiente dejan de servir. idSesion es el idInstitucional del Administrador que
	// la pide. Se revisa en este orden: 404 si la cuenta no existe, 409 si es la propia cuenta del Administrador (para eso está
	// PATCH /auth/password) y 409 si la cuenta está desactivada. Deja una línea de auditoría en el registro, sin la contraseña.
	Map<String, Object> nuevaTemporal(String id, String idSesion);

	// Disco, conductas y cola de análisis, en orden de llegada (el que se procesa primero).
	Map<String, Object> sistema();

}
