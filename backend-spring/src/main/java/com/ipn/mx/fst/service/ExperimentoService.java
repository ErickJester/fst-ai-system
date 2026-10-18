package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Experimento;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.springframework.security.core.Authentication;

// Lo que el controlador puede pedirle a la capa de servicio sobre experimentos. Las reglas de negocio viven
// aquí: cuando una rechaza la petición, el servicio lanza ErrorApi con el código HTTP que corresponde.
public interface ExperimentoService {

	List<Experimento> findAll();

	// 404 si no existe.
	Experimento buscar(Integer id);

	// idCreador sale del token y reemplaza al idInstitucional del cuerpo. 404 si el usuario no existe, 409 si
	// el nombre ya existe.
	Experimento crear(Experimento experimento, String idCreador);

	// 404 si no existe, 409 si el nombre ya lo tiene otro experimento. El creador no cambia.
	Experimento actualizar(Integer id, Experimento datos);

	// 404 si no existe, 403 si quien pide no es el creador ni Administrador (R-08), 409 si tiene grupos.
	// Borra también sus reportes y notificaciones.
	void eliminar(Integer id, Authentication authentication);

	// Reporte de resultados en PDF, generado en memoria.
	ByteArrayInputStream generarReporte(Integer idExperimento);

}
