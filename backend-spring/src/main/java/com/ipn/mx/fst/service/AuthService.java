// Carpeta lógica de esta interfaz: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// El DTO con los tres campos del perfil propio.
import com.ipn.mx.fst.dto.PerfilPeticion;
import java.util.Map;

// Autenticación: lo que el controlador puede pedirle a la capa de servicio sobre el inicio de sesión.
//
// Cubre los cinco momentos en que alguien toca sus credenciales: entrar, cambiar la contraseña, cambiar
// sus datos, pedir un enlace de recuperación y usarlo. Lo usan AuthController y UsuarioController.
// Fíjate en un patrón que se repite en los cinco comentarios: ninguno distingue entre causas de fallo
// cuando hacerlo daría información a quien tantea. Es la regla de no permitir enumerar usuarios.
// Ver la explicación de qué es la capa de servicio en VideoService.
public interface AuthService {

	// Valida correo y contraseña y devuelve el token de sesión junto con los datos del usuario (nunca el hash).
	// 401 con el mismo mensaje si el correo no existe, la cuenta está inactiva o la contraseña no coincide.
	//
	// "El mismo mensaje" para los tres casos es deliberado: un mensaje distinto para "ese correo no existe"
	// permitiría ir probando direcciones hasta dar con las registradas.
	// "Nunca el hash" lo garantiza además la entidad Usuario, cuyo campo contrasenaHash lleva @JsonIgnore.
	Map<String, Object> login(String correo, String contrasena);

	// Cambia la contraseña de la cuenta id y quita cambioRequerido. 400 si la actual no coincide, si la nueva
	// es corta o es igual a la actual. Devuelve los datos de la cuenta, sin token.
	//
	// "Quita cambioRequerido" es lo que desbloquea la cuenta: ConvertidorJwt deja de poner la marca
	// CAMBIO_PENDIENTE y FiltroCambioPendiente deja de cortar las peticiones. Por eso no hace falta token
	// nuevo: el que ya tiene el usuario sirve, porque esa condición se lee de la base en cada petición.
	// aceptaTerminos solo se exige cuando la cuenta está en primer acceso (ver CambioContrasenaPeticion).
	Map<String, Object> cambiarContrasena(String id, String actual, String nueva, Boolean aceptaTerminos);

	// Cambia nombre, apellidos y correo de la cuenta id. 409 si el correo es de otra cuenta. Devuelve los
	// datos de la cuenta, sin token.
	//
	// "De OTRA cuenta": guardar el correo que uno ya tiene no es un conflicto. Eso es lo que distingue
	// existsByCorreoIgnoreCaseAndIdInstitucionalNot de la versión sin el "Not" (ver UsuarioRepository).
	// Si cambian el nombre o los apellidos hay que invalidar los reportes guardados, porque el PDF lleva
	// impreso el nombre del creador (ver UsuarioController).
	Map<String, Object> actualizarPerfil(String id, PerfilPeticion peticion);

	// Manda por correo un enlace para restablecer la contraseña, si el correo es de una cuenta activa. No dice si lo encontró: el
	// controller responde lo mismo en los dos casos (R-01).
	//
	// Por eso devuelve void: no hay nada que contar hacia fuera, y devolver algo sería justamente la fuga
	// de información que se quiere evitar.
	void recuperar(String correo);

	// Cambia la contraseña con el enlace del correo. 400 si el enlace venció, no es válido o ya se usó (la contraseña cambió desde
	// que se emitió); 422 si la contraseña nueva no cumple las reglas. Deja cambioRequerido en false.
	//
	// El paréntesis explica un truco elegante: el enlace sirve una sola vez sin necesidad de guardar en
	// ninguna tabla los tokens ya usados. El token lleva dentro una huella del hash de la contraseña
	// vigente al emitirlo; en cuanto la contraseña cambia, esa huella deja de cuadrar y el enlace muere
	// solo. Pedir dos enlaces y usar el primero tampoco funciona, por lo mismo.
	// Los dos códigos separan dos cosas distintas: 400 es "el enlace no vale" y 422 es "el enlace vale,
	// pero la contraseña que mandas no cumple las reglas".
	void restablecer(String token, String nueva);

}
