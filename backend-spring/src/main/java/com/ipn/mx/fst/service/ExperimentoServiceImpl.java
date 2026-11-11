// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Las entidades y los enums de dominio que se usan.
import com.ipn.mx.fst.domain.Experimento;
import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.domain.valores.EstadoAnalisis;
import com.ipn.mx.fst.domain.valores.EtapaAnalisis;
import com.ipn.mx.fst.domain.valores.SesionVideo;
import com.ipn.mx.fst.domain.valores.TipoConducta;
// Los repositorios y sus proyecciones (las interfaces Fila*).
import com.ipn.mx.fst.repository.ExperimentoRepository;
import com.ipn.mx.fst.repository.FilaExperimento;
import com.ipn.mx.fst.repository.FilaGrupo;
import com.ipn.mx.fst.repository.FilaResultado;
import com.ipn.mx.fst.repository.FilaTandaVideo;
import com.ipn.mx.fst.repository.GrupoRepository;
import com.ipn.mx.fst.repository.NotificacionRepository;
import com.ipn.mx.fst.repository.SegundoRepository;
import com.ipn.mx.fst.repository.TandaRepository;
import com.ipn.mx.fst.repository.UsuarioRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
// iText 5, la biblioteca que escribe el PDF: colores, saltos, el documento, alineaciones, tipografías,
// tamaño de página, párrafos, frases, celdas, tablas y el escritor.
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
// Las clases de archivos: recorrer una carpeta con filtro, el resultado de un paso del recorrido, las
// utilidades, las rutas, el visitante de carpetas y los atributos de un archivo.
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
// Normalizer: separa las letras de sus acentos, para poder quitarlos (ver sinAcentos).
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
// Las clases del paquete graficas, que dibujan la sección de gráficas del PDF y sus marcadores.
import com.ipn.mx.fst.service.graficas.DatosGraficas;
import com.ipn.mx.fst.service.graficas.EstiloReportePdf;
import com.ipn.mx.fst.service.graficas.GraficaPdf;
import com.ipn.mx.fst.service.graficas.MarcadoresPdf;
import com.ipn.mx.fst.service.graficas.SeccionGraficasPdf;
import java.util.ArrayList;
import java.util.HashMap;
// HashSet: un conjunto sin orden; sirve para contar elementos distintos sin repetir.
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Las dos clases que permiten ejecutar algo DESPUÉS de que la transacción confirme.
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// La implementación de ExperimentoService, donde está el contrato completo de cada método.
//
// Es el archivo más grande del backend (1 122 líneas) y hace tres cosas bastante distintas:
//   1. la LISTA y el DETALLE, armados con consultas agregadas y mapas;
//   2. el CRUD con sus borrados en cascada a mano y su coreografía de candados;
//   3. el PDF de resultados, dibujado con iText.
// Las tres podrían vivir en clases separadas; están juntas por la estructura heredada de la práctica de
// clase, donde un servicio por entidad lo hacía todo.
@Service
public class ExperimentoServiceImpl implements ExperimentoService {

	private static final Logger log = LoggerFactory.getLogger(ExperimentoServiceImpl.class);

	// Las cuatro conductas en el orden de las columnas del reporte (sección 7 de la guía).
	// Un arreglo y no una lista porque se construye a mano con el orden exacto del reporte, que NO es el
	// del enum: aquí escalamiento va antes que inmovilidad.
	private static final String[] CONDUCTAS = {TipoConducta.NADO_ACTIVO.valor(), TipoConducta.ESCALAMIENTO.valor(),
			TipoConducta.INMOVILIDAD.valor(), TipoConducta.CONDUCTA_ACTIVA.valor()};
	// Las cuatro conductas y «Sin ver», en el orden de las columnas del PDF.
	// «Sin ver» va al final para que las cinco columnas sumen los 60 segundos del minuto.
	private static final List<String> COLUMNAS_PDF = Stream.concat(Stream.of(CONDUCTAS), Stream.of(TablaDeReporte.SIN_VER)).toList();

	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
	private static final int POR_PAGINA = 20;
	// Los estados que puede tener un experimento en la lista (los de calcularEstado): los dos de antes de que haya análisis y
	// los seis de un análisis.
	//
	// Un experimento no tiene columna de estado: se calcula. Esta lista es la de valores válidos del
	// filtro, y se arma con los dos propios más los seis del enum, para que no pueda descuadrarse si
	// algún día se añade un estado de análisis.
	private static final List<String> ESTADOS_DE_EXPERIMENTO = Stream.concat(
			Stream.of("sin videos", "en espera del Día 2"), Stream.of(EstadoAnalisis.values()).map(EstadoAnalisis::valor)).toList();
	// R-05: el video se borra 30 días después de un análisis completado; con 7 días o menos la lista avisa.
	private static final int DIAS_RETENCION = 30;
	private static final int DIAS_AVISO = 7;
	// Estados de un análisis de mayor a menor precedencia, para el estado del experimento (guía 4.8).
	//
	// El orden no es casual: lo que necesita atención va primero. Un experimento con cuatro análisis
	// completados y uno en error se muestra como "error", porque eso es lo que hay que mirar. Completado
	// va el último: solo se muestra si TODOS lo están.
	private static final List<String> PRECEDENCIA = List.of(EstadoAnalisis.ERROR.valor(), EstadoAnalisis.CANCELADO.valor(),
			EstadoAnalisis.EN_PAUSA.valor(), EstadoAnalisis.PROCESANDO.valor(), EstadoAnalisis.EN_COLA.valor(),
			EstadoAnalisis.COMPLETADO.valor());

	@Autowired
	private ExperimentoRepository experimentoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private GrupoRepository grupoRepository;

	@Autowired
	private ReporteAlmacen reportes;

	@Autowired
	private NotificacionRepository notificacionRepository;

	@Autowired
	private SegundoRepository segundoRepository;

	@Autowired
	private TandaRepository tandaRepository;

	// Para eliminar y eliminarTanda: consultas con candado (FOR UPDATE) y los borrados de las hojas hacia arriba, explícitos.
	// JPA no expone FOR UPDATE ni permite escribir los DELETE en el orden exacto que hace falta.
	@Autowired
	private JdbcTemplate jdbc;

	// Raíz del volumen videos_reportes: de ahí se borran los videos y la carpeta del análisis de una tanda o un experimento eliminado.
	// La versión del sistema que firma el pie de cada página del PDF (la de version.properties).
	@Value("${app.version:}")
	private String versionApp;

	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	// Los tiempos de cada análisis (tiempos.json) para la sección "Tiempos de análisis" del PDF.
	@Autowired
	private ResultadosService resultadosService;

