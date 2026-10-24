package com.ipn.mx.fst.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ipn.mx.fst.domain.valores.EstadoAnalisis;
import com.ipn.mx.fst.utilerias.ErrorApi;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Implementa PausaService. La pausa la escribe el worker (analisis/{id}/pausa.json); aquí se lee, se ajusta y se cancela.
@Service
public class PausaServiceImpl implements PausaService {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	// Las horas que se espera una respuesta antes de cancelar (la misma variable que lee el worker).
	@Value("${PAUSE_TIMEOUT_HOURS:72}")
	private int horasDePausa;

	@Autowired
	private JdbcTemplate jdbc;

	private Path raiz;

	@PostConstruct
	void prepararRaiz() {
		raiz = Path.of(raizConfigurada).toAbsolutePath().normalize();
	}

	@Override
	public Map<String, Object> pausa(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		exigirPausa(idExperimento, idAnalisis, authentication);
		JsonNode pausa = leerPausa(idAnalisis);
		OffsetDateTime horaPausa = OffsetDateTime.parse(pausa.path("hora_pausa").asText());
		long restantes = Duration.between(OffsetDateTime.now(), horaPausa.plusHours(horasDePausa)).getSeconds();

		List<Map<String, Object>> recuadros = new ArrayList<>();
		for (JsonNode r : pausa.path("recuadros")) {
			Map<String, Object> caja = new LinkedHashMap<>();
			caja.put("cilindro", r.path("cilindro").asInt());
			caja.put("x0", r.path("x0").asInt());
			caja.put("y0", r.path("y0").asInt());
			caja.put("x1", r.path("x1").asInt());
			caja.put("y1", r.path("y1").asInt());
			recuadros.add(caja);
		}
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("cilindro", pausa.path("cilindro").asInt());
		datos.put("desdeSegundo", pausa.path("desde_segundo").asInt());
		datos.put("segundosRestantes", Math.max(0, restantes));
		datos.put("ancho", pausa.path("ancho").asInt(0));
		datos.put("alto", pausa.path("alto").asInt(0));
		datos.put("recuadros", recuadros);
		return datos;
	}

