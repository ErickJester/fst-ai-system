package com.ipn.mx.fst.service;

import com.ipn.mx.fst.domain.Experimento;
import com.ipn.mx.fst.domain.Usuario;
import com.ipn.mx.fst.repository.ExperimentoRepository;
import com.ipn.mx.fst.repository.FilaResultado;
import com.ipn.mx.fst.repository.GrupoRepository;
import com.ipn.mx.fst.repository.NotificacionRepository;
import com.ipn.mx.fst.repository.ReporteRepository;
import com.ipn.mx.fst.repository.SegundoRepository;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperimentoServiceImpl implements ExperimentoService {

	private static final Logger log = LoggerFactory.getLogger(ExperimentoServiceImpl.class);

	// Las cuatro conductas en el orden de las columnas del reporte (sección 7 de la guía).
	private static final String[] CONDUCTAS = {"nado activo", "escalamiento", "inmovilidad", "conducta activa"};

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

	@Override
	@Transactional(readOnly = true)
	public List<Experimento> findAll() {
		return experimentoRepository.findAll(Sort.by("idExperimento"));
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
	public Experimento crear(Experimento experimento) {
		// idInstitucional no lleva @NotBlank en la entidad (el PUT no lo pide), así que se revisa aquí.
		// Se quitará cuando el creador salga del token.
		if (experimento.getIdInstitucional() == null || experimento.getIdInstitucional().isBlank()) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El campo 'idInstitucional' no debe estar vacío");
		}

		Usuario creador = usuarioRepository.findById(experimento.getIdInstitucional())
				.orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND,
						"El usuario " + experimento.getIdInstitucional() + " no existe"));

		if (experimentoRepository.existsByNombre(experimento.getNombre())) {
			throw new ErrorApi(HttpStatus.CONFLICT, "Ya existe un experimento con el nombre '" + experimento.getNombre() + "'");
		}

		// El id lo asigna PostgreSQL; si el JSON trae uno, se ignora para que siempre sea un INSERT.
		experimento.setIdExperimento(null);
		// La columna se escribe con idInstitucional; este campo (insertable = false) solo deja a mano al
		// creador ya cargado para que el controller lea su correo sin otra consulta.
		experimento.setUsuario(creador);

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

	// Por ahora solo se borran los experimentos sin grupos: borrar grupos, tandas, videos y resultados (de las
	// hojas hacia arriba) llega con la carga de video.
	// Todas las llaves foráneas son ON DELETE NO ACTION: la base no borra en cascada. Por eso primero se
	// borra lo que cuelga del experimento (reportes y notificaciones) y al final el experimento, todo en
	// una sola transacción: si algo falla, no se borra nada.
	@Override
	@Transactional
	public void eliminar(Integer id) {
		buscar(id);

		if (grupoRepository.existsByExperimentoIdExperimento(id)) {
			throw new ErrorApi(HttpStatus.CONFLICT,
					"El experimento " + id + " tiene grupos y todavía no se puede eliminar");
		}

		reporteRepository.deleteByExperimentoIdExperimento(id);
		notificacionRepository.deleteByExperimentoIdExperimento(id);
		experimentoRepository.deleteById(id);
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

	private PdfPCell celdaCentrada(String texto, Font letra) {
		PdfPCell celda = new PdfPCell(new Phrase(texto, letra));
		celda.setHorizontalAlignment(Element.ALIGN_CENTER);
		return celda;
	}

}
