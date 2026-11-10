// Carpeta lógica de esta clase: com/ipn/mx/fst/controller.
package com.ipn.mx.fst.controller;

// El DTO con los tres campos que un usuario puede cambiar de su propia cuenta.
import com.ipn.mx.fst.dto.PerfilPeticion;
// El servicio: las reglas viven ahí, no aquí.
import com.ipn.mx.fst.service.AuthService;
// El envoltorio {data, error, status}.
import com.ipn.mx.fst.utilerias.Respuesta;
// @Valid: dispara las validaciones declaradas en el DTO.
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// Authentication: el usuario autenticado de la petición, que Spring inyecta solo.
import org.springframework.security.core.Authentication;
// PATCH, el cuerpo de la petición y el prefijo común de las rutas.
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Datos de la cuenta de quien tiene la sesión abierta. El usuario sale del token (authentication.getName() es
// su idInstitucional), nunca de la URL ni del cuerpo: nadie puede editar la cuenta de otro por aquí.
//
// Ese "nunca de la URL" se ve en la ruta: es /users/me, con "me" literal, y no /users/{id}. Al no existir
// un id en la ruta, no hay nada que manipular. Para tocar la cuenta de otro está AdminController, bajo
// /admin/**, que SeguridadConfig reserva a los administradores.
// Ver la explicación de qué es un controller en HealthController.
@RestController
@RequestMapping("/users")
public class UsuarioController {

	// Se reutiliza AuthService en vez de crear un UsuarioService: cambiar el perfil toca el correo, que es
	// con lo que se inicia sesión, así que la regla pertenece al mismo sitio.
	@Autowired
	private AuthService authService;

	// Si cambia el nombre o los apellidos, se invalidan los reportes guardados de todos los experimentos de la cuenta
	// (AuthServiceImpl.actualizarPerfil). PATCH /admin/users/{id} (AdminServiceImpl.modificar) hace lo mismo, con los mismos
	// candados en el mismo orden (CandadosDeCuenta).
	//
	// La invalidación tiene sentido si recuerdas que el PDF lleva impreso el nombre del creador: cambiarlo
	// deja mal los reportes ya generados, y por eso hay que rehacerlos. Es la misma caché que administra
	// ReporteAlmacen (ver la entidad Reporte).
	// "Los mismos candados en el mismo orden" apunta a algo delicado: dos rutas distintas pueden cambiar
	// los mismos datos, y si tomaran los bloqueos en orden distinto podrían quedarse trabadas la una a la
	// otra. Por eso las dos pasan por CandadosDeCuenta, que fija el orden en un solo lugar.
	@PatchMapping("/me")
	public ResponseEntity<Map<String, Object>> actualizarPerfil(@Valid @RequestBody PerfilPeticion peticion,
			Authentication authentication) {
		// authentication.getName() es el idInstitucional, gracias al tercer argumento que ConvertidorJwt
		// pasa al construir el JwtAuthenticationToken.
		return Respuesta.ok(authService.actualizarPerfil(authentication.getName(), peticion), HttpStatus.OK);
	}

}