	@Override
	public Resource imagen(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		exigirPausa(idExperimento, idAnalisis, authentication);
		Path jpg = carpeta(idAnalisis).resolve("pausa.jpg");
		if (!Files.isRegularFile(jpg)) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "La imagen de la pausa del análisis " + idAnalisis + " ya no existe");
		}
		return new FileSystemResource(jpg);
	}

	@Override
	@Transactional
	public void ajustar(Integer idExperimento, Integer idAnalisis, Map<String, Object> recuadro,
			Authentication authentication) {
		exigirPausa(idExperimento, idAnalisis, authentication);
		JsonNode pausa = leerPausa(idAnalisis).deepCopy();
		int cilindro = numero(recuadro, "cilindro");
		int x0 = numero(recuadro, "x0");
		int y0 = numero(recuadro, "y0");
		int x1 = numero(recuadro, "x1");
		int y1 = numero(recuadro, "y1");

		// El cilindro debe existir en la pausa, y la caja dentro de la imagen con x0 < x1 y y0 < y1.
		boolean existe = false;
		for (JsonNode r : pausa.path("recuadros")) {
			existe |= r.path("cilindro").asInt() == cilindro;
		}
		if (!existe) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El cilindro " + cilindro + " no está en la pausa");
		}
		int ancho = pausa.path("ancho").asInt(0);
		int alto = pausa.path("alto").asInt(0);
		if (x0 < 0 || y0 < 0 || x0 >= x1 || y0 >= y1 || (ancho > 0 && x1 > ancho) || (alto > 0 && y1 > alto)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST,
					"El recuadro no es válido: debe caer dentro de la imagen con x0 < x1 y y0 < y1");
		}

		// Regla (a): primero el archivo, con escritura atómica; después el estado, solo si sigue en pausa (regla b).
		ObjectNode ajuste = JSON.createObjectNode();
		ajuste.put("cilindro", cilindro);
		ajuste.put("x0", x0);
		ajuste.put("y0", y0);
		ajuste.put("x1", x1);
		ajuste.put("y1", y1);
		ajuste.put("hora", OffsetDateTime.now().toString());
		((ObjectNode) pausa).set("ajuste", ajuste);
		escribirAtomico(idAnalisis, pausa);

		// Vuelve a 'en cola' sin tocar fechaAnalisis: conserva su lugar en la cola (D-79).
		int cambios = jdbc.update("UPDATE analisis SET estado = ? WHERE idanalisis = ? AND estado = ?",
				EstadoAnalisis.EN_COLA.valor(), idAnalisis, EstadoAnalisis.EN_PAUSA.valor());
		if (cambios == 0) {
			// Ya no estaba en pausa: se deshace el ajuste que se acaba de escribir, para que el worker no aplique
			// una corrección de un análisis que no está en pausa (409).
			escribirAtomico(idAnalisis, quitarAjuste(pausa));
			throw new ErrorApi(HttpStatus.CONFLICT, "El análisis ya no está en pausa");
		}
	}

	@Override
	@Transactional
	public void cancelar(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		exigirPausa(idExperimento, idAnalisis, authentication);
		int cambios = jdbc.update("UPDATE analisis SET estado = ? WHERE idanalisis = ? AND estado = ?",
				EstadoAnalisis.CANCELADO.valor(), idAnalisis, EstadoAnalisis.EN_PAUSA.valor());
		if (cambios == 0) {
			throw new ErrorApi(HttpStatus.CONFLICT, "El análisis ya no está en pausa");
		}
	}

	// 404 si el análisis no es de ese experimento; 403 si quien pide no subió el video ni es Administrador (D-107); 409
	// si el análisis ya no está en pausa.
	private void exigirPausa(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		List<String[]> filas = jdbc.query("""
				SELECT a.estado, x.idinstitucional
				FROM analisis a
				JOIN video v       ON v.idvideo = a.idvideo
				JOIN tanda t       ON t.idtanda = v.idtanda
				JOIN grupo g       ON g.idgrupo = t.idgrupo
				JOIN experimento x ON x.idexperimento = g.idexperimento
				WHERE a.idanalisis = ? AND g.idexperimento = ?
				""", (rs, n) -> new String[] {rs.getString("estado"), rs.getString("idinstitucional")},
				idAnalisis, idExperimento);
		if (filas.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND,
					"El análisis " + idAnalisis + " no existe en el experimento " + idExperimento);
		}
		boolean esAdministrador = authentication.getAuthorities().stream()
				.anyMatch(permiso -> permiso.getAuthority().equals("ROLE_ADMINISTRADOR"));
		boolean esCreador = filas.get(0)[1].equals(authentication.getName());
		if (!esCreador && !esAdministrador) {
			throw new ErrorApi(HttpStatus.FORBIDDEN,
					"Solo quien subió el video o un Administrador puede atender la pausa");
		}
		if (!EstadoAnalisis.EN_PAUSA.es(filas.get(0)[0])) {
			throw new ErrorApi(HttpStatus.CONFLICT, "El análisis no está en pausa");
		}
	}

	private Path carpeta(Integer idAnalisis) {
		return raiz.resolve("analisis").resolve(String.valueOf(idAnalisis));
	}

	private JsonNode leerPausa(Integer idAnalisis) {
		Path archivo = carpeta(idAnalisis).resolve("pausa.json");
		try {
			return JSON.readTree(Files.readString(archivo));
		} catch (IOException | DateTimeParseException e) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "La pausa del análisis " + idAnalisis + " ya no existe");
		}
	}

	private void escribirAtomico(Integer idAnalisis, JsonNode contenido) {
		Path destino = carpeta(idAnalisis).resolve("pausa.json");
		Path temporal = carpeta(idAnalisis).resolve("pausa.json.tmp");
		try {
			Files.writeString(temporal, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(contenido));
			Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el ajuste de la pausa");
		}
	}

	// La pausa tal como estaba antes del ajuste: sin el campo "ajuste" (o con ajuste null, como la escribe el worker).
	private static JsonNode quitarAjuste(JsonNode pausa) {
		ObjectNode copia = ((ObjectNode) pausa).deepCopy();
		copia.putNull("ajuste");
		return copia;
	}

	private static int numero(Map<String, Object> cuerpo, String clave) {
		Object valor = cuerpo.get(clave);
		if (!(valor instanceof Number numero)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "Falta " + clave + " en el recuadro");
		}
		return numero.intValue();
	}
}
