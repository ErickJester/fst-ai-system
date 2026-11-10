// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// La entidad que maneja este repositorio.
import com.ipn.mx.fst.domain.Usuario;
// Optional: una caja que puede traer un valor o venir vacía. Es la forma moderna de decir "puede que no
// haya resultado", en lugar de devolver null y arriesgarse a un NullPointerException.
import java.util.Optional;
// JpaRepository: la interfaz de Spring Data que trae hechos los métodos de acceso a una tabla.
import org.springframework.data.jpa.repository.JpaRepository;

// La llave de usuario es el identificador institucional (String), no un número.
//
// Este repositorio enseña la segunda capacidad de Spring Data, después de los métodos ya hechos: las
// CONSULTAS DERIVADAS DEL NOMBRE. Basta con declarar un método siguiendo unas reglas de nombres y Spring
// escribe el SQL solo, al arrancar. Si el nombre no cuadra con los campos de la entidad, el arranque falla
// con un error claro, así que una errata se descubre al instante y no en producción.
// Ver la explicación general de los repositorios en AdministradorRepository.
public interface UsuarioRepository extends JpaRepository<Usuario, String> {

	// El correo es el nombre de usuario del login; se compara sin importar mayúsculas.
	//
	// El nombre se lee por partes: findBy + Correo (el campo) + IgnoreCase (sin distinguir mayúsculas).
	// Equivale a "SELECT * FROM usuario WHERE lower(correo) = lower(?)".
	// Devuelve Optional porque el correo puede no existir, y quien llama está obligado a contemplarlo.
	// Lo usa AuthServiceImpl al iniciar sesión: el usuario escribe su correo, no su boleta.
	Optional<Usuario> findByCorreoIgnoreCase(String correo);

	// La misma consulta, pero preguntando solo si hay fila. Es más barata: la base no trae los datos,
	// solo comprueba la existencia. Lo usa AdminInicial antes de crear el administrador inicial.
	boolean existsByCorreoIgnoreCase(String correo);

	// ¿Otra cuenta (no la de idInstitucional) usa ya este correo? Para cambiar el propio correo.
	//
	// El nombre encadena dos condiciones con And: Correo igual (sin mayúsculas) Y IdInstitucional
	// distinto (el sufijo "Not"). El segundo es imprescindible: sin él, cambiar el correo por el que uno
	// ya tiene daría "ese correo está ocupado"... por uno mismo.
	boolean existsByCorreoIgnoreCaseAndIdInstitucionalNot(String correo, String idInstitucional);

}
