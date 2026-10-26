package com.ipn.mx.fst.service;

import com.ipn.mx.fst.dto.PerfilPeticion;
import java.util.Map;

// Autenticación: lo que el controlador puede pedirle a la capa de servicio sobre el inicio de sesión.
public interface AuthService {

	// Valida correo y contraseña y devuelve el token de sesión junto con los datos del usuario (nunca el hash).
	// 401 con el mismo mensaje si el correo no existe, la cuenta está inactiva o la contraseña no coincide.
	Map<String, Object> login(String correo, String contrasena);

	// Cambia la contraseña de la cuenta id y quita cambioRequerido. 400 si la actual no coincide, si la nueva
	// es corta o es igual a la actual. Devuelve los datos de la cuenta, sin token.
	Map<String, Object> cambiarContrasena(String id, String actual, String nueva);

	// Cambia nombre, apellidos y correo de la cuenta id. 409 si el correo es de otra cuenta. Devuelve los
	// datos de la cuenta, sin token.
	Map<String, Object> actualizarPerfil(String id, PerfilPeticion peticion);

	// Manda por correo un enlace para restablecer la contraseña, si el correo es de una cuenta activa. No dice si lo encontró: el
	// controller responde lo mismo en los dos casos (R-01).
	void recuperar(String correo);

	// Cambia la contraseña con el enlace del correo. 400 si el enlace venció, no es válido o ya se usó (la contraseña cambió desde
	// que se emitió); 422 si la contraseña nueva no cumple las reglas. Deja cambioRequerido en false.
	void restablecer(String token, String nueva);

}
