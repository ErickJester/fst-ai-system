// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// La entidad que maneja este repositorio.
import com.ipn.mx.fst.domain.Notificacion;
// JpaRepository: la interfaz de Spring Data que trae hechos los métodos de acceso a una tabla.
import org.springframework.data.jpa.repository.JpaRepository;

// Ver la explicación general de los repositorios en AdministradorRepository.
// Integer, y no String, porque la llave de notificacion es un número (IDENTITY).
public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

	// Igual que con los reportes: las notificaciones de un experimento se borran antes que él.
	//
	// El nombre del método navega la relación: deleteBy + Experimento (el campo de la entidad) +
	// IdExperimento (el campo DENTRO de ese objeto). Equivale a
	// "DELETE FROM notificacion WHERE idexperimento = ?".
	// Hace falta porque la llave foránea está declarada ON DELETE NO ACTION: PostgreSQL se niega a borrar
	// un experimento del que todavía cuelgan filas. El servicio limpia primero las notificaciones y los
	// reportes, y solo entonces borra el experimento.
	// La alternativa habría sido declarar la llave en cascada, pero eso borraría en silencio: prefirieron
	// que el borrado sea explícito y se vea en el código del servicio.
	void deleteByExperimentoIdExperimento(Integer idExperimento);

}
