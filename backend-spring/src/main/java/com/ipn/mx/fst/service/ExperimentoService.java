package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Experimento;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;

// Lo que el controlador puede pedirle a la capa de servicio sobre experimentos. Las reglas de negocio viven
// aquí: cuando una rechaza la petición, el servicio lanza ErrorApi con el código HTTP que corresponde.
public interface ExperimentoService {

	// Una página (de 20) de la lista, de la más reciente a la más antigua: {pagina, total, items, resumen}. 400 si
	// pagina no es ≥ 1. Cada item trae lo que la pantalla de la lista necesita (guía 6.1), calculado en pocas
	// consultas para todos los experimentos.
	// Filtros opcionales (null o en blanco = sin filtro): estado, uno de los que calcula el servicio (el mismo texto que trae
	// cada item); fecha, AAAA (el año) o AAAA-MM-DD (esa fecha); tratamiento, el de algún grupo del experimento, sin distinguir
	// mayúsculas ni acentos; q, un texto que el nombre del experimento o el tratamiento de algún grupo contiene (sin distinguir
	// mayúsculas ni acentos). Un valor que no es de esos formatos da 400. Los filtros se combinan; total son los experimentos que
	// los cumplen y una página fuera de rango devuelve la lista vacía con ese total.
	// resumen es de TODOS los experimentos, con o sin filtros: {total, porEstado: {estado: n}, anios: [más reciente primero],
	// tratamientos: [sin repetir, ordenados], avisos: [{idExperimento, nombre, fechaBorrado}]}; la pantalla saca de ahí los
	// conteos por estado, las opciones de los filtros y los avisos de vencimiento sin pedir la lista completa.
	Map<String, Object> listar(int pagina, String estado, String fecha, String tratamiento, String q);

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

	// 404 si no existe, 403 si quien pide no creó el experimento ni es Administrador (C-03), 409 si el nombre ya lo tiene
	// otro experimento. El creador no cambia.
	Experimento actualizar(Integer id, Experimento datos, Authentication authentication);

	// Elimina el experimento completo (D-87): grupos, tandas, videos, análisis, resultados, reportes y notificaciones.
	// 404 si no existe, 403 si quien pide no es el creador ni Administrador (R-08), 409 si algún análisis está
	// procesando o en pausa (los que están en cola sí se borran).
	void eliminar(Integer id, Authentication authentication);

	// Elimina una tanda con sus videos (Día 1 y Día 2), su análisis y sus resultados, para dejar libre su letra si se subió
	// mal. 404 si la tanda no es de ese experimento; 403 si quien pide no es el creador ni Administrador; 409 si su análisis
	// está procesando o en pausa (en cola sí se borra). Si era la última tanda de su grupo, el grupo también se borra (un grupo nunca queda sin tandas).
	void eliminarTanda(Integer idExperimento, Integer idTanda, Authentication authentication);

	// Reporte de resultados en PDF, generado en memoria.
	ByteArrayInputStream generarReporte(Integer idExperimento);

	// El PDF y si trae resultados: conResultados es false cuando el experimento no tiene análisis completados.
	record ReporteDeResultados(byte[] pdf, boolean conResultados) {
	}

	ReporteDeResultados generarReporteDeResultados(Integer idExperimento);

}
