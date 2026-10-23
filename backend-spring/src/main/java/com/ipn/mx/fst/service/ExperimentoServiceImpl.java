package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Experimento;
import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.repository.ExperimentoRepository;
import com.ipn.mx.fst.repository.FilaExperimento;
import com.ipn.mx.fst.repository.FilaGrupo;
import com.ipn.mx.fst.repository.FilaResultado;
import com.ipn.mx.fst.repository.FilaTandaVideo;
import com.ipn.mx.fst.repository.GrupoRepository;
import com.ipn.mx.fst.repository.NotificacionRepository;
import com.ipn.mx.fst.repository.ReporteRepository;
import com.ipn.mx.fst.repository.SegundoRepository;
import com.ipn.mx.fst.repository.TandaRepository;
import com.ipn.mx.fst.repository.UsuarioRepository;
import com.ipn.mx.fst.utilerias.ErrorApi;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ExperimentoServiceImpl implements ExperimentoService {

	private static final Logger log = LoggerFactory.getLogger(ExperimentoServiceImpl.class);

	// Las cuatro conductas en el orden de las columnas del reporte (sección 7 de la guía).
	private static final String[] CONDUCTAS = {"nado activo", "escalamiento", "inmovilidad", "conducta activa"};

	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
	private static final int POR_PAGINA = 20;
	// R-05: el video se borra 30 días después de un análisis completado; con 7 días o menos la lista avisa.
	private static final int DIAS_RETENCION = 30;
	private static final int DIAS_AVISO = 7;
	// Estados de un análisis de mayor a menor precedencia, para el estado del experimento (guía 4.8).
	private static final List<String> PRECEDENCIA = List.of("error", "cancelado", "en pausa", "procesando", "en cola", "completado");

	@Autowired
	private ExperimentoRepository experimentoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private GrupoRepository grupoRepository;

	@Autowired
	private ReporteRepository reporteRepository;

	@Autowired
	private NotificacionRepository notificacionRepository;

	@Autowired
	private SegundoRepository segundoRepository;

	@Autowired
	private TandaRepository tandaRepository;

	// Para eliminar y eliminarTanda: consultas con candado (FOR UPDATE) y los borrados de las hojas hacia arriba, explícitos.
	@Autowired
	private JdbcTemplate jdbc;

	// Raíz del volumen videos_reportes: de ahí se borran los videos y la carpeta del análisis de una tanda o un experimento eliminado.
	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	// Los tiempos de cada análisis (tiempos.json) para la sección "Tiempos de análisis" del PDF.
	@Autowired
	private ResultadosService resultadosService;

	// --- Lista y detalle: la forma de la guía 6.1, armada con consultas agregadas (proyecciones Fila*) y
	// mapas, no con la entidad Experimento (sus relaciones LAZY no se pueden serializar). ---

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> listar(int pagina) {
		if (pagina < 1) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El parámetro 'pagina' no tiene un valor válido");
		}

		List<FilaExperimento> filas = experimentoRepository.buscarPagina(POR_PAGINA, (long) (pagina - 1) * POR_PAGINA);
		List<Map<String, Object>> items = new ArrayList<>();
		if (!filas.isEmpty()) {
			// Una consulta de grupos y otra de tandas con videos para TODA la página, no una por experimento.
			List<Integer> ids = filas.stream().map(FilaExperimento::getIdExperimento).toList();
			Map<Integer, List<FilaGrupo>> gruposDe = grupoRepository.buscarGrupos(ids).stream()
					.collect(Collectors.groupingBy(FilaGrupo::getIdExperimento));
			Map<Integer, List<FilaTandaVideo>> tandasDe = tandaRepository.buscarTandasYVideos(ids).stream()
					.collect(Collectors.groupingBy(FilaTandaVideo::getIdExperimento));
			for (FilaExperimento fila : filas) {
				items.add(itemDeLista(fila, gruposDe.getOrDefault(fila.getIdExperimento(), List.of()),
						tandasDe.getOrDefault(fila.getIdExperimento(), List.of())));
			}
		}

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("pagina", pagina);
		datos.put("total", experimentoRepository.count());
		datos.put("items", items);
		return datos;
	}

	private Map<String, Object> itemDeLista(FilaExperimento fila, List<FilaGrupo> grupos, List<FilaTandaVideo> filas) {
		Set<Integer> tandas = new HashSet<>();
		Set<Integer> tandasConDia2 = new HashSet<>();
		Set<Integer> tandasAnalizadas = new HashSet<>();
		int especimenes = 0;
		boolean videosBorrados = false;
		Integer diasRetencion = null;
		for (FilaTandaVideo f : filas) {
			// La consulta trae una fila por video; la tanda se cuenta una sola vez.
			if (tandas.add(f.getIdTanda())) {
				especimenes += f.getCilindros().intValue();
			}
			if (f.getIdVideo() == null) {
				continue;
			}
			if (!f.getVideoDisponible()) {
				videosBorrados = true;
			}
			boolean dia2 = "Dia 2".equals(f.getSesion());
			boolean completado = "completado".equals(f.getEstadoAnalisis());
			if (dia2) {
				tandasConDia2.add(f.getIdTanda());
				if (completado) {
					tandasAnalizadas.add(f.getIdTanda());
				}
			}
			// R-05: el video se borra 30 días después de un análisis completado. Solo cuentan los videos que
			// todavía existen; el experimento muestra el que está más cerca de borrarse.
			if (completado && f.getVideoDisponible()) {
				int restantes = Math.max(0, DIAS_RETENCION - f.getDiasDesdeAnalisis().intValue());
				diasRetencion = diasRetencion == null ? restantes : Math.min(diasRetencion, restantes);
			}
		}

		Map<String, Object> videosDia2 = new LinkedHashMap<>();
		videosDia2.put("analizados", tandasAnalizadas.size());
		videosDia2.put("cargados", tandasConDia2.size());
		videosDia2.put("total", tandas.size());

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
		if (estados.isEmpty()) {
			return "en espera del Día 2";
		}
		for (String estado : PRECEDENCIA) {
			if (estados.contains(estado)) {
				return estado;
			}
		}
		return estados.iterator().next();
	}

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
		List<FilaGrupo> grupos = grupoRepository.buscarGrupos(List.of(id));
		List<FilaTandaVideo> filas = tandaRepository.buscarTandasYVideos(List.of(id));

		// Las tandas de cada grupo y los videos de cada tanda, en el orden en que llegan (ya ordenados por
		// la consulta: tandas por ordinal, 'Dia 1' antes que 'Dia 2'). Una tanda sin video llega en una
		// sola fila con el video en null.
		Map<Integer, List<Map<String, Object>>> tandasDe = new HashMap<>();
		Map<Integer, List<Map<String, Object>>> videosDe = new HashMap<>();
		for (FilaTandaVideo f : filas) {
			List<Map<String, Object>> videos = videosDe.get(f.getIdTanda());
			if (videos == null) {
				videos = new ArrayList<>();
				videosDe.put(f.getIdTanda(), videos);
				Map<String, Object> tanda = new LinkedHashMap<>();
				tanda.put("idTanda", f.getIdTanda());
				tanda.put("ordinal", f.getOrdinal().intValue());
				tanda.put("letra", String.valueOf((char) ('A' + f.getOrdinal().intValue() - 1)));
				tanda.put("nCilindros", f.getCilindros().intValue());
				tanda.put("videos", videos);
				tandasDe.computeIfAbsent(f.getIdGrupo(), k -> new ArrayList<>()).add(tanda);
			}
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
				video.put("subidoPor", fila.getCreadorId());
				videos.add(video);
			}
		}

		List<Map<String, Object>> arbol = new ArrayList<>();
		for (FilaGrupo g : grupos) {
			Map<String, Object> grupo = new LinkedHashMap<>();
			grupo.put("idGrupo", g.getIdGrupo());
			grupo.put("etiqueta", g.getEtiqueta());
			grupo.put("tipo", g.getTipo());
			grupo.put("tratamiento", g.getTratamiento());
			grupo.put("tandas", tandasDe.getOrDefault(g.getIdGrupo(), List.of()));
			arbol.add(grupo);
		}

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("idExperimento", fila.getIdExperimento());
		datos.put("nombre", fila.getNombre());
		datos.put("fecha", fila.getFecha());
		datos.put("notas", fila.getNotas() == null ? "" : fila.getNotas());
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
		String buscado = sinAcentos(q == null ? "" : q.trim());

		List<Map<String, Object>> resultado = new ArrayList<>();
		for (FilaGrupo g : grupoRepository.buscarGrupos(List.of(id))) {
			// Se busca en el mismo texto que ve el usuario: "etiqueta · tratamiento". Con q vacío entran todos.
			if (!sinAcentos(g.getEtiqueta() + " · " + g.getTratamiento()).contains(buscado)) {
				continue;
			}
			Map<String, Object> grupo = new LinkedHashMap<>();
			grupo.put("idGrupo", g.getIdGrupo());
			grupo.put("etiqueta", g.getEtiqueta());
			grupo.put("tipo", g.getTipo());
			grupo.put("tratamiento", g.getTratamiento());
			grupo.put("siguienteOrdinal", g.getSiguienteOrdinal().intValue());
			resultado.add(grupo);
		}
		return resultado;
	}

	// Minúsculas y sin acentos, en Java (java.text.Normalizer) y no con una extensión de PostgreSQL:
	// NFD separa cada letra de su acento y \p{M} borra los acentos.
	private static String sinAcentos(String texto) {
		return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	@Override
	@Transactional(readOnly = true)
	public Experimento buscar(Integer id) {
		return experimentoRepository.findById(id)
				.orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "El experimento " + id + " no existe"));
	}

	// Crea un experimento: solo nombre (único), fecha y notas opcionales, más el creador. Los grupos se
	// capturan después, al subir el primer video de cada uno (todavía no implementado).
	@Override
	@Transactional
	public Experimento crear(Experimento experimento, String idCreador) {
		// El creador es quien tiene la sesión abierta: se ignora el idInstitucional que venga en el cuerpo,
		// para que nadie cree experimentos a nombre de otro.
		experimento.setIdInstitucional(idCreador);

		if (!usuarioRepository.existsById(idCreador)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "El usuario " + idCreador + " no existe");
		}

		if (experimentoRepository.existsByNombre(experimento.getNombre())) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe un experimento con el nombre '" + experimento.getNombre() + "'");
		}

		// El id lo asigna PostgreSQL; si el JSON trae uno, se ignora para que siempre sea un INSERT.
		experimento.setIdExperimento(null);

		try {
			return experimentoRepository.saveAndFlush(experimento);
		} catch (DataIntegrityViolationException e) {
			// Por si otro usuario guardó el mismo nombre justo entre la revisión de arriba y este INSERT.
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe un experimento con el nombre '" + experimento.getNombre() + "'");
		}
	}

	// Actualiza nombre, fecha y notas. El creador no cambia aunque venga otro en el JSON.
	@Override
	@Transactional
	public Experimento actualizar(Integer id, Experimento datos) {
		Experimento actual = buscar(id);

		if (experimentoRepository.existsByNombreAndIdExperimentoNot(datos.getNombre(), id)) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe otro experimento con el nombre '" + datos.getNombre() + "'");
		}

		actual.setNombre(datos.getNombre());
		actual.setFecha(datos.getFecha());
		actual.setNotas(datos.getNotas());

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

		// Candado sobre la fila del experimento: una subida en curso (su grupo, tanda o video apuntan a él) espera a que
		// esta transacción termine, y no queda un video a medias dentro de un experimento que ya no existe.
		jdbc.queryForList("SELECT idexperimento FROM experimento WHERE idexperimento = ? FOR UPDATE", Integer.class, id);

		List<Integer> idsAnalisis = bloquearAnalisis(TANDAS_DEL_EXPERIMENTO, id,
				"No se puede eliminar mientras un análisis está procesando o en pausa");
		List<Integer> idsVideo = jdbc.queryForList("SELECT idvideo FROM video WHERE idtanda IN " + TANDAS_DEL_EXPERIMENTO,
				Integer.class, id);

		borrarDesdeLosVideos(TANDAS_DEL_EXPERIMENTO, id);
		jdbc.update("DELETE FROM grupo WHERE idexperimento = ?", id);
		reporteRepository.deleteByExperimentoIdExperimento(id);
		notificacionRepository.deleteByExperimentoIdExperimento(id);
		experimentoRepository.deleteById(id);

		borrarArchivosDespuesDelCommit(idsVideo, idsAnalisis);
	}

	// Elimina una tanda (regla nueva, parecida a R-08): su letra queda libre para volver a subirla. Todo en UNA
	// transacción, de las hojas hacia arriba; los archivos se borran DESPUÉS del commit (si fallara el borrado de uno, la
	// base ya quedó bien: solo se avisa en el log).
	@Override
	@Transactional
	public void eliminarTanda(Integer idExperimento, Integer idTanda, Authentication authentication) {
		Experimento experimento = buscar(idExperimento);
		List<Integer> delExperimento = jdbc.queryForList("""
				SELECT t.idtanda FROM tanda t JOIN grupo g ON g.idgrupo = t.idgrupo
				WHERE t.idtanda = ? AND g.idexperimento = ?
				""", Integer.class, idTanda, idExperimento);
		if (delExperimento.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "La tanda " + idTanda + " no existe en el experimento " + idExperimento);
		}

		// Igual que el experimento: solo quien lo creó o un Administrador.
		boolean esCreador = experimento.getIdInstitucional().equals(authentication.getName());
		boolean esAdministrador = authentication.getAuthorities().stream()
				.anyMatch(permiso -> permiso.getAuthority().equals("ROLE_ADMINISTRADOR"));
		if (!esCreador && !esAdministrador) {
			throw new ErrorApi(HttpStatus.FORBIDDEN, "Solo quien creó el experimento o un Administrador puede eliminar una tanda");
		}

		List<Integer> idsAnalisis = bloquearAnalisis(TANDA_UNICA, idTanda,
				"No se puede eliminar mientras su análisis está procesando o en pausa");
		List<Integer> idsVideo = jdbc.queryForList("SELECT idvideo FROM video WHERE idtanda IN " + TANDA_UNICA,
				Integer.class, idTanda);

		borrarDesdeLosVideos(TANDA_UNICA, idTanda);
		// Sus resultados cambian: un reporte guardado ya no los refleja.
		jdbc.update("DELETE FROM reporte WHERE idexperimento = ?", idExperimento);

		borrarArchivosDespuesDelCommit(idsVideo, idsAnalisis);
	}

	// Lo que comparten eliminar y eliminarTanda. "tandas" es una subconsulta con UN parámetro (?) que da las tandas a
	// borrar, y "parametro" es su valor (el idTanda o el idExperimento).
	private static final String TANDA_UNICA = "(SELECT idtanda FROM tanda WHERE idtanda = ?)";
	private static final String TANDAS_DEL_EXPERIMENTO =
			"(SELECT t.idtanda FROM tanda t JOIN grupo g ON g.idgrupo = t.idgrupo WHERE g.idexperimento = ?)";

	// FOR UPDATE: el análisis queda bloqueado hasta el commit. El worker toma el siguiente con FOR UPDATE SKIP LOCKED,
	// así que no lo agarra a la mitad; si ya lo tomó (procesando) o está en pausa, no se borra. En cola sí: sale de la cola.
	// Devuelve los ids de los análisis a borrar.
	private List<Integer> bloquearAnalisis(String tandas, Integer parametro, String mensajeSiOcupado) {
		List<Map<String, Object>> analisis = jdbc.queryForList(
				"SELECT idanalisis, estado FROM analisis WHERE idvideo IN (SELECT idvideo FROM video WHERE idtanda IN "
						+ tandas + ") FOR UPDATE",
				parametro);
		List<Integer> idsAnalisis = new ArrayList<>();
		for (Map<String, Object> a : analisis) {
			String estado = (String) a.get("estado");
			if ("procesando".equals(estado) || "en pausa".equals(estado)) {
				throw new ErrorApi(HttpStatus.CONFLICT, mensajeSiOcupado);
			}
			idsAnalisis.add(((Number) a.get("idanalisis")).intValue());
		}
		return idsAnalisis;
	}

	// Los borrados de las hojas hacia arriba, hasta las tandas inclusive (el grupo y lo de más arriba lo decide cada
	// método). Observacion cuelga de especimen y también de video: se borra antes que los dos.
	private void borrarDesdeLosVideos(String tandas, Integer parametro) {
		String videos = "(SELECT idvideo FROM video WHERE idtanda IN " + tandas + ")";
		jdbc.update("DELETE FROM segundo WHERE idobservacion IN (SELECT idobservacion FROM observacion WHERE idvideo IN "
				+ videos + ")", parametro);
		jdbc.update("DELETE FROM observacion WHERE idvideo IN " + videos, parametro);
		jdbc.update("DELETE FROM analisis WHERE idvideo IN " + videos, parametro);
		jdbc.update("DELETE FROM video WHERE idtanda IN " + tandas, parametro);
		jdbc.update("DELETE FROM especimen WHERE idtanda IN " + tandas, parametro);
		jdbc.update("DELETE FROM tanda WHERE idtanda IN " + tandas, parametro);
	}

	// Los archivos se borran DESPUÉS del commit: si fallara el borrado de uno, la base ya quedó bien (solo se avisa en el log).
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
			if (!carpeta.startsWith(raiz) || !Files.isDirectory(carpeta)) {
				continue;
			}
			try {
				Files.walkFileTree(carpeta, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path archivo, BasicFileAttributes atributos) throws IOException {
						Files.delete(archivo);
						return FileVisitResult.CONTINUE;
					}

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

	// Reporte de resultados con iText 5, igual que generarReporte de la práctica: se arma en memoria y se
	// devuelve como bytes, sin escribir ningún archivo. Va dentro de una transacción porque lee al creador
	// del experimento, que es una relación LAZY (se carga hasta que se usa).
	@Override
	@Transactional(readOnly = true)
	public ByteArrayInputStream generarReporte(Integer idExperimento) {
		Experimento experimento = experimentoRepository.findById(idExperimento).orElseThrow();
		Usuario creador = experimento.getUsuario();
		List<FilaResultado> filas = segundoRepository.buscarResultados(idExperimento);

		Document documento = new Document(PageSize.LETTER);
		ByteArrayOutputStream salida = new ByteArrayOutputStream();

		try {
			PdfWriter.getInstance(documento, salida);
			documento.open();

			Font letraTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
			Font letraDato = FontFactory.getFont(FontFactory.HELVETICA, 10);
			Font letraNota = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, BaseColor.DARK_GRAY);

			Paragraph titulo = new Paragraph("Reporte de resultados", letraTitulo);
			titulo.setAlignment(Element.ALIGN_CENTER);
			documento.add(titulo);
			documento.add(Chunk.NEWLINE);

			// Datos del experimento, un renglón cada uno.
			documento.add(new Paragraph("Experimento: " + experimento.getNombre(), letraDato));
			documento.add(new Paragraph("Fecha: " + experimento.getFecha(), letraDato));
			documento.add(new Paragraph("Creado por: " + creador.getNombre() + " " + creador.getApellidos()
					+ " (" + creador.getIdInstitucional() + ")", letraDato));
			if (experimento.getNotas() != null && !experimento.getNotas().isBlank()) {
				documento.add(new Paragraph("Notas: " + experimento.getNotas(), letraDato));
			}
			documento.add(new Paragraph("Generado: "
					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), letraDato));
			documento.add(Chunk.NEWLINE);

			if (filas.isEmpty()) {
				documento.add(new Paragraph(
						"Sin resultados: el experimento todavía no tiene análisis completados.", letraDato));
			} else {
				documento.add(armarTabla(filas));
				documento.add(new Paragraph("Segundos de cada conducta en cada minuto. Un minuto suma menos de "
						+ "60 s cuando hubo segundos sin conducta (el espécimen no se ve o el sistema no decidió).",
						letraNota));

				// Los videos analizados: cada tanda con el nombre del archivo como lo subió el usuario. Siempre
				// visible (no depende del modo desarrollador); los videos anteriores al 20-oct-2026 no tienen nombre.
				documento.add(Chunk.NEWLINE);
				Paragraph tituloVideos = new Paragraph("Videos analizados", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
				tituloVideos.setSpacingAfter(6);
				documento.add(tituloVideos);
				documento.add(armarTablaVideos(resultadosService.videosAnalizados(idExperimento)));

				// Cuánto tardó cada tanda analizada y en qué equipo, para comparar entre computadoras. Solo en modo
				// desarrollador: sin él, tiemposDelExperimento devuelve una lista vacía y la sección no sale.
				List<Map<String, Object>> tiempos = resultadosService.tiemposDelExperimento(idExperimento);
				if (!tiempos.isEmpty()) {
					documento.add(Chunk.NEWLINE);
					Paragraph subtitulo = new Paragraph("Tiempos de análisis", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
					subtitulo.setSpacingAfter(6);
					documento.add(subtitulo);
					documento.add(armarTablaTiempos(tiempos));
					documento.add(new Paragraph("Tiempo que el worker trabajó en el análisis (no incluye la espera en la "
							+ "cola). \"sin registro\": análisis de antes de que se midiera el tiempo.", letraNota));
				}
			}

			documento.close();
		} catch (DocumentException e) {
			// A diferencia de la práctica (que imprimía el error y devolvía el PDF a medias), aquí la excepción
			// sube hasta ManejadorErrores, que responde 500.
			log.error("No se pudo generar el PDF del experimento {}: {}", idExperimento, e.getMessage());
			throw new IllegalStateException("No se pudo generar el PDF", e);
		}

		return new ByteArrayInputStream(salida.toByteArray());
	}

	// La consulta trae una fila por conducta; la tabla lleva una fila por espécimen y minuto con las cuatro
	// conductas en columnas. Este método junta las cuatro filas de la consulta en una de la tabla.
	private PdfPTable armarTabla(List<FilaResultado> filas) throws DocumentException {
		PdfPTable tabla = new PdfPTable(6);
		tabla.setWidthPercentage(100);
		tabla.setWidths(new float[] {3.2f, 1.6f, 1f, 1.3f, 1.2f, 1.3f});

		// Encabezados, como en la práctica: Stream.of con los títulos y una celda con formato por cada uno.
		Font letraEncabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
		Stream.of("Espécimen", "Tiempo (min:seg)", "Nado", "Escalamiento", "Inmovilidad", "Conducta activa")
				.forEach(textoEncabezado -> {
					PdfPCell encabezado = new PdfPCell(new Phrase(textoEncabezado, letraEncabezado));
					encabezado.setHorizontalAlignment(Element.ALIGN_CENTER);
					encabezado.setBackgroundColor(BaseColor.LIGHT_GRAY);
					encabezado.setPadding(4);
					tabla.addCell(encabezado);
				});

		// Agrupa: la llave es "espécimen|minuto" y el valor, los segundos de cada conducta. LinkedHashMap
		// conserva el orden en que llegan las filas (el ORDER BY de la consulta).
		Map<String, Map<String, BigDecimal>> segundosPorFila = new LinkedHashMap<>();
		Map<String, String[]> etiquetasPorFila = new HashMap<>();
		for (FilaResultado f : filas) {
			char letraTanda = (char) ('A' + f.getOrdinal().intValue() - 1);
			String especimen = f.getGrupo() + " · Tanda " + letraTanda + " · Espécimen " + f.getCilindro();
			int minuto = f.getMinuto().intValue();
			String tiempo = (minuto - 1) + ":00-" + minuto + ":00";
			String llave = especimen + "|" + minuto;

			segundosPorFila.computeIfAbsent(llave, k -> new HashMap<>()).put(f.getConducta(), f.getSegundos());
			etiquetasPorFila.put(llave, new String[] {especimen, tiempo});
		}

		Font letraTexto = FontFactory.getFont(FontFactory.HELVETICA, 9);
		for (Map.Entry<String, Map<String, BigDecimal>> fila : segundosPorFila.entrySet()) {
			String[] etiquetas = etiquetasPorFila.get(fila.getKey());
			tabla.addCell(new PdfPCell(new Phrase(etiquetas[0], letraTexto)));
			tabla.addCell(celdaCentrada(etiquetas[1], letraTexto));
			for (String conducta : CONDUCTAS) {
				BigDecimal segundos = fila.getValue().getOrDefault(conducta, BigDecimal.ZERO);
				tabla.addCell(celdaCentrada(segundos.stripTrailingZeros().toPlainString(), letraTexto));
			}
		}
		return tabla;
	}

	// "Videos analizados": una fila por tanda con su grupo, el nombre del video como lo subió el usuario (o "nombre no
	// registrado" en los videos anteriores al 20-oct-2026) y su duración.
	private PdfPTable armarTablaVideos(List<Map<String, Object>> videos) throws DocumentException {
		PdfPTable tabla = new PdfPTable(3);
		tabla.setWidthPercentage(100);
		tabla.setWidths(new float[] {3.2f, 5f, 1.6f});

		Font letraEncabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
		Stream.of("Grupo · Tanda", "Video", "Duración (mm:ss)").forEach(textoEncabezado -> {
			PdfPCell encabezado = new PdfPCell(new Phrase(textoEncabezado, letraEncabezado));
			encabezado.setHorizontalAlignment(Element.ALIGN_CENTER);
			encabezado.setBackgroundColor(BaseColor.LIGHT_GRAY);
			encabezado.setPadding(4);
			tabla.addCell(encabezado);
		});

		Font letraTexto = FontFactory.getFont(FontFactory.HELVETICA, 9);
		for (Map<String, Object> video : videos) {
			tabla.addCell(new PdfPCell(new Phrase(video.get("grupo") + " · Tanda " + video.get("tanda"), letraTexto)));
			tabla.addCell(new PdfPCell(new Phrase(nombreDelVideo(video), letraTexto)));
			tabla.addCell(celdaCentrada(mmss((Number) video.get("duracionVideo")), letraTexto));
		}
		return tabla;
	}

	private static String nombreDelVideo(Map<String, Object> video) {
		Object nombre = video.get("nombreVideo");
		return nombre != null ? nombre.toString() : "nombre no registrado";
	}

	// Una fila por tanda analizada: grupo y tanda, duración del video, tiempo total, tiempo de cada etapa y equipo.
	private PdfPTable armarTablaTiempos(List<Map<String, Object>> tandas) throws DocumentException {
		PdfPTable tabla = new PdfPTable(7);
		tabla.setWidthPercentage(100);
		tabla.setWidths(new float[] {2.6f, 1.2f, 1.3f, 1.4f, 1.3f, 1.5f, 3.2f});

		Font letraEncabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
		Stream.of("Grupo · Tanda", "Video (mm:ss)", "Total (mm:ss)", "Preprocesamiento", "Cilindros",
				"Clasificación", "Equipo")
				.forEach(textoEncabezado -> {
					PdfPCell encabezado = new PdfPCell(new Phrase(textoEncabezado, letraEncabezado));
					encabezado.setHorizontalAlignment(Element.ALIGN_CENTER);
					encabezado.setBackgroundColor(BaseColor.LIGHT_GRAY);
					encabezado.setPadding(4);
					tabla.addCell(encabezado);
				});

		Font letraTexto = FontFactory.getFont(FontFactory.HELVETICA, 8);
		for (Map<String, Object> tanda : tandas) {
			// Además del grupo y la tanda, el nombre del video, para saber a qué archivo corresponde cada tiempo.
			tabla.addCell(new PdfPCell(new Phrase(tanda.get("grupo") + " · Tanda " + tanda.get("tanda") + "\n"
					+ nombreDelVideo(tanda), letraTexto)));
			tabla.addCell(celdaCentrada(mmss((Number) tanda.get("duracionVideo")), letraTexto));

			@SuppressWarnings("unchecked")
			Map<String, Object> tiempos = (Map<String, Object>) tanda.get("tiempos");
			if (tiempos == null) {
				// Análisis de antes de que el worker midiera el tiempo.
				tabla.addCell(celdaCentrada("sin registro", letraTexto));
				for (int i = 0; i < 4; i++) {
					tabla.addCell(celdaCentrada("—", letraTexto));
				}
				continue;
			}
			tabla.addCell(celdaCentrada(mmss((Number) tiempos.get("totalSegundos")), letraTexto));
			Map<String, Number> porEtapa = new HashMap<>();
			for (Object etapa : (List<?>) tiempos.get("etapas")) {
				Map<?, ?> e = (Map<?, ?>) etapa;
				porEtapa.put((String) e.get("etapa"), (Number) e.get("segundos"));
			}
			for (String etapa : List.of("preprocesamiento", "cilindros", "clasificacion")) {
				tabla.addCell(celdaCentrada(mmss(porEtapa.get(etapa)), letraTexto));
			}
			tabla.addCell(new PdfPCell(new Phrase(textoDelEquipo((Map<?, ?>) tiempos.get("equipo")), letraTexto)));
		}
		return tabla;
	}

	// "Laptop Erick · 8 núcleos": el nombre (o, si no tiene, el CPU) y los núcleos.
	private static String textoDelEquipo(Map<?, ?> equipo) {
		Object quien = equipo.get("nombre") != null ? equipo.get("nombre") : equipo.get("cpu");
		String texto = quien != null ? quien.toString() : "equipo desconocido";
		return equipo.get("nucleos") != null ? texto + " · " + equipo.get("nucleos") + " núcleos" : texto;
	}

	// mm:ss, o h:mm:ss si pasa de una hora; "—" si no hay dato.
	private static String mmss(Number segundos) {
		if (segundos == null) {
			return "—";
		}
		long total = Math.round(segundos.doubleValue());
		long horas = total / 3600;
		long minutos = (total % 3600) / 60;
		long resto = total % 60;
		return horas > 0 ? String.format("%d:%02d:%02d", horas, minutos, resto) : String.format("%d:%02d", minutos, resto);
	}

	private PdfPCell celdaCentrada(String texto, Font letra) {
		PdfPCell celda = new PdfPCell(new Phrase(texto, letra));
		celda.setHorizontalAlignment(Element.ALIGN_CENTER);
		return celda;
	}

}
