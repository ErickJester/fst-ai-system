package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Experimento;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;

// Lo que el controlador puede pedirle a la capa de servicio sobre experimentos. Las reglas de negocio viven
// aquí: cuando una rechaza la petición, el servicio lanza ErrorApi con el código HTTP que corresponde.
public interface ExperimentoService {

	// Una página (de 20) de la lista, de la más reciente a la más antigua: {pagina, total, items}. 400 si
	// pagina no es ≥ 1. Cada item trae lo que la pantalla de la lista necesita (guía 6.1), calculado en pocas
	// consultas para toda la página.
	Map<String, Object> listar(int pagina);

	// El detalle de un experimento con su árbol de grupos, tandas y videos. 404 si no existe.
	Map<String, Object> detalle(Integer id);

	// Los grupos del experimento cuya "etiqueta · tratamiento" contiene q (sin distinguir mayúsculas ni
	// acentos; todos si q viene vacío o es null), cada uno con el ordinal de su siguiente tanda. 404 si el
	// experimento no existe.
	List<Map<String, Object>> buscarGrupos(Integer id, String q);

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