	// --- Lista y detalle: la forma de la guía 6.1, armada con consultas agregadas (proyecciones Fila*) y
	// mapas, no con la entidad Experimento (sus relaciones LAZY no se pueden serializar). ---
	//
	// El paréntesis es el motivo técnico de todo el paquete de proyecciones: con open-in-view en false
	// (ver application.properties), una relación LAZY no se puede cargar al serializar y daría
	// LazyInitializationException. Ver FilaExperimento.

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> listar(int pagina, String estado, String fecha, String tratamiento, String q) {
		// Las cuatro validaciones de los parámetros, antes de tocar la base.
		if (pagina < 1) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El parámetro 'pagina' no tiene un valor válido");
		}
		String estadoPedido = sinEspacios(estado);
		if (estadoPedido != null && !ESTADOS_DE_EXPERIMENTO.contains(estadoPedido)) {
			// El mensaje enumera los valores válidos: así quien llama sabe qué escribir sin ir al código.
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El parámetro 'estado' no tiene un valor válido; puede ser: "
					+ String.join(", ", ESTADOS_DE_EXPERIMENTO));
		}
		String fechaPedida = sinEspacios(fecha);
		if (fechaPedida != null && !fechaValida(fechaPedida)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El parámetro 'fecha' debe ser AAAA o AAAA-MM-DD");
		}
		String tratamientoPedido = sinEspacios(tratamiento);
		// El texto buscado se normaliza UNA vez aquí, no en cada comparación del filtro de abajo.
		String tratamientoBuscado = tratamientoPedido == null ? null : sinAcentos(tratamientoPedido);
		String textoPedido = sinEspacios(q);
		String textoBuscado = textoPedido == null ? null : sinAcentos(textoPedido);

		// El estado se calcula, no se guarda, y el resumen cuenta TODOS los experimentos: no se puede filtrar y paginar en SQL
		// con LIMIT y OFFSET. Se leen todos y se arman sus items una vez (con una consulta de grupos y otra de tandas para
		// todos); con eso salen el resumen, el filtro y la página. Es un laboratorio: cientos de experimentos, no millones.
		//
		// Es un compromiso consciente y está bien justificado: normalmente filtrar y paginar en memoria
		// sería un error grave, pero aquí el estado no existe como columna y el resumen necesita contarlos
		// todos de cualquier modo. La última frase es la que lo hace defendible: con cientos de filas, no
		// con millones.
		List<Map<String, Object>> todos = armarItems(experimentoRepository.buscarTodos());
		// Los cuatro filtros encadenados. Cada uno deja pasar todo si su parámetro es null, así que se
		// combinan sin casos especiales.
		List<Map<String, Object>> filtrados = todos.stream()
				.filter(item -> fechaPedida == null || coincideFecha((String) item.get("fecha"), fechaPedida))
				.filter(item -> estadoPedido == null || estadoPedido.equals(item.get("estado")))
				.filter(item -> tratamientoBuscado == null || tieneTratamiento(item, tratamientoBuscado))
				.filter(item -> textoBuscado == null || coincideTexto(item, textoBuscado))
				.toList();

		// El recorte de la página. Math.min con el tamaño de la lista evita salirse del rango: pedir la
		// página 50 de algo que tiene 3 devuelve una lista vacía en vez de tronar.
		// El (long) del cálculo evita que un número de página enorme desborde el int al multiplicar.
		int desde = (int) Math.min((long) (pagina - 1) * POR_PAGINA, filtrados.size());
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("pagina", pagina);
		// total son los que cumplen los filtros, no los de la página: la pantalla lo necesita para el paginador.
		datos.put("total", filtrados.size());
		datos.put("items", filtrados.subList(desde, Math.min(filtrados.size(), desde + POR_PAGINA)));
		// El resumen se calcula sobre TODOS, no sobre los filtrados (ver el método).
		datos.put("resumen", resumenDeLista(todos));
		return datos;
	}

	// Lo que la pantalla necesita saber de TODOS los experimentos, con o sin filtros: cuántos hay, cuántos por estado (solo los
	// estados que tienen alguno), los años (el más reciente primero), los tratamientos (sin repetir, ordenados) y los avisos de
	// vencimiento (R-05) de los videos que se borran en 7 días o menos.
	//
	// Que ignore los filtros es deliberado: si los respetara, al filtrar por un estado desaparecerían las
	// opciones de los demás filtros y no habría forma de volver atrás.
	// @SuppressWarnings("unchecked") silencia el aviso del compilador por las conversiones de los mapas:
	// como el item es un Map<String, Object>, Java no puede comprobar que el valor sea una List<String>.
	// Es seguro porque los arma itemDeLista unas líneas más arriba.
	@SuppressWarnings("unchecked")
	private static Map<String, Object> resumenDeLista(List<Map<String, Object>> todos) {
		Map<String, Integer> porEstado = new LinkedHashMap<>();
		// TreeSet con el orden invertido: los años salen solos del más reciente al más antiguo, y sin
		// repetirse, sin tener que ordenar después.
		Set<String> anios = new java.util.TreeSet<>(java.util.Comparator.reverseOrder());
		Map<String, String> tratamientos = new LinkedHashMap<>();   // sin acentos ni mayúsculas → el texto con que se escribió
		List<Map<String, Object>> avisos = new ArrayList<>();
		for (Map<String, Object> item : todos) {
			// merge con Integer::sum es el contador clásico: suma 1 al valor que hubiera, o lo pone en 1.
			porEstado.merge((String) item.get("estado"), 1, Integer::sum);
			// Los cuatro primeros caracteres de "2026-11-10" son el año.
			anios.add(((String) item.get("fecha")).substring(0, 4));
			for (String t : (List<String>) item.get("tratamientos")) {
				// La clave normalizada agrupa "Salina" y "salina"; el valor conserva cómo se escribió la
				// PRIMERA vez (putIfAbsent no pisa), que es lo que se muestra en el menú del filtro.
				tratamientos.putIfAbsent(sinAcentos(t), t);
			}
			Map<String, String> aviso = (Map<String, String>) item.get("avisoVencimiento");
			if (aviso != null) {
				Map<String, Object> a = new LinkedHashMap<>();
				a.put("idExperimento", item.get("idExperimento"));
				a.put("nombre", item.get("nombre"));
				a.put("fechaBorrado", aviso.get("fechaBorrado"));
				avisos.add(a);
			}
		}
		// Collator ordena respetando el alfabeto español: pone la "ñ" después de la "n" y trata bien los
		// acentos, cosa que un sort normal (que ordena por código de carácter) no hace.
		java.text.Collator orden = java.text.Collator.getInstance(Locale.forLanguageTag("es"));
		List<String> listaDeTratamientos = new ArrayList<>(tratamientos.values());
		listaDeTratamientos.sort(orden);

		Map<String, Object> resumen = new LinkedHashMap<>();
		resumen.put("total", todos.size());
		resumen.put("porEstado", porEstado);
		resumen.put("anios", new ArrayList<>(anios));
		resumen.put("tratamientos", listaDeTratamientos);
		resumen.put("avisos", avisos);
		return resumen;
	}

	// Los items de la lista para estas filas, con una consulta de grupos y otra de tandas con videos para TODAS, no una por
	// experimento.
	//
	// Aquí está la defensa contra el problema N+1: DOS consultas en total, no dos por experimento. Con 20
	// experimentos por página, la diferencia es 3 consultas contra 41 (ver GrupoRepository).
	private List<Map<String, Object>> armarItems(List<FilaExperimento> filas) {
		List<Map<String, Object>> items = new ArrayList<>();
		// Sin experimentos no hay nada que consultar, y además un IN () vacío daría error en SQL.
		if (filas.isEmpty()) {
			return items;
		}
		List<Integer> ids = filas.stream().map(FilaExperimento::getIdExperimento).toList();
		// groupingBy convierte la lista plana en un mapa de idExperimento a sus filas: así cada
		// experimento encuentra las suyas sin recorrer la lista entera.
		Map<Integer, List<FilaGrupo>> gruposDe = grupoRepository.buscarGrupos(ids).stream()
				.collect(Collectors.groupingBy(FilaGrupo::getIdExperimento));
		Map<Integer, List<FilaTandaVideo>> tandasDe = tandaRepository.buscarTandasYVideos(ids).stream()
				.collect(Collectors.groupingBy(FilaTandaVideo::getIdExperimento));
		for (FilaExperimento fila : filas) {
			// getOrDefault con List.of(): un experimento sin grupos recibe una lista vacía, no null.
			items.add(itemDeLista(fila, gruposDe.getOrDefault(fila.getIdExperimento(), List.of()),
					tandasDe.getOrDefault(fila.getIdExperimento(), List.of())));
		}
		return items;
	}

	// null si el filtro no viene o está en blanco.
	// Normaliza los dos casos a uno solo, para que los filtros de arriba solo comprueben null.
	private static String sinEspacios(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}

	// AAAA (cuatro dígitos) o AAAA-MM-DD (una fecha que existe).
	// "Que existe" importa: con LocalDate.parse, un 2026-02-30 se rechaza, cosa que una simple
	// comprobación de formato no haría.
	private static boolean fechaValida(String fecha) {
		if (fecha.length() == 4) {
			// allMatch sobre los caracteres: los cuatro tienen que ser dígitos.
			return fecha.chars().allMatch(c -> c >= '0' && c <= '9');
		}
		if (fecha.length() != 10) {
			return false;
		}
		try {
			LocalDate.parse(fecha);
			return true;
		} catch (DateTimeParseException e) {
			return false;
		}
	}

	// fecha es la del experimento en AAAA-MM-DD; el filtro, AAAA o AAAA-MM-DD ya validado.
	// Con solo el año se compara el prefijo "2026-" (con el guion, para que "2026" no case con "20267");
	// con la fecha completa, el texto entero.
	private static boolean coincideFecha(String fecha, String filtro) {
		return filtro.length() == 4 ? fecha.startsWith(filtro + "-") : fecha.equals(filtro);
	}

	// ¿El nombre del experimento o el tratamiento de algún grupo CONTIENE el texto (ya sin acentos ni mayúsculas)?
	// Es la búsqueda libre de la caja de texto: basta con que aparezca en cualquiera de los dos sitios.
	@SuppressWarnings("unchecked")
	private static boolean coincideTexto(Map<String, Object> item, String buscado) {
		return sinAcentos((String) item.get("nombre")).contains(buscado)
				|| ((List<String>) item.get("tratamientos")).stream().anyMatch(t -> sinAcentos(t).contains(buscado));
	}

	// ¿Algún grupo del item tiene exactamente este tratamiento (ya sin acentos ni mayúsculas)?
	// equals y no contains, al revés que arriba: este filtro sale de un menú con valores exactos, no de
	// una caja de texto, así que una coincidencia parcial sería un error.
	@SuppressWarnings("unchecked")
	private static boolean tieneTratamiento(Map<String, Object> item, String buscado) {
		return ((List<String>) item.get("tratamientos")).stream().anyMatch(t -> sinAcentos(t).equals(buscado));
	}

	// Arma un item de la lista a partir de las filas planas de las dos consultas.
	private Map<String, Object> itemDeLista(FilaExperimento fila, List<FilaGrupo> grupos, List<FilaTandaVideo> filas) {
		// Tres conjuntos para contar sin repetir: la consulta trae una fila por VIDEO, así que una tanda
		// con dos videos aparece dos veces.
		Set<Integer> tandas = new HashSet<>();
		Set<Integer> tandasConDia2 = new HashSet<>();
		Set<Integer> tandasAnalizadas = new HashSet<>();
		int especimenes = 0;
		boolean videosBorrados = false;
		Integer diasRetencion = null;
		for (FilaTandaVideo f : filas) {
			// La consulta trae una fila por video; la tanda se cuenta una sola vez.
			// add devuelve true solo la primera vez que se añade: es el truco para contar distintos.
			if (tandas.add(f.getIdTanda())) {
				especimenes += f.getCilindros().intValue();
			}
			// Una tanda sin video llega con todo lo del video en null (el LEFT JOIN).
			if (f.getIdVideo() == null) {
				continue;
			}
			if (!f.getVideoDisponible()) {
				videosBorrados = true;
			}
			boolean dia2 = SesionVideo.DIA_2.es(f.getSesion());
			boolean completado = EstadoAnalisis.COMPLETADO.es(f.getEstadoAnalisis());
			if (dia2) {
				tandasConDia2.add(f.getIdTanda());
				if (completado) {
					tandasAnalizadas.add(f.getIdTanda());
				}
			}
			// R-05: el video se borra 30 días después de un análisis completado. Solo cuentan los videos que
			// todavía existen; el experimento muestra el que está más cerca de borrarse.
			//
			// Math.min va quedándose con el más urgente: el aviso tiene que hablar del primero que se va,
			// no del último. Math.max(0, ...) evita días negativos en un video ya vencido que aún no se
			// ha borrado.
			if (completado && f.getVideoDisponible()) {
				int restantes = Math.max(0, DIAS_RETENCION - f.getDiasDesdeAnalisis().intValue());
				diasRetencion = diasRetencion == null ? restantes : Math.min(diasRetencion, restantes);
			}
		}

		// Los tres números del avance: analizados de cargados, de un total de tandas.
		Map<String, Object> videosDia2 = new LinkedHashMap<>();
		videosDia2.put("analizados", tandasAnalizadas.size());
		videosDia2.put("cargados", tandasConDia2.size());
		videosDia2.put("total", tandas.size());

		// El aviso solo aparece a 7 días o menos. La fecha se calcula en la zona de México para que el
		// día no se corra respecto a lo que ve el usuario.
		Map<String, String> avisoVencimiento = null;
		if (diasRetencion != null && diasRetencion <= DIAS_AVISO) {
			avisoVencimiento = Map.of("fechaBorrado", LocalDate.now(ZONA).plusDays(diasRetencion).toString());
		}

		Map<String, Object> item = new LinkedHashMap<>();
		item.put("idExperimento", fila.getIdExperimento());
		item.put("nombre", fila.getNombre());
		item.put("fecha", fila.getFecha());
		item.put("notas", fila.getNotas());
		item.put("nGrupos", grupos.size());
		item.put("estado", calcularEstado(filas));
		item.put("avisoVencimiento", avisoVencimiento);
		item.put("creador", creador(fila));
		item.put("nEspecimenes", especimenes);
		// distinct quita los tratamientos repetidos (varios grupos pueden compartirlo) y CASE_INSENSITIVE_ORDER
		// los ordena sin distinguir mayúsculas.
		item.put("tratamientos", grupos.stream().map(FilaGrupo::getTratamiento).distinct()
				.sorted(String.CASE_INSENSITIVE_ORDER).toList());
		item.put("videosDia2", videosDia2);
		item.put("diasRetencion", diasRetencion);
		item.put("videosBorrados", videosBorrados);
		return item;
	}

	// El estado del experimento no se guarda: se calcula (guía 4.8). Este es el ÚNICO lugar que lo decide, y
	// lo usan la lista y el detalle. Sin videos: "sin videos". Con videos pero sin ningún análisis (solo Día 1):
	// "en espera del Día 2". Con análisis: el de mayor precedencia entre todos los suyos.
	//
	// "El ÚNICO lugar" es lo que garantiza que la lista y el detalle no se contradigan. Y no guardarlo
	// evita el problema de siempre: un estado guardado se desfasa en cuanto cambia un análisis.
	private String calcularEstado(List<FilaTandaVideo> filas) {
		boolean hayVideos = false;
		Set<String> estados = new HashSet<>();
		for (FilaTandaVideo f : filas) {
			if (f.getIdVideo() == null) {
				continue;
			}
			hayVideos = true;
			if (f.getIdAnalisis() != null) {
				estados.add(f.getEstadoAnalisis());
			}
		}
		if (!hayVideos) {
			return "sin videos";
		}
		// Hay videos pero ninguno analizado: solo se subió el Día 1, que no se analiza (ver SesionVideo).
		if (estados.isEmpty()) {
			return "en espera del Día 2";
		}
		// Se recorre la precedencia y se devuelve el primero que esté presente: lo que necesita atención
		// gana sobre lo que va bien.
		for (String estado : PRECEDENCIA) {
			if (estados.contains(estado)) {
				return estado;
			}
		}
		// Red de seguridad: un estado que no esté en PRECEDENCIA (no debería ocurrir). Devolver cualquiera
		// es mejor que devolver null y que la pantalla se quede en blanco.
		return estados.iterator().next();
	}

	// Los datos del creador como objeto anidado, igual en la lista y en el detalle.
	private Map<String, Object> creador(FilaExperimento fila) {
		Map<String, Object> creador = new LinkedHashMap<>();
		creador.put("idInstitucional", fila.getCreadorId());
		creador.put("nombre", fila.getCreadorNombre());
		creador.put("apellidos", fila.getCreadorApellidos());
		return creador;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> detalle(Integer id) {
		FilaExperimento fila = experimentoRepository.buscarFila(id)
				.orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + id + " no existe"));
		// Las mismas dos consultas de la lista, con un solo id: tres consultas para todo el árbol.
		List<FilaGrupo> grupos = grupoRepository.buscarGrupos(List.of(id));
		List<FilaTandaVideo> filas = tandaRepository.buscarTandasYVideos(List.of(id));

		// Las tandas de cada grupo y los videos de cada tanda, en el orden en que llegan (ya ordenados por
		// la consulta: tandas por ordinal, 'Dia 1' antes que 'Dia 2'). Una tanda sin video llega en una
		// sola fila con el video en null.
		//
		// Aquí se reconstruye el árbol (grupo → tanda → video) a partir de las filas planas. El truco es
		// la lista de videos: su presencia en el mapa dice si la tanda ya se creó.
		Map<Integer, List<Map<String, Object>>> tandasDe = new HashMap<>();
		Map<Integer, List<Map<String, Object>>> videosDe = new HashMap<>();
		for (FilaTandaVideo f : filas) {
			List<Map<String, Object>> videos = videosDe.get(f.getIdTanda());
			if (videos == null) {
				// Primera fila de esta tanda: se crea la tanda y su lista de videos.
				videos = new ArrayList<>();
				videosDe.put(f.getIdTanda(), videos);
				Map<String, Object> tanda = new LinkedHashMap<>();
				tanda.put("idTanda", f.getIdTanda());
				tanda.put("ordinal", f.getOrdinal().intValue());
				// La letra se calcula aquí en línea en vez de con el método letra() de otros servicios.
				tanda.put("letra", String.valueOf((char) ('A' + f.getOrdinal().intValue() - 1)));
				tanda.put("nCilindros", f.getCilindros().intValue());
				// La MISMA lista se guarda en el mapa y dentro de la tanda: lo que se añada abajo aparece
				// en las dos, porque son el mismo objeto.
				tanda.put("videos", videos);
				tandasDe.computeIfAbsent(f.getIdGrupo(), k -> new ArrayList<>()).add(tanda);
			}
			// Una tanda sin videos deja la lista vacía y no entra aquí.
			if (f.getIdVideo() != null) {
				Map<String, Object> video = new LinkedHashMap<>();
				video.put("idVideo", f.getIdVideo());
				video.put("sesion", f.getSesion());
				video.put("duracion", f.getDuracion());
				video.put("nombreOriginal", f.getNombreOriginal());   // null en los videos anteriores al 20-oct-2026
				video.put("fecha", f.getFecha());
				video.put("videoDisponible", f.getVideoDisponible());
				video.put("idAnalisis", f.getIdAnalisis());
				// Limitación conocida: la tabla VIDEO no guarda quién subió el video, así que se usa al
				// creador del experimento. Cuando varios investigadores suban videos al mismo experimento,
				// habrá que guardarlo (una columna idinstitucional en VIDEO).
				//
				// Hoy el dato es correcto porque solo el creador puede subir (ver VideoServiceImpl y
				// D-107): el comentario deja anotado qué habría que cambiar si esa regla se relaja.
				video.put("subidoPor", fila.getCreadorId());
				videos.add(video);
			}
		}

		// El árbol final: un grupo con sus tandas, y cada tanda con sus videos.
		List<Map<String, Object>> arbol = new ArrayList<>();
		for (FilaGrupo g : grupos) {
			Map<String, Object> grupo = new LinkedHashMap<>();
			grupo.put("idGrupo", g.getIdGrupo());
			grupo.put("etiqueta", g.getEtiqueta());
			grupo.put("tipo", g.getTipo());
			grupo.put("tratamiento", g.getTratamiento());
			// Un grupo sin tandas (que ya no debería existir, ver eliminarTanda) sale con la lista vacía.
			grupo.put("tandas", tandasDe.getOrDefault(g.getIdGrupo(), List.of()));
			arbol.add(grupo);
		}

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("idExperimento", fila.getIdExperimento());
		datos.put("nombre", fila.getNombre());
		datos.put("fecha", fila.getFecha());
		// Aquí las notas vacías se mandan como "" y no como null, al revés que en la lista: es lo que
		// espera el formulario de edición, que no sabría qué poner en una caja de texto con null.
		datos.put("notas", fila.getNotas() == null ? "" : fila.getNotas());
		// El mismo cálculo que en la lista: un solo sitio lo decide.
		datos.put("estado", calcularEstado(filas));
		datos.put("creador", creador(fila));
		datos.put("grupos", arbol);
		return datos;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Map<String, Object>> buscarGrupos(Integer id, String q) {
		if (!experimentoRepository.existsById(id)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + id + " no existe");
		}
		// Con q vacío, buscado queda "" y contains("") es siempre true: entran todos sin un caso aparte.
		String buscado = sinAcentos(q == null ? "" : q.trim());

		List<Map<String, Object>> resultado = new ArrayList<>();
		for (FilaGrupo g : grupoRepository.buscarGrupos(List.of(id))) {
			// Se busca en el mismo texto que ve el usuario: "etiqueta · tratamiento". Con q vacío entran todos.
			// Buscar sobre el texto completo permite escribir "control salina" y encontrarlo, aunque las
			// dos palabras estén en campos distintos.
			if (!sinAcentos(g.getEtiqueta() + " · " + g.getTratamiento()).contains(buscado)) {
				continue;
			}
			Map<String, Object> grupo = new LinkedHashMap<>();
			grupo.put("idGrupo", g.getIdGrupo());
			grupo.put("etiqueta", g.getEtiqueta());
			grupo.put("tipo", g.getTipo());
			grupo.put("tratamiento", g.getTratamiento());
			// El ordinal que le tocaría a la próxima tanda, que la pantalla propone al subir un video.
			grupo.put("siguienteOrdinal", g.getSiguienteOrdinal().intValue());
			resultado.add(grupo);
		}
		return resultado;
	}

	// Minúsculas y sin acentos, en Java (java.text.Normalizer) y no con una extensión de PostgreSQL:
	// NFD separa cada letra de su acento y \p{M} borra los acentos.
	//
	// Cómo funciona: NFD descompone "á" en la letra "a" más un carácter de acento aparte; \p{M} casa con
	// esos caracteres de marca y replaceAll los borra, dejando "a".
	// Se hace en Java y no con unaccent de PostgreSQL porque esa extensión hay que instalarla en la base,
	// y así el sistema funciona en cualquier PostgreSQL sin preparativos.
	// Locale.ROOT en toLowerCase: sin él, en turco la "I" no bajaría a "i" y la búsqueda fallaría.
	private static String sinAcentos(String texto) {
		return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	@Override
	@Transactional(readOnly = true)
	public Experimento buscar(Integer id) {
		// La búsqueda cruda con su 404, que reutilizan actualizar y eliminar.
		return experimentoRepository.findById(id)
				.orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + id + " no existe"));
	}

	// Crea un experimento: solo nombre (único), fecha y notas opcionales, más el creador. Los grupos se
	// capturan después, al subir el primer video de cada uno (todavía no implementado).
	//
	// (el paréntesis final quedó de una versión anterior: los grupos SÍ se crean hoy al subir el video,
	// en VideoServiceImpl.resolverDestino)
	@Override
	@Transactional
	public Experimento crear(Experimento experimento, String idCreador) {
		// El creador es quien tiene la sesión abierta: se ignora el idInstitucional que venga en el cuerpo,
		// para que nadie cree experimentos a nombre de otro.
		// Es la regla de seguridad que anuncia la propia entidad Experimento.
		experimento.setIdInstitucional(idCreador);

		if (!usuarioRepository.existsById(idCreador)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El usuario " + idCreador + " no existe");
		}

		if (experimentoRepository.existsByNombre(experimento.getNombre())) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe un experimento con el nombre '" + experimento.getNombre() + "'");
		}

		// El id lo asigna PostgreSQL; si el JSON trae uno, se ignora para que siempre sea un INSERT.
		// Sin esta línea, un id en el cuerpo haría que JPA intentara ACTUALIZAR ese experimento, que es
		// la misma trampa que explica AdminServiceImpl.crear con save().
		experimento.setIdExperimento(null);

		try {
			// saveAndFlush fuerza el INSERT aquí, dentro del try, para poder atrapar el choque.
			return experimentoRepository.saveAndFlush(experimento);
		} catch (DataIntegrityViolationException e) {
			// Por si otro usuario guardó el mismo nombre justo entre la revisión de arriba y este INSERT.
			// La restricción UNIQUE de la base es la última defensa; aquí se traduce al mismo 409.
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe un experimento con el nombre '" + experimento.getNombre() + "'");
		}
	}

	// Actualiza nombre, fecha y notas. El creador no cambia aunque venga otro en el JSON.
	@Override
	@Transactional
	public Experimento actualizar(Integer id, Experimento datos, Authentication authentication) {
		reportes.bloquearExperimento(id);     // primer paso: el candado del experimento (orden de candados)
		Experimento actual = buscar(id);

		// C-03 (decidido por Erick): solo quien creó el experimento o un Administrador lo edita. Mismo criterio que eliminar.
		// Los permisos ya vienen en el Authentication (ConvertidorJwt los leyó de la base en esta petición).
		boolean esCreador = actual.getIdInstitucional().equals(authentication.getName());
		boolean esAdministrador = authentication.getAuthorities().stream()
				.anyMatch(permiso -> permiso.getAuthority().equals("ROLE_ADMINISTRADOR"));
		if (!esCreador && !esAdministrador) {
			throw new ErrorApi(HttpStatus.FORBIDDEN, "Solo quien creó el experimento o un Administrador puede editarlo");
		}

		// El "Not" del nombre del método excluye el propio experimento: guardar sin cambiar el nombre no
		// puede chocar consigo mismo (ver ExperimentoRepository).
		if (experimentoRepository.existsByNombreAndIdExperimentoNot(datos.getNombre(), id)) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe otro experimento con el nombre '" + datos.getNombre() + "'");
		}

		// Solo estos tres campos: el creador y el id no se tocan, aunque vengan en el cuerpo.
		actual.setNombre(datos.getNombre());
		actual.setFecha(datos.getFecha());
		actual.setNotas(datos.getNotas());

		// El PDF muestra el nombre, la fecha y las notas: un reporte guardado ya no los refleja.
		// Es el segundo motivo de invalidación de los cuatro que enumera ReporteAlmacen.
		reportes.invalidar(id);
		try {
			return experimentoRepository.saveAndFlush(actual);
		} catch (DataIntegrityViolationException e) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe otro experimento con el nombre '" + datos.getNombre() + "'");
		}
	}

	// Elimina un experimento COMPLETO (D-87): sus grupos, tandas, especímenes, videos, análisis y resultados, más sus
	// reportes y notificaciones. Todas las llaves foráneas son ON DELETE NO ACTION: la base no borra en cascada. Por eso
	// se borra de las hojas hacia arriba y el experimento al final, todo en una sola transacción: si algo falla, no se
	// borra nada. Los archivos se borran DESPUÉS del commit, igual que al eliminar una tanda.
	//
	// "La base no borra en cascada" es una decisión, no una carencia: un ON DELETE CASCADE borraría
	// decenas de miles de filas en silencio con un solo DELETE. Escribirlo a mano obliga a pensar el
	// orden y deja a la vista todo lo que desaparece.
	@Override
	@Transactional
	public void eliminar(Integer id, Authentication authentication) {
		Experimento experimento = buscar(id);

		// R-08: solo quien creó el experimento o un Administrador. Los permisos ya vienen en el Authentication
		// (ConvertidorJwt los leyó de la base en esta petición), no hace falta otra consulta.
		boolean esCreador = experimento.getIdInstitucional().equals(authentication.getName());
		boolean esAdministrador = authentication.getAuthorities().stream()
				.anyMatch(permiso -> permiso.getAuthority().equals("ROLE_ADMINISTRADOR"));
		if (!esCreador && !esAdministrador) {
			throw new ErrorApi(HttpStatus.FORBIDDEN,
					"Solo quien creó el experimento o un Administrador puede eliminarlo");
		}

		// Candados de arriba hacia abajo, con FOR UPDATE: el experimento, sus grupos y (en bloquear) sus tandas y análisis.
		// Una subida a un grupo, tanda o experimento que ya existen solo toma FOR KEY SHARE sobre esa fila (lo que pide la
		// llave foránea); FOR UPDATE choca con él, así que la subida espera al commit y su INSERT falla después porque la
		// fila ya no existe. Sin esto, un video podía entrar entre el SELECT de idsVideo y el DELETE (archivo huérfano)
		// o crearse una tanda nueva en un grupo que se está borrando. Siempre en este orden (experimento, grupos, tandas,
		// análisis): es el mismo que sigue la subida, y el worker solo bloquea análisis, así que no hay deadlock.
		//
		// Es el razonamiento de concurrencia más fino del backend. Las tres ideas:
		//   1. los candados hacen que una subida en marcha espere, y al despertar su INSERT falle porque
		//      la fila padre ya no existe, en vez de dejar un video colgando de un grupo borrado;
		//   2. el archivo huérfano sería el peor caso: un .mp4 de 1 GB en el disco que ninguna fila
		//      referencia y que nadie volverá a borrar;
		//   3. el orden fijo (y el hecho de que el worker solo bloquee análisis) es lo que garantiza que
		//      nadie se espere en círculo. Es el mismo principio de CandadosDeCuenta.
		jdbc.queryForList("SELECT idexperimento FROM experimento WHERE idexperimento = ? FOR UPDATE", Integer.class, id);
		jdbc.queryForList("SELECT idgrupo FROM grupo WHERE idexperimento = ? FOR UPDATE", Integer.class, id);

		// bloquear() toma los candados de tandas y análisis y, de paso, rechaza con 409 si hay uno en marcha.
		List<Integer> idsAnalisis = bloquear(TANDAS_DEL_EXPERIMENTO, id,
				"No se puede eliminar mientras un análisis está procesando o en pausa");
		// Los ids de los videos se leen ANTES de borrar las filas: después ya no habría de dónde sacarlos
		// para borrar los archivos.
		List<Integer> idsVideo = jdbc.queryForList("SELECT idvideo FROM video WHERE idtanda IN " + TANDAS_DEL_EXPERIMENTO,
				Integer.class, id);

		// El borrado, de las hojas hacia arriba.
		borrarDesdeLosVideos(TANDAS_DEL_EXPERIMENTO, id);
		jdbc.update("DELETE FROM grupo WHERE idexperimento = ?", id);
		reportes.invalidar(id);     // las filas de REPORTE y, después del commit, la carpeta reportes/{id}/
		// Estas dos van antes del experimento porque sus llaves foráneas son ON DELETE NO ACTION
		// (ver NotificacionRepository y ReporteRepository).
		notificacionRepository.deleteByExperimentoIdExperimento(id);
		experimentoRepository.deleteById(id);

		borrarArchivosDespuesDelCommit(idsVideo, idsAnalisis);
	}

	// Elimina una tanda (regla nueva, parecida a R-08): su letra queda libre para volver a subirla. Todo en UNA
	// transacción, de las hojas hacia arriba; los archivos se borran DESPUÉS del commit (si fallara el borrado de uno, la
	// base ya quedó bien: solo se avisa en el log). Un grupo nunca queda sin tandas (§5 de la guía: grupo con 1 o más
	// tandas, y de ahí mínimo 2 especímenes): si era la última de su grupo, el grupo se borra en la misma transacción
	// (23-oct, decisión de Erick; antes, D-115, el grupo se conservaba vacío).
	//
	// El caso de uso es el del laboratorio: alguien sube el video equivocado y hay que deshacerlo.
	// Borrar el grupo que se queda vacío mantiene la regla del modelo que sostiene VideoServiceImpl desde
	// el otro lado (un grupo nace siempre con su primera tanda).
	@Override
	@Transactional
	public void eliminarTanda(Integer idExperimento, Integer idTanda, Authentication authentication) {
		// Primer paso: el candado del experimento, igual que eliminar, antes que el grupo, la tanda y el análisis.
		reportes.bloquearExperimento(idExperimento);
		Experimento experimento = buscar(idExperimento);
		// Las DOS condiciones: la tanda existe Y es de ese experimento. Sin la segunda, cambiando el
		// número de la ruta se podría borrar una tanda de otro experimento.
		List<Integer> gruposDeLaTanda = jdbc.queryForList("""
				SELECT t.idgrupo FROM tanda t JOIN grupo g ON g.idgrupo = t.idgrupo
				WHERE t.idtanda = ? AND g.idexperimento = ?
				""", Integer.class, idTanda, idExperimento);
		if (gruposDeLaTanda.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "La tanda " + idTanda + " no existe en el experimento " + idExperimento);
		}
		Integer idGrupo = gruposDeLaTanda.get(0);

		// Igual que el experimento: solo quien lo creó o un Administrador.
		boolean esCreador = experimento.getIdInstitucional().equals(authentication.getName());
		boolean esAdministrador = authentication.getAuthorities().stream()
				.anyMatch(permiso -> permiso.getAuthority().equals("ROLE_ADMINISTRADOR"));
		if (!esCreador && !esAdministrador) {
			throw new ErrorApi(HttpStatus.FORBIDDEN, "Solo quien creó el experimento o un Administrador puede eliminar una tanda");
		}

		// Mismo orden de candados que eliminar (grupo, tandas, análisis; ver los comentarios de ahí): el grupo también se
		// bloquea porque quizá se borre con su última tanda, y una subida a ese grupo debe esperar al commit.
		jdbc.queryForList("SELECT idgrupo FROM grupo WHERE idgrupo = ? FOR UPDATE", Integer.class, idGrupo);
		List<Integer> idsAnalisis = bloquear(TANDA_UNICA, idTanda,
				"No se puede eliminar mientras su análisis está procesando o en pausa");
		List<Integer> idsVideo = jdbc.queryForList("SELECT idvideo FROM video WHERE idtanda IN " + TANDA_UNICA,
				Integer.class, idTanda);

		borrarDesdeLosVideos(TANDA_UNICA, idTanda);
		// Si ya no quedan tandas en el grupo, el grupo se va con ella (no tiene nada más que lo cuelgue: especímenes y
		// tandas ya se borraron).
		// NOT EXISTS es más barato que un COUNT: la base para en cuanto encuentra una fila.
		Boolean sinTandas = jdbc.queryForObject("SELECT NOT EXISTS (SELECT 1 FROM tanda WHERE idgrupo = ?)",
				Boolean.class, idGrupo);
		// Boolean.TRUE.equals protege del null, aunque aquí no pueda darse.
		if (Boolean.TRUE.equals(sinTandas)) {
			jdbc.update("DELETE FROM grupo WHERE idgrupo = ?", idGrupo);
		}
		// Sus resultados cambian: los reportes guardados ya no los reflejan (filas y carpeta).
		reportes.invalidar(idExperimento);

		borrarArchivosDespuesDelCommit(idsVideo, idsAnalisis);
	}

	// Lo que comparten eliminar y eliminarTanda. "tandas" es una subconsulta con UN parámetro (?) que da las tandas a
	// borrar, y "parametro" es su valor (el idTanda o el idExperimento).
	//
	// Es un patrón elegante: las dos operaciones borran lo mismo y solo cambia QUÉ tandas. En vez de
	// duplicar seis DELETE, se parametriza la subconsulta que las elige. Y como las dos tienen exactamente
	// un ?, el mismo valor sirve en todas.
	// Son constantes del código, nunca texto del usuario: concatenarlas no abre ningún agujero.
	private static final String TANDA_UNICA = "(SELECT idtanda FROM tanda WHERE idtanda = ?)";
	private static final String TANDAS_DEL_EXPERIMENTO =
			"(SELECT t.idtanda FROM tanda t JOIN grupo g ON g.idgrupo = t.idgrupo WHERE g.idexperimento = ?)";

	// FOR UPDATE, de arriba hacia abajo: primero las tandas (una subida a una tanda que ya existe solo toma FOR KEY SHARE
	// sobre ella y así espera al commit; ver eliminar) y después los análisis, que quedan bloqueados hasta el commit.
	// El worker toma el siguiente con FOR UPDATE SKIP LOCKED, así que no lo agarra a la mitad; si ya lo tomó (procesando)
	// o está en pausa, no se borra. En cola sí: sale de la cola. Devuelve los ids de los análisis a borrar.
	//
	// SKIP LOCKED es la pieza que lo hace funcionar: el worker, al buscar trabajo, SALTA las filas que
	// otro tiene bloqueadas en vez de esperarlas. Así no se queda parado mientras se borra un experimento,
	// y tampoco puede tomar un análisis que está a punto de desaparecer.
	// La distinción de estados es la misma de ExperimentoService: 'en cola' se puede descartar sin más,
	// 'procesando' lo tiene el worker en la mano ahora mismo, y 'en pausa' espera a una persona.
	private List<Integer> bloquear(String tandas, Integer parametro, String mensajeSiOcupado) {
		jdbc.queryForList("SELECT idtanda FROM tanda WHERE idtanda IN " + tandas + " FOR UPDATE", Integer.class, parametro);
		List<Map<String, Object>> analisis = jdbc.queryForList(
				"SELECT idanalisis, estado FROM analisis WHERE idvideo IN (SELECT idvideo FROM video WHERE idtanda IN "
						+ tandas + ") FOR UPDATE",
				parametro);
		List<Integer> idsAnalisis = new ArrayList<>();
		for (Map<String, Object> a : analisis) {
			String estado = (String) a.get("estado");
			if (EstadoAnalisis.PROCESANDO.es(estado) || EstadoAnalisis.EN_PAUSA.es(estado)) {
				// Al lanzarse, la transacción se deshace y los candados se sueltan: nada queda a medias.
				throw new ErrorApi(HttpStatus.CONFLICT, mensajeSiOcupado);
			}
			idsAnalisis.add(((Number) a.get("idanalisis")).intValue());
		}
		return idsAnalisis;
	}

	// Los borrados de las hojas hacia arriba, hasta las tandas inclusive (el grupo y lo de más arriba lo decide cada
	// método). Observacion cuelga de especimen y también de video: se borra antes que los dos.
	//
	// El ORDEN es obligatorio: con llaves foráneas ON DELETE NO ACTION, borrar un padre antes que sus
	// hijos falla. Cada DELETE de aquí depende de que el anterior ya se hiciera.
	// La nota sobre observacion es el motivo de que vaya tan arriba: es la tabla que cruza las dos ramas
	// del modelo (ver la entidad Observacion), así que estorba a las dos.
	private void borrarDesdeLosVideos(String tandas, Integer parametro) {
		// La subconsulta de videos se arma una vez y se reutiliza en los tres primeros DELETE.
		String videos = "(SELECT idvideo FROM video WHERE idtanda IN " + tandas + ")";
		// 1. Los segundos: la tabla más grande, y la que cuelga de observacion.
		jdbc.update("DELETE FROM segundo WHERE idobservacion IN (SELECT idobservacion FROM observacion WHERE idvideo IN "
				+ videos + ")", parametro);
		// 2. Las observaciones, que ya no tienen segundos colgando.
		jdbc.update("DELETE FROM observacion WHERE idvideo IN " + videos, parametro);
		// 3. Los análisis, que cuelgan del video.
		jdbc.update("DELETE FROM analisis WHERE idvideo IN " + videos, parametro);
		// 4. Los videos, ya libres de observaciones y análisis.
		jdbc.update("DELETE FROM video WHERE idtanda IN " + tandas, parametro);
		// 5. Los especímenes, que ya no tienen observaciones.
		jdbc.update("DELETE FROM especimen WHERE idtanda IN " + tandas, parametro);
		// 6. Las tandas, ya sin videos ni especímenes.
		jdbc.update("DELETE FROM tanda WHERE idtanda IN " + tandas, parametro);
	}

	// Los archivos se borran DESPUÉS del commit: si fallara el borrado de uno, la base ya quedó bien (solo se avisa en el log).
	//
	// Es el mismo mecanismo que ReporteAlmacen.invalidar y por la misma razón: la base sabe deshacer una
	// transacción, el sistema de archivos no. Borrando después del commit, si la transacción se deshace
	// los archivos siguen ahí, que es lo correcto.
	private void borrarArchivosDespuesDelCommit(List<Integer> idsVideo, List<Integer> idsAnalisis) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				borrarArchivos(idsVideo, idsAnalisis);
			}
		});
	}

	// Ya con la base en orden: los videos (videos/{idVideo}.mp4 o .mov) y la carpeta analisis/{idAnalisis}/. Los nombres
	// salen de ids de la base, nunca de la petición, y la carpeta se revisa DENTRO de la raíz. Un fallo es solo un aviso.
	private void borrarArchivos(List<Integer> idsVideo, List<Integer> idsAnalisis) {
		Path raiz = Path.of(raizConfigurada).toAbsolutePath().normalize();
		for (Integer idVideo : idsVideo) {
			// newDirectoryStream con el patrón "17.*" encuentra el archivo sin saber si es .mp4 o .mov.
			// try-con-recursos: abre descriptores del sistema y hay que cerrarlos.
			try (DirectoryStream<Path> archivos = Files.newDirectoryStream(raiz.resolve("videos"), idVideo + ".*")) {
				for (Path archivo : archivos) {
					Files.deleteIfExists(archivo);
				}
			} catch (IOException e) {
				log.warn("No se pudo borrar el video {} de lo eliminado: {}", idVideo, e.getMessage());
			}
		}
		for (Integer idAnalisis : idsAnalisis) {
			Path carpeta = raiz.resolve("analisis").resolve(String.valueOf(idAnalisis)).normalize();
			// La comprobación de siempre: después de normalizar, la ruta sigue dentro de la raíz.
			if (!carpeta.startsWith(raiz) || !Files.isDirectory(carpeta)) {
				continue;
			}
			try {
				// El mismo visitante que ReporteAlmacen: Java no trae un "borrar carpeta con todo dentro".
				Files.walkFileTree(carpeta, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path archivo, BasicFileAttributes atributos) throws IOException {
						Files.delete(archivo);
						return FileVisitResult.CONTINUE;
					}

					// postVisitDirectory se llama DESPUÉS de recorrer el contenido, cuando la carpeta ya
					// está vacía y se puede borrar.
					@Override
					public FileVisitResult postVisitDirectory(Path directorio, IOException e) throws IOException {
						Files.delete(directorio);
						return FileVisitResult.CONTINUE;
					}
				});
			} catch (IOException e) {
				log.warn("No se pudo borrar la carpeta {} de lo eliminado: {}", carpeta, e.getMessage());
			}
		}
	}

	// Reporte de resultados con iText 5: arma el PDF en memoria y lo devuelve como bytes. Quien lo guarda y lo sirve es
	// ReporteAlmacen (GET .../report/pdf); este método no escribe nada. Va dentro de una transacción porque lee al creador
	// del experimento, que es una relación LAZY (se carga hasta que se usa).
	//
	// Esa última frase es importante: fuera de la transacción, experimento.getUsuario() lanzaría
	// LazyInitializationException, porque open-in-view está en false (ver application.properties).
	@Override
	@Transactional(readOnly = true)
	public ByteArrayInputStream generarReporte(Integer idExperimento) {
		// La versión antigua, heredada de la práctica: delega en la de abajo y envuelve los bytes.
		return new ByteArrayInputStream(generarReporteDeResultados(idExperimento).pdf());
	}

	// Lo mismo que generarReporte, con la marca conResultados: false si el experimento no tiene análisis completados (el PDF
	// dice «Sin resultados»). ReporteAlmacen no guarda ese PDF en caché.
	//
	// Orden de las secciones (los marcadores siguen este orden): Resumen · Resultados por grupo · Gráficas · Tabla por
	// espécimen · Desglose por minuto · Modelo del análisis (solo en modo desarrollador). Las gráficas salen de
	// resultadosService.graficas, el mismo mapa que consume la pantalla.
	//
	// Esa última frase es la regla de siempre: un solo cálculo para la web y para el papel, así no pueden
	// diferir. Y el motivo de conResultados está en ExperimentoController: guardar un PDF que dice "sin
	// resultados" sería peligroso, porque nada lo invalidaría al terminar el primer análisis.
	@Override
	@Transactional(readOnly = true)
	public ReporteDeResultados generarReporteDeResultados(Integer idExperimento) {
		Experimento experimento = experimentoRepository.findById(idExperimento).orElseThrow();
		// Aquí se dispara la carga LAZY del creador; de ahí que haga falta la transacción.
		Usuario creador = experimento.getUsuario();
		List<FilaResultado> filas = segundoRepository.buscarResultados(idExperimento);
		// Los especímenes con segundos que no son de la máquina: "grupo|ordinal|cilindro".
		// Un Set de esas claves permite preguntar después, por cada fila, si ese espécimen fue revisado,
		// sin recorrer la lista una y otra vez.
		Set<String> revisados = segundoRepository.buscarRevisados(idExperimento).stream()
				.map(r -> r.getGrupo() + "|" + r.getOrdinal().intValue() + "|" + r.getCilindro().intValue())
				.collect(Collectors.toSet());

		// PageSize.LETTER: el tamaño de papel de México y Estados Unidos (no A4). Los márgenes de arriba y abajo dejan sitio al
		// encabezado y al pie de cada página, que llevan el estilo del sistema (ver EstiloReportePdf).
		Document documento = new Document(PageSize.LETTER, 48, 48, 66, 62);
		// El PDF se arma en memoria: estos reportes son de unos pocos cientos de kilobytes.
		ByteArrayOutputStream salida = new ByteArrayOutputStream();

		try {
			PdfWriter writer = PdfWriter.getInstance(documento, salida);
			// La tipografía y los colores del sistema, para todo el documento; el marco pone «FST» y las migas arriba y la versión y la
			// página abajo.
			EstiloReportePdf estilo = new EstiloReportePdf();
			writer.setPageEvent(new EstiloReportePdf.Marco(estilo, experimento.getNombre(), versionApp));
			// Los marcadores son el índice lateral del PDF, que permite saltar entre secciones.
			MarcadoresPdf marcadores = new MarcadoresPdf(writer);
			documento.open();

			Font letraDato = estilo.texto(9.5f);

			marcadores.seccion("Resumen", MarcadoresPdf.RESUMEN);
			documento.add(estilo.titulo("Reporte de resultados", MarcadoresPdf.RESUMEN));

			// Datos del experimento, un renglón cada uno.
			documento.add(estilo.dato("Experimento", experimento.getNombre()));
			documento.add(estilo.dato("Fecha", String.valueOf(experimento.getFecha())));
			// El nombre del creador impreso aquí es lo que obliga a invalidar los reportes cuando alguien
			// cambia su nombre (ver AuthServiceImpl.actualizarPerfil).
			documento.add(estilo.dato("Creado por", creador.getNombre() + " " + creador.getApellidos()
					+ " (" + creador.getIdInstitucional() + ")"));
			if (experimento.getNotas() != null && !experimento.getNotas().isBlank()) {
				documento.add(estilo.dato("Notas", experimento.getNotas()));
			}
			// La línea de transparencia: cuántos especímenes tocó una persona. El ternario de dentro
			// resuelve el singular y el plural.
			documento.add(estilo.dato("Revisión humana", revisados.isEmpty() ? "No"
					: "Sí (" + revisados.size() + (revisados.size() == 1 ? " espécimen" : " especímenes")
					+ " con segundos corregidos)"));
			documento.add(estilo.dato("Generado", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));

			if (filas.isEmpty()) {
				// El PDF "vacío": se genera igual (el usuario pidió un reporte y recibe uno), pero
				// conResultados será false y ReporteAlmacen no lo guardará.
				documento.add(new Paragraph(
						"Sin resultados: el experimento todavía no tiene análisis completados.", letraDato));
			} else {
				// Los videos analizados: cada tanda con el nombre del archivo como lo subió el usuario. Siempre visible (no
				// depende del modo desarrollador); los videos anteriores al 20-oct-2026 no tienen nombre.
				documento.add(estilo.seccion("Videos analizados", "videos-analizados"));
				documento.add(armarTablaVideos(resultadosService.videosAnalizados(idExperimento), estilo));

				// Resultados por grupo: las medias de la gráfica, las mismas cifras que dibuja el PDF y la pantalla.
				// Se piden UNA vez y se reutilizan para la tabla y para las gráficas de más abajo.
				Map<String, Object> graficas = resultadosService.graficas(idExperimento);
				marcadores.seccion("Resultados por grupo", MarcadoresPdf.RESULTADOS_GRUPO);
				documento.add(estilo.seccion("Resultados por grupo", MarcadoresPdf.RESULTADOS_GRUPO));
				documento.add(armarTablaGrupos(graficas, estilo));
				// La nota explica qué significan los números: sin ella, un "12.3 ± 4.5" no dice si el
				// segundo número es la desviación estándar o el error estándar.
				documento.add(estilo.nota("Media ± DE de los especímenes de cada grupo, en segundos del Día 2. Un grupo con "
						+ "niveles de clasificación mezclados se da por tanda. " + GraficaPdf.NOTA_DE));

				// Gráficas: solo las que aplican; si ninguna aplica, no hay sección ni marcador.
				// Toda la lógica vive en SeccionGraficasPdf; aquí solo se le pasa el documento y los datos.
				SeccionGraficasPdf.escribir(documento, writer, graficas, marcadores, estilo);

				marcadores.seccion("Tabla por espécimen", MarcadoresPdf.TABLA_ESPECIMEN);
				documento.add(estilo.seccion("Tabla por espécimen", MarcadoresPdf.TABLA_ESPECIMEN));
				documento.add(armarTablaEspecimenes(resultadosService.resultados(idExperimento), estilo));

				marcadores.seccion("Desglose por minuto", MarcadoresPdf.DESGLOSE_MINUTO);
				documento.add(estilo.seccion("Desglose por minuto", MarcadoresPdf.DESGLOSE_MINUTO));
				documento.add(armarTabla(filas, revisados, estilo));
				// La nota se adelanta a la pregunta obvia de quien mire la tabla: por qué un minuto no
				// suma 60 segundos.
				documento.add(estilo.nota("Segundos de cada conducta en cada minuto. Un minuto suma menos de "
						+ "60 s cuando hubo segundos sin conducta (el espécimen no se ve o el sistema no decidió)."));

				// Cuánto tardó cada tanda analizada y con qué modelo, solo en modo desarrollador: sin él, las dos listas
				// salen vacías y la sección no sale.
				// El modo se comprueba de forma indirecta: tiemposDelExperimento devuelve una lista vacía
				// cuando está apagado, así que basta con mirar si viene algo.
				List<Map<String, Object>> tiempos = resultadosService.tiemposDelExperimento(idExperimento);
				if (!tiempos.isEmpty()) {
					marcadores.seccion("Modelo del análisis", MarcadoresPdf.MODELO);
					documento.add(estilo.seccion("Modelo del análisis", MarcadoresPdf.MODELO));

					documento.add(estilo.subseccion("Tiempos de análisis"));
					documento.add(armarTablaTiempos(tiempos, estilo));
					documento.add(estilo.nota("Tiempo que el worker trabajó en el análisis (no incluye la espera en la "
							+ "cola). \"sin registro\": análisis de antes de que se midiera el tiempo."));

					documento.add(estilo.subseccion("Modelo con el que se hizo cada análisis"));
					documento.add(armarTablaModelos(tiempos, estilo));
					documento.add(estilo.nota("SHA-256: los primeros 16 caracteres de la huella del archivo del modelo; lo "
							+ "identifica aunque cambie de nombre. \"sin registro\": análisis de antes de que se anotara el modelo."));
				}
			}

			// close() escribe el índice final del PDF: sin él, el archivo no se puede abrir.
			documento.close();
		} catch (DocumentException | IOException e) {
			// A diferencia de la práctica (que imprimía el error y devolvía el PDF a medias), aquí la excepción
			// sube hasta ManejadorErrores, que responde 500.
			//
			// Es lo correcto: un PDF truncado es peor que ningún PDF, porque el usuario no sabría que le
			// faltan páginas.
			log.error("No se pudo generar el PDF del experimento {}: {}", idExperimento, e.getMessage());
			throw new IllegalStateException("No se pudo generar el PDF", e);
		}

		// El segundo campo es lo que ReporteAlmacen mira para decidir si guardarlo.
		return new ReporteDeResultados(salida.toByteArray(), !filas.isEmpty());
	}

	// Las medias de cada grupo: una fila por grupo con su n y, por conducta, media ± DE. Un grupo con niveles mezclados
	// lleva «por nivel» y una fila por tanda debajo, con sus números (porNivel).
	//
	// Es la tabla que responde la pregunta del experimento. El caso de niveles mezclados sale de
	// NivelClasifAnalisis: dos tandas clasificadas con distinto detalle no se pueden promediar juntas.
	private PdfPTable armarTablaGrupos(Map<String, Object> graficas, EstiloReportePdf estilo) throws DocumentException {
		// 7 columnas: el grupo, la n y las cinco de conducta.
		PdfPTable tabla = estilo.tabla(2.0f, 1.2f, 1.6f, 1.6f, 1.7f, 1.6f, 1.4f);
		Font letraTexto = estilo.texto(8.5f);
		estilo.encabezados(tabla, "IDDDDDD", "Grupo", "n", "Nado activo (s)", "Inmovilidad (s)", "Escalamiento (s)", "Conducta activa (s)", "Sin ver (s)");
		// DatosGraficas son utilidades para leer el mapa de graficas sin conversiones repetidas.
		for (Map<String, Object> grupo : DatosGraficas.grupos(graficas)) {
			tabla.addCell(estilo.izquierda(String.valueOf(grupo.get("etiqueta")), letraTexto, true));
			// textoN da "n = 6" o "n = 6 de 8" si faltan especímenes por analizar.
			tabla.addCell(estilo.derecha(DatosGraficas.textoN(grupo), letraTexto));
			if (DatosGraficas.bool(grupo.get("nivelMixto"))) {
				// Las cinco columnas dicen "por nivel" y los números van en las filas de abajo.
				for (int i = 0; i < CONDUCTAS_GRUPO.size() + 1; i++) {
					tabla.addCell(estilo.derecha("por nivel", letraTexto));
				}
			} else {
				for (String conducta : COLUMNAS_GRUPO) {
					Map<String, Object> e = DatosGraficas.estadisticos(grupo, conducta);
					// mediaDe arma el "12.3 ± 4.5"; con la DE en null (un solo espécimen) escribe solo la media.
					tabla.addCell(estilo.derecha(DatosGraficas.mediaDe(DatosGraficas.dbl(e.get("media")),
							DatosGraficas.dbl(e.get("de")), ""), letraTexto));
				}
			}
			// Las filas por tanda, solo si el grupo tiene niveles mezclados (porNivel es null si no).
			// Con null, lista() devuelve una lista vacía y el bucle no da vueltas.
			for (Map<String, Object> renglon : DatosGraficas.lista(grupo.get("porNivel"))) {
				// Los espacios del principio indentan la fila bajo su grupo.
				tabla.addCell(estilo.izquierda("   Tanda " + renglon.get("letra") + " · " + renglon.get("nivelClasif"), letraTexto, true));
				tabla.addCell(estilo.derecha("n = " + renglon.get("n"), letraTexto));
				Map<String, Object> porConducta = DatosGraficas.mapa(renglon.get("porConducta"));
				for (String conducta : COLUMNAS_GRUPO) {
					Map<String, Object> e = DatosGraficas.mapa(porConducta.get(conducta));
					tabla.addCell(estilo.derecha(DatosGraficas.mediaDe(DatosGraficas.dbl(e.get("media")),
							DatosGraficas.dbl(e.get("de")), ""), letraTexto));
				}
			}
		}
		return tabla;
	}

	// Lo que se ve en la pantalla por espécimen: los segundos de cada conducta y su porcentaje (los de resultados, los mismos).
	// "Los mismos" otra vez: salen de resultadosService.resultados, no se recalculan aquí.
	private PdfPTable armarTablaEspecimenes(Map<String, Object> resultados, EstiloReportePdf estilo) throws DocumentException {
		PdfPTable tabla = estilo.tabla(2.6f, 1.4f, 1.4f, 1.4f, 1.4f, 1.4f);
		Font letraTexto = estilo.texto(9);
		estilo.encabezados(tabla, "IDDDDD", "Espécimen", "Nado activo", "Inmovilidad", "Escalamiento", "Conducta activa", "Sin ver");
		// Se copia la lista para poder añadirle «sin ver»: CONDUCTAS_GRUPO es inmutable (List.of).
		List<String> columnas = new ArrayList<>(CONDUCTAS_GRUPO);
		columnas.add(TablaDeReporte.SIN_VER);
		for (Map<String, Object> especimen : DatosGraficas.lista(resultados.get("especimenes"))) {
			tabla.addCell(estilo.izquierda(especimen.get("tanda") + " · Espécimen " + especimen.get("numeroCilindro"), letraTexto, true));
			Map<String, Object> totales = DatosGraficas.mapa(especimen.get("totales"));
			for (String columna : columnas) {
				Map<String, Object> par = DatosGraficas.mapa(totales.get(columna));
				// "180 (60.0 %)": los segundos y su porcentaje. Con null se deja la celda vacía en vez de
				// escribir "null".
				String texto = par == null ? "" : par.get("segundos") + " (" + DatosGraficas.numero(DatosGraficas.dbl(par.get("porcentaje"))) + " %)";
				tabla.addCell(estilo.derecha(texto, letraTexto));
			}
		}
		return tabla;
	}

	// Las conductas que llevan las tablas de grupos y de espécimen, en el orden de la pantalla (sin «sin ver»).
	// Ojo: este orden (inmovilidad antes que escalamiento) NO es el de CONDUCTAS de arriba, que sigue el
	// del reporte CSV. Son dos tablas distintas con dos ordenaciones pedidas por separado.
	private static final List<String> CONDUCTAS_GRUPO = List.of("nado activo", "inmovilidad", "escalamiento", "conducta activa");
	// Las columnas de la tabla de grupos: las cuatro conductas y «sin ver» (la pantalla muestra las cinco).
	private static final List<String> COLUMNAS_GRUPO = Stream.concat(CONDUCTAS_GRUPO.stream(), Stream.of(TablaDeReporte.SIN_VER)).toList();

	// La consulta trae una fila por conducta; la tabla lleva una fila por espécimen y minuto con las cuatro
	// conductas en columnas. Este método junta las cuatro filas de la consulta en una de la tabla.
	//
	// Es un "pivote": pasar de filas largas (una por conducta) a filas anchas (una por minuto, con las
	// conductas en columnas), que es como se lee una tabla en papel.
	private PdfPTable armarTabla(List<FilaResultado> filas, Set<String> revisados, EstiloReportePdf estilo) throws DocumentException {
		PdfPTable tabla = estilo.tabla(4.0f, 1.5f, 0.9f, 1.5f, 1.4f, 1.4f, 0.9f);

		estilo.encabezados(tabla, "IDDDDDD", "Espécimen", "Tiempo (min:seg)", "Nado", "Escalamiento", "Inmovilidad", "Conducta activa", "Sin ver");

		// Agrupa: la llave es "espécimen|minuto" y el valor, los segundos de cada conducta. LinkedHashMap
		// conserva el orden en que llegan las filas (el ORDER BY de la consulta).
		Map<String, Map<String, BigDecimal>> segundosPorFila = new LinkedHashMap<>();
		// Un segundo mapa con las etiquetas ya armadas, para no recalcularlas al escribir.
		Map<String, String[]> etiquetasPorFila = new HashMap<>();
		for (FilaResultado f : filas) {
			char letraTanda = (char) ('A' + f.getOrdinal().intValue() - 1);
			String especimen = f.getGrupo() + " · Tanda " + letraTanda + " · Espécimen " + f.getCilindro();
			// La marca de revisión va pegada al nombre del espécimen: así, al leer una fila se sabe de
			// inmediato si ese dato lo revisó una persona.
			if (revisados.contains(f.getGrupo() + "|" + f.getOrdinal().intValue() + "|" + f.getCilindro().intValue())) {
				especimen += " · revisado";
			}
			int minuto = f.getMinuto().intValue();
			// El mismo formato que TablaDeReporte.FilaDeReporte.tiempo(): el minuto 1 es 0:00-1:00.
			String tiempo = (minuto - 1) + ":00-" + minuto + ":00";
			String llave = especimen + "|" + minuto;

			// Un segundo sin conducta (NULL) es «Sin ver»: la quinta columna, igual que en CSV y XLSX (R49).
			String columna = f.getConducta() == null ? TablaDeReporte.SIN_VER : f.getConducta();
			segundosPorFila.computeIfAbsent(llave, k -> new HashMap<>()).put(columna, f.getSegundos());
			etiquetasPorFila.put(llave, new String[] {especimen, tiempo});
		}

		Font letraTexto = estilo.texto(8.5f);
		for (Map.Entry<String, Map<String, BigDecimal>> fila : segundosPorFila.entrySet()) {
			String[] etiquetas = etiquetasPorFila.get(fila.getKey());
			tabla.addCell(estilo.izquierda(etiquetas[0], letraTexto, true));
			tabla.addCell(estilo.derecha(etiquetas[1], letraTexto));
			// Las cinco columnas en el orden del encabezado. Una conducta que no ocurrió sale como 0.
			for (String conducta : COLUMNAS_PDF) {
				BigDecimal segundos = fila.getValue().getOrDefault(conducta, BigDecimal.ZERO);
				// stripTrailingZeros + toPlainString escriben "12" en vez de "12.00", que es un conteo de
				// segundos y no necesita decimales. toPlainString evita la notación científica.
				tabla.addCell(estilo.derecha(segundos.stripTrailingZeros().toPlainString(), letraTexto));
			}
		}
		return tabla;
	}

	// "Videos analizados": una fila por tanda con su grupo, el nombre del video como lo subió el usuario (o "nombre no
	// registrado" en los videos anteriores al 20-oct-2026) y su duración.
	// Es la tabla que permite rastrear de qué archivo salió cada medición.
	private PdfPTable armarTablaVideos(List<Map<String, Object>> videos, EstiloReportePdf estilo) throws DocumentException {
		PdfPTable tabla = estilo.tabla(3.2f, 5f, 1.6f);

		estilo.encabezados(tabla, "IID", "Grupo · Tanda", "Video", "Duración (mm:ss)");

		Font letraTexto = estilo.texto(9);
		for (Map<String, Object> video : videos) {
			tabla.addCell(estilo.izquierda(video.get("grupo") + " · Tanda " + video.get("tanda"), letraTexto, true));
			tabla.addCell(estilo.izquierda(nombreDelVideo(video), letraTexto, false));
			tabla.addCell(estilo.derecha(mmss((Number) video.get("duracionVideo")), letraTexto));
		}
		return tabla;
	}

	// El nombre del video o un texto claro si no se guardó. "nombre no registrado" y no una celda vacía:
	// así se distingue "no lo sabemos" de un fallo al dibujar la tabla.
	private static String nombreDelVideo(Map<String, Object> video) {
		Object nombre = video.get("nombreVideo");
		return nombre != null ? nombre.toString() : "nombre no registrado";
	}

	// Una fila por tanda analizada: grupo y tanda, duración del video, tiempo total, tiempo de cada etapa y equipo.
	// Solo sale en modo desarrollador: sirve para comparar cuánto tarda el análisis en distintas máquinas.
	private PdfPTable armarTablaTiempos(List<Map<String, Object>> tandas, EstiloReportePdf estilo) throws DocumentException {
		PdfPTable tabla = estilo.tabla(2.3f, 1.1f, 1.4f, 2.3f, 1.2f, 1.8f, 2.5f);

		// Letra 8 y no 9 en los datos: son siete columnas con datos largos y hay que apretar.
		estilo.encabezados(tabla, "IDDDDDI", "Grupo · Tanda", "Video (mm:ss)", "Total (mm:ss)", "Preprocesamiento", "Cilindros", "Clasificación", "Equipo");

		Font letraTexto = estilo.texto(8);
		for (Map<String, Object> tanda : tandas) {
			// Además del grupo y la tanda, el nombre del video, para saber a qué archivo corresponde cada tiempo.
			// El \n lo parte en dos renglones dentro de la misma celda.
			tabla.addCell(estilo.izquierda(tanda.get("grupo") + " · Tanda " + tanda.get("tanda") + "\n"
					+ nombreDelVideo(tanda), letraTexto, true));
			tabla.addCell(estilo.derecha(mmss((Number) tanda.get("duracionVideo")), letraTexto));

			@SuppressWarnings("unchecked")
			Map<String, Object> tiempos = (Map<String, Object>) tanda.get("tiempos");
			if (tiempos == null) {
				// Análisis de antes de que el worker midiera el tiempo.
				// Hay que rellenar las cinco celdas que faltan: una tabla de iText se llena en orden y
				// dejar huecos correría todo lo demás.
				tabla.addCell(estilo.derecha("sin registro", letraTexto));
				for (int i = 0; i < 3; i++) {
					tabla.addCell(estilo.derecha("—", letraTexto));
				}
				tabla.addCell(estilo.izquierda("—", letraTexto, false));
				continue;
			}
			tabla.addCell(estilo.derecha(mmss((Number) tiempos.get("totalSegundos")), letraTexto));
			// Las etapas vienen como lista; se pasan a mapa para poder pedirlas por nombre.
			Map<String, Number> porEtapa = new HashMap<>();
			for (Object etapa : (List<?>) tiempos.get("etapas")) {
				Map<?, ?> e = (Map<?, ?>) etapa;
				porEtapa.put((String) e.get("etapa"), (Number) e.get("segundos"));
			}
			// Se recorre el ENUM y no la lista del archivo: así las columnas salen siempre en el mismo
			// orden y en el mismo número, aunque el archivo traiga las etapas desordenadas o falte alguna.
			for (EtapaAnalisis etapa : EtapaAnalisis.values()) {
				tabla.addCell(estilo.derecha(mmss(porEtapa.get(etapa.valor())), letraTexto));
			}
			tabla.addCell(estilo.izquierda(textoDelEquipo((Map<?, ?>) tiempos.get("equipo")), letraTexto, false));
		}
		return tabla;
	}

	// Una fila por tanda analizada: grupo y tanda, y el modelo con el que se hizo (archivo, versión de la red, huella, tamaño
	// y fecha del archivo). Las de antes de que se anotara el modelo dicen "sin registro".
	//
	// Es la tabla que permite explicar por qué dos corridas del mismo video dieron resultados distintos.
	private PdfPTable armarTablaModelos(List<Map<String, Object>> tandas, EstiloReportePdf estilo) throws DocumentException {
		PdfPTable tabla = estilo.tabla(2.8f, 2.2f, 1.4f, 2.6f, 1.2f, 1.6f);

		estilo.encabezados(tabla, "IIIIDD", "Grupo · Tanda", "Archivo del modelo", "Versión de la red", "SHA-256", "Tamaño", "Archivo del");

		Font letraTexto = estilo.texto(8);
		for (Map<String, Object> tanda : tandas) {
			tabla.addCell(estilo.izquierda(tanda.get("grupo") + " · Tanda " + tanda.get("tanda") + "\n"
					+ nombreDelVideo(tanda), letraTexto, true));

			@SuppressWarnings("unchecked")
			Map<String, Object> tiempos = (Map<String, Object>) tanda.get("tiempos");
			// Dos niveles de null: puede faltar el bloque de tiempos entero, o solo el de modelo dentro.
			@SuppressWarnings("unchecked")
			Map<String, Object> modelo = tiempos == null ? null : (Map<String, Object>) tiempos.get("modelo");
			if (modelo == null) {
				tabla.addCell(estilo.izquierda(ModeloDelAnalisis.SIN_REGISTRO, letraTexto, false));
				tabla.addCell(estilo.izquierda("—", letraTexto, false));
				tabla.addCell(estilo.izquierda("—", letraTexto, false));
				tabla.addCell(estilo.derecha("—", letraTexto));
				tabla.addCell(estilo.derecha("—", letraTexto));
				continue;
			}
			tabla.addCell(estilo.izquierda(String.valueOf(modelo.get("archivo")), letraTexto, false));
			// Cada campo que puede faltar escribe una raya en vez de "null".
			tabla.addCell(estilo.izquierda(modelo.get("version") != null ? modelo.get("version").toString() : "—", letraTexto, false));
			// huella() y fecha() son de ModeloDelAnalisis, el mismo que arma la línea del CSV y del XLSX.
			tabla.addCell(estilo.izquierda(ModeloDelAnalisis.huella(modelo) != null ? ModeloDelAnalisis.huella(modelo) : "—", letraTexto, false));
			tabla.addCell(estilo.derecha(modelo.get("tamanoMB") != null ? modelo.get("tamanoMB") + " MB" : "—", letraTexto));
			tabla.addCell(estilo.derecha(ModeloDelAnalisis.fecha(modelo) != null ? ModeloDelAnalisis.fecha(modelo) : "—", letraTexto));
		}
		return tabla;
	}

	// "Laptop Erick · 8 núcleos": el nombre (o, si no tiene, el CPU) y los núcleos.
	// El nombre sale de EQUIPO_NOMBRE; si nadie lo configuró, el modelo del procesador sirve igual para
	// distinguir una máquina de otra.
	private static String textoDelEquipo(Map<?, ?> equipo) {
		Object quien = equipo.get("nombre") != null ? equipo.get("nombre") : equipo.get("cpu");
		String texto = quien != null ? quien.toString() : "equipo desconocido";
		return equipo.get("nucleos") != null ? texto + " · " + equipo.get("nucleos") + " núcleos" : texto;
	}

	// mm:ss, o h:mm:ss si pasa de una hora; "—" si no hay dato.
	//
	// El formato cambia solo porque "125:30" se lee peor que "2:05:30".
	// %02d rellena con un cero a la izquierda: 5 segundos salen como "05".
	private static String mmss(Number segundos) {
		if (segundos == null) {
			return "—";
		}
		// Math.round porque el valor puede venir con decimales (es un double en el JSON).
		long total = Math.round(segundos.doubleValue());
		long horas = total / 3600;
		// (total % 3600) / 60 son los minutos que sobran después de quitar las horas.
		long minutos = (total % 3600) / 60;
		long resto = total % 60;
		return horas > 0 ? String.format("%d:%02d:%02d", horas, minutos, resto) : String.format("%d:%02d", minutos, resto);
	}

}
