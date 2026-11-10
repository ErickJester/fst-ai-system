// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// Los cuatro DTO que dan forma a los cuerpos que acepta este controller.
import com.ipn.mx.fst.dto.CambioContrasenaPeticion;
import com.ipn.mx.fst.dto.LoginPeticion;
import com.ipn.mx.fst.dto.RecuperarPeticion;
import com.ipn.mx.fst.dto.RestablecerPeticion;
// El servicio con las reglas de autenticación (el controller no las lleva).
import com.ipn.mx.fst.service.AuthService;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
// @Valid: dispara las validaciones declaradas en el DTO.
import jakarta.validation.Valid;
import java.util.Map;
// @Autowired: Spring inyecta el servicio ya construido.
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// Authentication: el objeto con el usuario autenticado de la petición, que arma ConvertidorJwt.
import org.springframework.security.core.Authentication;
// Las anotaciones de ruta: PATCH, POST, el cuerpo de la petición y el prefijo común.
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Inicio y cierre de sesión (sección 11 de la guía). Las dos rutas usan el envoltorio {data, error, status};
// los errores (401, 400) los arma ManejadorErrores, igual que en el resto de la API.
//
// Fíjate en lo delgado que es este controller: cada método es una línea que llama al servicio. Eso no es
// casualidad, es la regla del proyecto. El controller traduce entre HTTP y Java (saca el cuerpo, saca el
// usuario del token, elige el código de respuesta) y nada más; las reglas viven en AuthServiceImpl.
// @RequestMapping("/auth") pone ese prefijo a todas las rutas de la clase, así que @PostMapping("/login")
// responde en /auth/login.
// Tres de estas cinco rutas son públicas (login, recover y reset en SeguridadConfig), porque quien las usa
// todavía no tiene sesión. Las otras dos sí exigen token.
@RestController
@RequestMapping("/auth")
public class AuthController {

	@Autowired
	private AuthService authService;

	// POST /auth/login. @Valid comprueba el DTO antes de entrar al método: si falla, Spring lanza la
	// excepción que atrapa ManejadorErrores y responde 400 sin que aquí haya que hacer nada.
	// @RequestBody convierte el JSON del cuerpo en el objeto LoginPeticion.
	// Lo que devuelve el servicio (el token y los datos del usuario) se envuelve y sale con 200.
	@PostMapping("/login")
	public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginPeticion peticion) {
		return Respuesta.ok(authService.login(peticion.getCorreo(), peticion.getContrasena()), HttpStatus.OK);
	}

	// 200 siempre que el cuerpo sea válido, exista o no la cuenta (R-01): no se revela qué correos están registrados.
	//
	// Es una regla de seguridad con nombre: evitar la enumeración de usuarios. Si respondiera 404 cuando
	// el correo no existe, cualquiera podría ir probando direcciones para averiguar quién tiene cuenta.
	// Por eso se devuelve null como data: no hay nada que contar, y contar algo sería justamente el fallo.
	@PostMapping("/recover")
	public ResponseEntity<Map<String, Object>> recuperar(@Valid @RequestBody RecuperarPeticion peticion) {
		authService.recuperar(peticion.getCorreo());
		return Respuesta.ok(null, HttpStatus.OK);
	}

	// Sin sesión: el enlace del correo es la credencial. 400 si ya no sirve, 422 si la contraseña no cumple las reglas.
	//
	// Los dos códigos distinguen dos cosas distintas: 400 es "el token no vale" (venció o ya se usó) y 422
	// es "el token vale, pero la contraseña que mandas no cumple las reglas". Los lanza el servicio como
	// ErrorApi; aquí no se ven porque atraviesan el método hasta ManejadorErrores.
	@PostMapping("/reset")
	public ResponseEntity<Map<String, Object>> restablecer(@Valid @RequestBody RestablecerPeticion peticion) {
		authService.restablecer(peticion.getToken(), peticion.getContrasenaNueva());
		return Respuesta.ok(null, HttpStatus.OK);
	}

	// Cambia la contraseña de quien tiene la sesión abierta (el usuario sale del token). Es lo único, junto
	// con cerrar sesión, que se permite mientras la cuenta tiene una contraseña temporal. No devuelve token:
	// el mismo sigue sirviendo. Con contraseña temporal, aceptaTerminos debe venir en true (400 si no); en un
	// cambio normal el campo se ignora.
	//
	// El parámetro Authentication lo inyecta Spring Security solo, sin anotación: basta con declararlo.
	// authentication.getName() devuelve el idInstitucional gracias al tercer argumento que ConvertidorJwt
	// pasa al construir el JwtAuthenticationToken.
	// Que el usuario salga del token y no del cuerpo es la misma regla de seguridad de Experimento: si
	// viniera en el cuerpo, cualquiera podría cambiarle la contraseña a otro.
	// "El mismo token sigue sirviendo" funciona porque la marca CAMBIO_PENDIENTE se lee de la base en cada
	// petición (ver ConvertidorJwt), no del token: al cambiar la contraseña, el bloqueo se levanta solo.
	@PatchMapping("/password")
	public ResponseEntity<Map<String, Object>> cambiarContrasena(
			@Valid @RequestBody CambioContrasenaPeticion peticion, Authentication authentication) {
		return Respuesta.ok(authService.cambiarContrasena(authentication.getName(),
				peticion.getContrasenaActual(), peticion.getContrasenaNueva(), peticion.getAceptaTerminos()), HttpStatus.OK);
	}

	// No hay lista de bloqueo de tokens ni sesión en el servidor: el cliente descarta su token y deja de
	// mandarlo (guía 11.1). Por eso aquí no hay nada que invalidar y solo se confirma con 200.
	//
	// Es la consecuencia directa de SessionCreationPolicy.STATELESS: el servidor no guarda nada de la
	// sesión, así que no tiene nada que borrar. Un token robado sigue valiendo hasta que vence (7 días);
	// el remedio para ese caso no es el logout, sino dar de baja la cuenta, que ConvertidorJwt comprueba
	// en cada petición.
	// El endpoint existe igualmente para que el frontend tenga a quién avisar y, sobre todo, porque
	// FiltroCambioPendiente lo deja pasar con contraseña temporal: sin él, quien no quiera cambiarla no
	// tendría forma de salir.
	@PostMapping("/logout")
	public ResponseEntity<Map<String, Object>> logout() {
		return Respuesta.ok(null, HttpStatus.OK);
	}

}
