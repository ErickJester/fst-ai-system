// Carpeta lógica de esta interfaz: com/ipn/mx/fst/repository.
package com.ipn.mx.fst.repository;

// La entidad que maneja este repositorio.
import com.ipn.mx.fst.domain.Reporte;
// JpaRepository: la interfaz de Spring Data que trae hechos los métodos de acceso a una tabla.
import org.springframework.data.jpa.repository.JpaRepository;

// Ver la explicación general de los repositorios en AdministradorRepository.
// La llave de reporte es un número (IDENTITY), de ahí el Integer.
public interface ReporteRepository extends JpaRepository<Reporte, Integer> {

	// Borra los reportes guardados de un experimento antes de borrar el experimento: la llave foránea
	// no deja borrarlo si aún tiene reportes (ON DELETE NO ACTION).
	//
	// Es el mismo caso que en NotificacionRepository, y ahí está explicado con más detalle.
	// Ojo con lo que NO hace: borra las FILAS de la tabla reporte, no los archivos del volumen. De los
	// archivos se encarga ReporteAlmacen, que es quien los escribió. Borrar solo las filas dejaría
	// archivos huérfanos ocupando disco.
	void deleteByExperimentoIdExperimento(Integer idExperimento);

}
