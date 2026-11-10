// Carpeta lógica de esta interfaz: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// La entidad, que aquí se usa como objeto de entrada y de salida del CRUD.
import com.ipn.mx.fst.domain.Experimento;
// ByteArrayInputStream: bytes en memoria leídos como si fueran un archivo.
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;

// Lo que el controlador puede pedirle a la capa de servicio sobre experimentos. Las reglas de negocio viven
// aquí: cuando una rechaza la petición, el servicio lanza ErrorApi con el código HTTP que corresponde.
//
// Esa segunda frase es la que explica por qué los controllers del proyecto no tienen try/catch: el
// servicio lanza y ManejadorErrores recoge, atravesando el controller sin que este se entere.
// Es el contrato más grande del sistema, y por eso su implementación (ExperimentoServiceImpl) es el
// archivo más largo, con 1 122 líneas.
// Ver la explicación de qué es la capa de servicio en VideoService.
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
	//
	// Es el método más complejo del backend y conviene quedarse con tres ideas:
	//   1. "En pocas consultas para todos" es lo que evita el problema N+1: tres consultas en total, no
	//      una por experimento (ver ExperimentoRepository, GrupoRepository y TandaRepository).
	//   2. El filtrado y la paginación ocurren en JAVA, no en SQL. ExperimentoRepository explica por qué:
	//      el estado no es una columna (se calcula) y el resumen tiene que contar TODOS.
	//   3. El resumen ignora los filtros a propósito. Si los respetara, al filtrar por un estado las
	//      opciones de los demás filtros desaparecerían y no habría forma de volver atrás.
	// "Una página fuera de rango devuelve la lista vacía con ese total" y no un 404: pedir la página 50 de
	// algo que tiene 3 no es un error, es una página sin nada.
	Map<String, Object> listar(int pagina, String estado, String fecha, String tratamiento, String q);

	// El detalle de un experimento con su árbol de grupos, tandas y videos. 404 si no existe.
	// Es la pantalla del experimento, y se arma con las dos consultas agregadas de GrupoRepository y
	// TandaRepository, no recorriendo relaciones objeto a objeto.
	Map<String, Object> detalle(Integer id);

	// Los grupos del experimento cuya "etiqueta · tratamiento" contiene q (sin distinguir mayúsculas ni
	// acentos; todos si q viene vacío o es null), cada uno con el ordinal de su siguiente tanda. 404 si el
	// experimento no existe.
	//
	// Es el autocompletado de la pantalla de subir video: sirve para reusar un grupo que ya existe en vez
	// de crear uno nuevo por un error de dedo. Ignorar acentos importa de verdad aquí, porque los
	// tratamientos se escriben a mano y "solución salina" y "solucion salina" tienen que encontrarse.
	List<Map<String, Object>> buscarGrupos(Integer id, String q);

	// 404 si no existe.
	// La búsqueda cruda, que usan por dentro los demás métodos para no repetir la comprobación del 404.
	Experimento buscar(Integer id);

	// idCreador sale del token y reemplaza al idInstitucional del cuerpo. 404 si el usuario no existe, 409 si
	// el nombre ya existe.
	//
	// "Reemplaza al del cuerpo" es la regla de seguridad que ya anuncia la entidad Experimento: venga lo
	// que venga en el JSON, el creador es quien tiene la sesión.
	Experimento crear(Experimento experimento, String idCreador);

	// 404 si no existe, 403 si quien pide no creó el experimento ni es Administrador (C-03), 409 si el nombre ya lo tiene
	// otro experimento. El creador no cambia.
	//
	// Aquí recibe Authentication entero y no solo el nombre, porque necesita además saber si es
	// administrador. "Otro experimento" es la diferencia entre existsByNombre y
	// existsByNombreAndIdExperimentoNot (ver ExperimentoRepository): guardar sin cambiar el nombre no
	// puede chocar consigo mismo.
	Experimento actualizar(Integer id, Experimento datos, Authentication authentication);

	// Elimina el experimento completo (D-87): grupos, tandas, videos, análisis, resultados, reportes y notificaciones.
	// 404 si no existe, 403 si quien pide no es el creador ni Administrador (R-08), 409 si algún análisis está
	// procesando o en pausa (los que están en cola sí se borran).
	//
	// La distinción del 409 es fina y tiene sentido: un análisis 'en cola' todavía no se ha tocado y se
	// puede descartar sin más; uno 'procesando' lo tiene el worker en la mano ahora mismo, y borrarle las
	// filas por debajo lo haría fallar a media faena. Lo mismo con uno 'en pausa', que está esperando a
	// una persona.
	// El orden de borrado importa: los reportes y las notificaciones van primero, porque sus llaves
	// foráneas son ON DELETE NO ACTION (ver ReporteRepository y NotificacionRepository).
	void eliminar(Integer id, Authentication authentication);

	// Elimina una tanda con sus videos (Día 1 y Día 2), su análisis y sus resultados, para dejar libre su letra si se subió
	// mal. 404 si la tanda no es de ese experimento; 403 si quien pide no es el creador ni Administrador; 409 si su análisis
	// está procesando o en pausa (en cola sí se borra). Si era la última tanda de su grupo, el grupo también se borra (un grupo nunca queda sin tandas).
	//
	// "Dejar libre su letra" es el caso de uso real: las tandas se numeran 1, 2, 3 (A, B, C) y el
	// siguiente ordinal se calcula como max + 1 (ver GrupoRepository). Al borrar la última, su letra
	// vuelve a quedar disponible.
	// Borrar el grupo si se queda sin tandas evita dejar grupos fantasma que luego aparecerían en el
	// autocompletado y en los filtros de la lista.
	void eliminarTanda(Integer idExperimento, Integer idTanda, Authentication authentication);

	// Reporte de resultados en PDF, generado en memoria.
	// Es la versión antigua, heredada de la práctica de clase. La que usa hoy el controller es la de
	// abajo, que además dice si el PDF trae resultados.
	ByteArrayInputStream generarReporte(Integer idExperimento);

	// El PDF y si trae resultados: conResultados es false cuando el experimento no tiene análisis completados.
	//
	// Un record es una clase de solo datos: Java genera el constructor, los getters (pdf() y
	// conResultados(), sin "get"), equals, hashCode y toString. Declararlo DENTRO de la interfaz lo ata a
	// su contrato: se nombra ExperimentoService.ReporteDeResultados y no anda suelto por el paquete.
	record ReporteDeResultados(byte[] pdf, boolean conResultados) {
	}

	// El segundo campo es lo que necesita ReporteAlmacen para decidir si vale la pena guardar el PDF.
	// ExperimentoController lo explica: un PDF que dice "sin resultados" no debe guardarse, porque nada
	// lo invalidaría cuando por fin terminara un análisis.
	ReporteDeResultados generarReporteDeResultados(Integer idExperimento);

}
