// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// Los dos DTO de los cuerpos que acepta este controller.
import com.ipn.mx.fst.dto.CrearCuentaPeticion;
import com.ipn.mx.fst.dto.ModificarCuentaPeticion;
// El servicio con las reglas de administración.
import com.ipn.mx.fst.service.AdminService;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
// @Operation y @ApiResponse: documentación para la página de Swagger. No cambian el comportamiento.
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
// @Valid: dispara las validaciones declaradas en el DTO.
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// Authentication: el usuario autenticado de la petición.
import org.springframework.security.core.Authentication;
// Las anotaciones de ruta: GET, PATCH, POST, variables de ruta, parámetros y cuerpo.
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Administración (R-07). La revisión de rol está en SeguridadConfig (todo /admin/** pide ROLE_ADMINISTRADOR, que ConvertidorJwt lee
// de la base en cada petición): un Investigador recibe 403 antes de llegar aquí. No hay DELETE: las cuentas se desactivan.
//
// Fíjate en que en toda la clase no hay ni una comprobación de rol. Eso es a propósito: la regla vive en
// un solo sitio, la línea de SeguridadConfig que reserva /admin/** a los administradores. Repartirla por
// cada método sería la forma más fácil de olvidarla en uno.
// El prefijo /admin no es decorativo: es LA condición de seguridad. Una ruta nueva en esta clase nace
// protegida solo por estar aquí, y una ruta de administración puesta fuera de /admin quedaría abierta a
// cualquiera con sesión.
// "No hay DELETE" es la otra decisión de fondo: las cuentas se desactivan (ver el campo activo de
// Usuario), porque borrarlas dejaría experimentos sin responsable.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/admin")
public class AdminController {

	@Autowired
	private AdminService adminService;

	// GET /admin/users?rol=...&estado=...
	// Los dos filtros son opcionales; el servicio decide qué hacer cuando faltan.
	@GetMapping("/users")
	public ResponseEntity<Map<String, Object>> listar(@RequestParam(required = false) String rol,
			@RequestParam(required = false) String estado) {
		return Respuesta.ok(adminService.listar(rol, estado), HttpStatus.OK);
	}

	// 201 con la cuenta y passwordTemporal, que solo se devuelve aquí.
	//
	// "Solo se devuelve aquí" es importante: la contraseña temporal viaja UNA vez, en esta respuesta, y
	// nunca más. En la base solo queda su hash de bcrypt, que no se puede revertir. Si el administrador
	// pierde la pantalla, no hay forma de recuperarla: hay que generar otra con reset-temporal.
	// Las @ApiResponse documentan cada código posible en Swagger, para que quien pruebe la API sepa qué
	// esperar sin leer el código del servicio.
	@Operation(summary = "Crear cuenta de Investigador", description = """
			Alta con una contraseña temporal, que se manda por correo y se devuelve UNA vez en `passwordTemporal`. \
			Toda alta nace como Investigador: no hay campo `rol` (un `rol` en el cuerpo se ignora). El ascenso a \
			Administrador es solo por `PATCH /admin/users/{id}`. `idInstitucional` son solo dígitos, de 1 a 10; los espacios de \
			los lados se quitan antes de validar.""")
	@ApiResponse(responseCode = "201", description = "{idInstitucional, nombre, apellidos, correo, rol, activo, cambioRequerido, passwordTemporal}")
	@ApiResponse(responseCode = "400", description = "Datos inválidos: el identificador no son solo dígitos de 1 a 10")
	@ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese identificador o correo")
	@PostMapping("/users")
	// HttpStatus.CREATED (201) y no OK (200): la respuesta correcta cuando una petición crea un recurso.
	public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody CrearCuentaPeticion peticion) {
		return Respuesta.ok(adminService.crear(peticion), HttpStatus.CREATED);
	}

	// PATCH /admin/users/{id}: cambia los datos, el rol o el estado de una cuenta ajena.
	// El id es String y no Integer porque la llave de usuario es el identificador institucional.
	// Se le pasa también authentication.getName() (quién hace el cambio): el servicio lo necesita para
	// impedir que un administrador se degrade o se desactive a sí mismo, y para dejar constancia de quién
	// tocó la cuenta.
	@PatchMapping("/users/{id}")
	public ResponseEntity<Map<String, Object>> modificar(@PathVariable String id,
			@Valid @RequestBody ModificarCuentaPeticion peticion, Authentication authentication) {
		return Respuesta.ok(adminService.modificar(id, peticion, authentication.getName()), HttpStatus.OK);
	}

	// Ruta oficial (la guía no la trae; Erick la aceptó el 28-oct-2026). Responde {passwordTemporal}, que solo se devuelve aquí.
	// authentication.getName() es el idInstitucional de la sesión: con él el servicio rechaza que el Administrador se la dé a
	// su propia cuenta (para eso está PATCH /auth/password).
	//
	// Es el "olvidé mi contraseña" por la vía del administrador, para cuando alguien ya no tiene acceso ni
	// a su correo. Dejar la cuenta con cambioRequerido = true encadena todo lo demás: ConvertidorJwt pone
	// la marca CAMBIO_PENDIENTE y FiltroCambioPendiente bloquea la sesión salvo para cambiar la contraseña.
	// La prohibición de usarla sobre uno mismo evita que un administrador se salte el tener que escribir
	// su contraseña actual, que es lo que PATCH /auth/password sí exige.
	@Operation(summary = "Restablecer contraseña temporal", description = """
			Le da a una cuenta existente una contraseña temporal nueva (12 caracteres), la manda por correo y la devuelve UNA vez en \
			`passwordTemporal`. La cuenta queda con `cambioRequerido = true`: sus tokens anteriores dan 401 salvo en \
			`PATCH /auth/password` y `POST /auth/logout`, la contraseña vieja y los enlaces de restablecer pendientes dejan de servir. \
			Si el correo falla igual responde 200 con la temporal. Queda una línea de auditoría en el registro del servidor (sin la \
			contraseña).""")
	@ApiResponse(responseCode = "200", description = "{passwordTemporal}")
	@ApiResponse(responseCode = "403", description = "La sesión no es de un Administrador")
	@ApiResponse(responseCode = "404", description = "La cuenta no existe")
	@ApiResponse(responseCode = "409", description = "La cuenta está desactivada, o es la propia cuenta del Administrador (usa Mi perfil)")
	@PostMapping("/users/{id}/reset-temporal")
	public ResponseEntity<Map<String, Object>> nuevaTemporal(@PathVariable String id, Authentication authentication) {
		return Respuesta.ok(adminService.nuevaTemporal(id, authentication.getName()), HttpStatus.OK);
	}

	// GET /admin/system: el estado del sistema (uso de disco y demás) para la pantalla de administración.
	// Sin parámetros: es información global, no de una cuenta.
	@GetMapping("/system")
	public ResponseEntity<Map<String, Object>> sistema() {
		return Respuesta.ok(adminService.sistema(), HttpStatus.OK);
	}

}
