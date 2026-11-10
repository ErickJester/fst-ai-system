// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// La entidad que maneja este repositorio.
import com.ipn.mx.fst.domain.Administrador;
// JpaRepository: la interfaz de Spring Data que trae hechos los métodos de acceso a una tabla.
import org.springframework.data.jpa.repository.JpaRepository;

// La llave de administrador es la misma de usuario (identificador institucional). existsById(id) responde si
// un usuario es Administrador.
//
// Qué es un repositorio: la capa que habla con la base. Fíjate en que es una INTERFAZ VACÍA y que nadie
// escribe su implementación: Spring Data la genera al arrancar, leyendo los tipos de JpaRepository.
// JpaRepository<Administrador, String> significa "tabla de Administrador, cuya llave primaria es un String".
// Solo con eso aparecen ya hechos save, findById, findAll, existsById, delete, count y una treintena más.
// Es la diferencia más visible con escribir SQL a mano: el caso corriente no cuesta ni una línea.
//
// Este es el más pequeño del proyecto porque no necesita nada propio: el único uso real es el existsById
// de ConvertidorJwt, que decide en cada petición si quien llama es administrador. Y ese método ya viene
// incluido, de ahí que el cuerpo esté vacío.
public interface AdministradorRepository extends JpaRepository<Administrador, String> {
}
