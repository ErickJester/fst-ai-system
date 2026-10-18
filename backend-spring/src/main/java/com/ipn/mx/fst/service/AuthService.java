package com.ipn.mx.fst.service;

import java.util.Map;

// Autenticación: lo que el controlador puede pedirle a la capa de servicio sobre el inicio de sesión.
public interface AuthService {

	// Valida correo y contraseña y devuelve el token de sesión junto con los datos del usuario (nunca el hash).
	// 401 con el mismo mensaje si el correo no existe, la cuenta está inactiva o la contraseña no coincide.
	Map<String, Object> login(String correo, String contrasena);

}
