// Carpeta lógica de esta interfaz: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Los dos DTO de los cuerpos que acepta la administración.
import com.ipn.mx.fst.dto.CrearCuentaPeticion;
import com.ipn.mx.fst.dto.ModificarCuentaPeticion;
import java.util.List;
import java.util.Map;

// Administración (sección 3 del checklist, R-07 y R-16). Solo la usa AdminController, que solo deja pasar a un Administrador.
//
// "Solo deja pasar a un Administrador" conviene leerlo bien: la comprobación NO está en esta interfaz ni
// en su implementación, sino en la línea de SeguridadConfig que reserva /admin/** a ROLE_ADMINISTRADOR.
// Por eso ningún método de aquí comprueba el rol de quien llama. Sí reciben idSesion, pero para otra cosa:
// para impedir que un administrador se aplique a sí mismo ciertas acciones.
// Ver la explicación de qué es la capa de servicio en VideoService.
public interface AdminService {

	// Las cuentas con su rol, su estado y cuántos experimentos crearon. rol ("Investigador" o "Administrador") y estado
	// ("activa" o "inactiva") son filtros opcionales; 400 si traen otro valor.
	//
	// Rechazar un valor desconocido con 400, en vez de ignorarlo, evita el peor de los errores: que un
	// filtro mal escrito devuelva en silencio la lista entera y alguien tome una decisión sobre ella.
	List<Map<String, Object>> listar(String rol, String estado);

	// Crea la cuenta como Investigador con una contraseña temporal (cambioRequerido = true), la manda por correo y la devuelve
	// UNA vez en passwordTemporal. Toda alta nace como Investigador: el rol no se pide aquí. 409 si el identificador o el
	// correo ya existen; 400 si el identificador no son solo dígitos (1 a 10).
	//
	// "Toda alta nace como Investigador" es una regla de mínimo privilegio: nadie se crea ya con poderes.
	// Ascender exige un segundo paso explícito, el modificar de abajo.
	// La temporal viaja UNA vez y después solo queda su hash de bcrypt, que no se puede revertir. Si se
	// pierde, hay que generar otra con nuevaTemporal.
	Map<String, Object> crear(CrearCuentaPeticion peticion);

	// Cambia datos, rol o activo de la cuenta. idSesion es el idInstitucional del Administrador que lo pide. 404 si no existe;
	// 409 si el correo es de otra cuenta, si deja al sistema sin Administradores activos (R-16) o si el Administrador se
	// desactiva o se quita el rol a sí mismo («usa Mi perfil»); 400 si intenta cambiar el identificador. Nunca borra.
	//
	// Las dos reglas interesantes son protecciones contra dejar el sistema inservible:
	//   R-16, quedarse sin administradores activos, dejaría un sistema donde ya nadie puede crear cuentas
	//   ni ascender a nadie. No habría forma de salir de ahí sin tocar la base a mano.
	//   Que un administrador se desactive o se degrade a sí mismo es la manera más fácil de provocarlo sin
	//   darse cuenta. De ahí el mensaje «usa Mi perfil», que lo manda a PATCH /users/me.
	// Esta es la operación que necesita el orden de candados de CandadosDeCuenta: toca administrador,
	// usuario y, si cambia el nombre, los reportes de sus experimentos.
	// "Nunca borra" es la regla de siempre: las cuentas se desactivan (ver el campo activo de Usuario).
	Map<String, Object> modificar(String id, ModificarCuentaPeticion peticion, String idSesion);

	// Otra contraseña temporal para la cuenta: mismas reglas que al crearla (una vez, por correo, cambioRequerido = true). La
	// anterior y cualquier enlace de restablecer pendiente dejan de servir. idSesion es el idInstitucional del Administrador que
	// la pide. Se revisa en este orden: 404 si la cuenta no existe, 409 si es la propia cuenta del Administrador (para eso está
	// PATCH /auth/password) y 409 si la cuenta está desactivada. Deja una línea de auditoría en el registro, sin la contraseña.
	//
	// "Los enlaces pendientes dejan de servir" sale solo de cómo está hecho el token de restablecer: lleva
	// una huella del hash de la contraseña, y al cambiarla deja de cuadrar (ver AuthService.restablecer).
	// La prohibición sobre la propia cuenta cierra un atajo: por aquí no se exige la contraseña actual,
	// y PATCH /auth/password sí. Sin esta regla, un administrador con una sesión olvidada abierta podría
	// cambiarse la contraseña sin conocer la anterior.
	// "Se revisa en este orden" está escrito porque el orden es observable desde fuera: con una cuenta
	// inexistente sale 404 aunque además fuera la propia. Las pruebas lo comprueban.
	// La auditoría sin la contraseña es la regla de siempre: los secretos no se escriben en el log.
	Map<String, Object> nuevaTemporal(String id, String idSesion);

	// Disco, conductas y cola de análisis, en orden de llegada (el que se procesa primero).
	// Es la pantalla de estado del sistema. El orden de la cola es el mismo que sigue el worker: el
	// análisis 'en cola' más antiguo primero (ver la entidad Analisis).
	Map<String, Object> sistema();

}
