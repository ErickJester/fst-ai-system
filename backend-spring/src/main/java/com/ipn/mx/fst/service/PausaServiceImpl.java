// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// Las clases de Jackson 2 para leer y escribir JSON como un árbol de nodos.
// JsonNode: un nodo cualquiera (solo lectura). ObjectNode: un nodo de objeto, que sí se puede modificar.
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
// El enum de los estados del análisis.
import com.ipn.mx.fst.domain.valores.EstadoAnalisis;
import com.ipn.mx.fst.utilerias.ErrorApi;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
// Files, Path y StandardCopyOption: leer y escribir archivos, y renombrar de forma atómica.
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
// Duration y OffsetDateTime: calcular cuánto falta para que venza la pausa.
import java.time.Duration;
import java.time.OffsetDateTime;
// DateTimeParseException: si la hora del archivo no se puede leer.
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
// FileSystemResource: un archivo del disco que Spring vuelca a la respuesta.
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Implementa PausaService. La pausa la escribe el worker (analisis/{id}/pausa.json); aquí se lee, se ajusta y se cancela.
//
// El contrato completo, con qué es la pausa y por qué existe, está en PausaService.
// Lo que distingue a este archivo del resto del backend: el estado NO está en la base, sino en un archivo
// JSON del volumen compartido. La base solo guarda que el análisis está 'en pausa'. Eso obliga a coordinar
// DOS almacenes que no comparten transacción, y de ahí viene el orden de los pasos que se repite abajo:
// primero el archivo (que se puede deshacer a mano) y después el estado (con su condición incluida).
@Service
public class PausaServiceImpl implements PausaService {

	// El lector y escritor de JSON. Fíjate en el paquete: com.fasterxml (Jackson 2), no tools.jackson
	// (Jackson 3) como en Ffprobe. Spring Boot 4 trae las dos y aquí se usa la del ObjectNode mutable.
	private static final ObjectMapper JSON = new ObjectMapper();

	@Value("${app.archivos.raiz}")
	private String raizConfigurada;

	// Las horas que se espera una respuesta antes de cancelar (la misma variable que lee el worker).
	//
	// "La misma variable" es importante: si el backend y el worker tuvieran plazos distintos, la pantalla
	// enseñaría un tiempo restante que no coincide con cuándo se cancela de verdad.
	@Value("${PAUSE_TIMEOUT_HOURS:72}")
	private int horasDePausa;

	// Píxeles que un borde tiene que moverse, como mínimo, para contar como ajuste (app.pausa.umbral-recuadro-px).
	@Value("${app.pausa.umbral-recuadro-px:3}")
	private int umbralRecuadro;

	@Autowired
	private JdbcTemplate jdbc;

	private Path raiz;

	// Se calcula al terminar de construir el objeto, cuando el @Value ya está puesto.
	@PostConstruct
	void prepararRaiz() {
		raiz = Path.of(raizConfigurada).toAbsolutePath().normalize();
	}

	@Override
	public Map<String, Object> pausa(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		// Comprueba de una vez el 404, el 403 y el 409. Todos los métodos empiezan igual.
		exigirPausa(idExperimento, idAnalisis, authentication);
		JsonNode pausa = leerPausa(idAnalisis);
		OffsetDateTime horaPausa = OffsetDateTime.parse(pausa.path("hora_pausa").asText());
		// Cuánto falta para que venza: desde ahora hasta la hora de la pausa más el plazo.
		// Puede salir negativo si ya venció y el worker todavía no la canceló; abajo se corta en 0.
		long restantes = Duration.between(OffsetDateTime.now(), horaPausa.plusHours(horasDePausa)).getSeconds();

		// Los recuadros se copian uno a uno en lugar de reenviar el JSON del archivo tal cual: así la API
		// tiene su propia forma y un cambio en el formato del worker no se filtra a la pantalla.
		// Fíjate también en el cambio de nombres: desde_segundo del archivo pasa a desdeSegundo en el JSON.
		List<Map<String, Object>> recuadros = new ArrayList<>();
		for (JsonNode r : pausa.path("recuadros")) {
			Map<String, Object> caja = new LinkedHashMap<>();
			caja.put("cilindro", r.path("cilindro").asInt());
			// Las cuatro esquinas del rectángulo: (x0,y0) arriba a la izquierda, (x1,y1) abajo a la derecha.
			caja.put("x0", r.path("x0").asInt());
			caja.put("y0", r.path("y0").asInt());
			caja.put("x1", r.path("x1").asInt());
			caja.put("y1", r.path("y1").asInt());
			recuadros.add(caja);
		}
		Map<String, Object> datos = new LinkedHashMap<>();
		// Qué cilindro es el dudoso y desde qué segundo se atascó.
		datos.put("cilindro", pausa.path("cilindro").asInt());
		datos.put("desdeSegundo", pausa.path("desde_segundo").asInt());
		// Math.max(0, ...) evita enseñar un tiempo negativo si la pausa ya venció.
		datos.put("segundosRestantes", Math.max(0, restantes));
		// El tamaño del video: la pantalla lo necesita para dibujar el recuadro a escala sobre la imagen.
		// asInt(0) da 0 si la clave falta, en archivos de versiones anteriores.
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
		// FileSystemResource y no leer los bytes: Spring lo vuelca sin cargarlo entero en memoria.
		return new FileSystemResource(jpg);
	}

	@Override
	@Transactional
	public void ajustar(Integer idExperimento, Integer idAnalisis, Map<String, Object> recuadro,
			Authentication authentication) {
		exigirPausa(idExperimento, idAnalisis, authentication);
		// deepCopy: una copia completa del árbol, para modificarla sin tocar la original.
		JsonNode pausa = leerPausa(idAnalisis).deepCopy();
		// numero() comprueba que cada campo venga y sea numérico; si no, lanza un 400 con su mensaje.
		int cilindro = numero(recuadro, "cilindro");
		int x0 = numero(recuadro, "x0");
		int y0 = numero(recuadro, "y0");
		int x1 = numero(recuadro, "x1");
		int y1 = numero(recuadro, "y1");

		// El cilindro debe existir en la pausa, y la caja dentro de la imagen con x0 < x1 y y0 < y1.
		JsonNode automatico = recuadroDe(pausa, cilindro);
		if (automatico == null) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "El cilindro " + cilindro + " no está en la pausa");
		}
		int ancho = pausa.path("ancho").asInt(0);
		int alto = pausa.path("alto").asInt(0);
		// Las condiciones, una a una: nada negativo; x0 estrictamente menor que x1 y y0 que y1 (si no, el
		// rectángulo estaría invertido o sería una línea); y los dos bordes derechos dentro de la imagen.
		// Los "ancho > 0" y "alto > 0" se saltan esa última comprobación en los archivos viejos que no
		// traen el tamaño, en vez de rechazarlos todos.
		if (x0 < 0 || y0 < 0 || x0 >= x1 || y0 >= y1 || (ancho > 0 && x1 > ancho) || (alto > 0 && y1 > alto)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST,
					"El recuadro no es válido: debe caer dentro de la imagen con x0 < x1 y y0 < y1");
		}

		// «Recuadro casi igual»: si ningún borde se movió más del umbral, no es un ajuste (422).
		//
		// Evita un caso real: la persona arrastra el recuadro un par de píxeles sin querer, cree que lo
		// corrigió y el worker se vuelve a atascar en el mismo sitio. El mensaje la empuja a lo que de
		// verdad resuelve: mover el recuadro de verdad, o aceptar el automático.
		if (movimientoMayor(automatico, x0, y0, x1, y1) <= umbralRecuadro) {
			throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY,
					"El recuadro casi no cambió: ningún borde se movió más de " + umbralRecuadro
							+ " px. Muévelo sobre el tubo o elige «El recuadro está bien, continuar».");
		}

		// Regla (a): primero el archivo, con escritura atómica; después el estado, solo si sigue en pausa (regla b).
		//
		// El orden importa por lo que puede fallar en medio. Si se cambiara primero el estado, el worker
		// podría tomar el análisis ANTES de que el ajuste estuviera escrito y volver a atascarse igual.
		// Escribiendo primero el archivo, lo peor que puede pasar es un ajuste escrito que no se usa, y de
		// eso se encarga el deshacer de abajo.
		ObjectNode ajuste = JSON.createObjectNode();
		ajuste.put("cilindro", cilindro);
		ajuste.put("x0", x0);
		ajuste.put("y0", y0);
		ajuste.put("x1", x1);
		ajuste.put("y1", y1);
		ajuste.put("hora", OffsetDateTime.now().toString());
		// El ajuste se AÑADE al archivo; los recuadros automáticos se quedan. Así se conserva en qué se
		// equivocó el modelo, que es lo que sirve para reentrenarlo.
		((ObjectNode) pausa).set("ajuste", ajuste);
		escribirAtomico(idAnalisis, pausa);

		// Vuelve a 'en cola' sin tocar fechaAnalisis: conserva su lugar en la cola (D-79).
		//
		// El "AND estado = 'en pausa'" del WHERE es la regla (b): la condición va DENTRO del UPDATE en vez
		// de comprobarse antes. Entre leer y escribir cabe otra petición, y así la base lo resuelve de un
		// solo golpe. update devuelve cuántas filas cambió: 0 significa que la condición ya no se cumplía.
		int cambios = jdbc.update("UPDATE analisis SET estado = ? WHERE idanalisis = ? AND estado = ?",
				EstadoAnalisis.EN_COLA.valor(), idAnalisis, EstadoAnalisis.EN_PAUSA.valor());
		if (cambios == 0) {
			// Ya no estaba en pausa: se deshace el ajuste que se acaba de escribir, para que el worker no aplique
			// una corrección de un análisis que no está en pausa (409).
			//
			// Es el "deshacer a mano" del que no puede encargarse la transacción: la base sabe revertir,
			// el sistema de archivos no.
			escribirAtomico(idAnalisis, quitarAjuste(pausa));
			throw new ErrorApi(HttpStatus.CONFLICT, "El análisis ya no está en pausa");
		}
	}

	// «El recuadro está bien, continuar»: el usuario acepta la racha tal como está. Mismo orden que ajustar: primero el
	// archivo (continuar), después el estado (en cola), conservando fechaAnalisis.
	@Override
	@Transactional
	public void continuar(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		exigirPausa(idExperimento, idAnalisis, authentication);
		ObjectNode pausa = leerPausa(idAnalisis).deepCopy();
		ObjectNode continuar = JSON.createObjectNode();
		continuar.put("hora", OffsetDateTime.now().toString());
		// Esta marca es la que impide que el worker vuelva a pausar en el mismo punto: sin ella llegaría
		// al mismo sitio dudoso, pausaría otra vez y el análisis no avanzaría nunca.
		pausa.set("continuar", continuar);
		// Se limpia cualquier ajuste anterior: aceptar el recuadro automático y tener un ajuste a la vez
		// sería contradictorio, y el worker no sabría a cuál hacer caso.
		pausa.putNull("ajuste");
		escribirAtomico(idAnalisis, pausa);

		// La misma escritura condicional que en ajustar.
		int cambios = jdbc.update("UPDATE analisis SET estado = ? WHERE idanalisis = ? AND estado = ?",
				EstadoAnalisis.EN_COLA.valor(), idAnalisis, EstadoAnalisis.EN_PAUSA.valor());
		if (cambios == 0) {
			// Ya no estaba en pausa: se quita «continuar» para que el worker no acepte una racha por nada.
			// remove y no putNull, al revés que con el ajuste: así el campo desaparece del archivo.
			ObjectNode deshecha = pausa.deepCopy();
			deshecha.remove("continuar");
			escribirAtomico(idAnalisis, deshecha);
			throw new ErrorApi(HttpStatus.CONFLICT, "El análisis ya no está en pausa");
		}
	}

	@Override
	@Transactional
	public void cancelar(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		exigirPausa(idExperimento, idAnalisis, authentication);
		// Cancelar es el más simple de los tres: no toca el archivo, porque no hay nada que decirle al
		// worker. Solo cambia el estado, con la misma condición de que siga en pausa.
		// La fila del análisis NO se borra: queda constancia de que se intentó (ver la entidad Analisis).
		int cambios = jdbc.update("UPDATE analisis SET estado = ? WHERE idanalisis = ? AND estado = ?",
				EstadoAnalisis.CANCELADO.valor(), idAnalisis, EstadoAnalisis.EN_PAUSA.valor());
		if (cambios == 0) {
			throw new ErrorApi(HttpStatus.CONFLICT, "El análisis ya no está en pausa");
		}
	}

	// 404 si el análisis no es de ese experimento; 403 si quien pide no subió el video ni es Administrador (D-107); 409
	// si el análisis ya no está en pausa.
	//
	// Las tres comprobaciones en un solo sitio, llamado al principio de los cuatro métodos públicos: así
	// no se puede olvidar ninguna al añadir un método nuevo.
	// El ORDEN es observable desde fuera y por eso está fijado: primero existencia, luego permiso, luego
	// estado. Comprobar el estado antes que el permiso le diría a un desconocido si el análisis está en
	// pausa, que es información que no le corresponde.
	private void exigirPausa(Integer idExperimento, Integer idAnalisis, Authentication authentication) {
		// Los cuatro JOIN suben del análisis al experimento. El WHERE lleva las DOS condiciones: que el
		// análisis exista Y que sea de ese experimento. Sin la segunda, cambiando el número de la ruta se
		// podría husmear en los análisis de otro experimento.
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
		// El rol se lee de los permisos que armó ConvertidorJwt a partir de la base, no del token.
		// El texto lleva el prefijo ROLE_, que es la convención de Spring Security.
		boolean esAdministrador = authentication.getAuthorities().stream()
				.anyMatch(permiso -> permiso.getAuthority().equals("ROLE_ADMINISTRADOR"));
		// filas.get(0)[1] es el idinstitucional del creador del experimento.
		boolean esCreador = filas.get(0)[1].equals(authentication.getName());
		if (!esCreador && !esAdministrador) {
			throw new ErrorApi(HttpStatus.FORBIDDEN,
					"Solo quien subió el video o un Administrador puede atender la pausa");
		}
		// es(...) es el método de ValorDeBase: compara el texto crudo de la base con el del enum sin
		// tronar si viniera null o un valor desconocido.
		if (!EstadoAnalisis.EN_PAUSA.es(filas.get(0)[0])) {
			throw new ErrorApi(HttpStatus.CONFLICT, "El análisis no está en pausa");
		}
	}

	// La carpeta del análisis dentro del volumen: analisis/{idAnalisis}/.
	// La ruta se arma solo con el id, nunca con texto del usuario.
	private Path carpeta(Integer idAnalisis) {
		return raiz.resolve("analisis").resolve(String.valueOf(idAnalisis));
	}

	private JsonNode leerPausa(Integer idAnalisis) {
		Path archivo = carpeta(idAnalisis).resolve("pausa.json");
		try {
			return JSON.readTree(Files.readString(archivo));
		} catch (IOException | DateTimeParseException e) {
			// Las dos causas (no está el archivo, o su contenido no se entiende) dan el mismo 404: para
			// quien pregunta, una pausa ilegible y una que no existe son lo mismo.
			throw new ErrorApi(HttpStatus.NOT_FOUND, "La pausa del análisis " + idAnalisis + " ya no existe");
		}
	}

	// El mismo patrón de escritura atómica que ReporteAlmacen: a un temporal y luego renombrar.
	// Aquí es todavía más necesario, porque el worker puede estar leyendo este archivo al mismo tiempo:
	// con el renombrado, o lee el contenido viejo entero o el nuevo entero, nunca uno a medias.
	// El temporal va en la MISMA carpeta para que el renombrado sea atómico de verdad.
	private void escribirAtomico(Integer idAnalisis, JsonNode contenido) {
		Path destino = carpeta(idAnalisis).resolve("pausa.json");
		Path temporal = carpeta(idAnalisis).resolve("pausa.json.tmp");
		try {
			// writerWithDefaultPrettyPrinter deja el JSON indentado: el archivo lo lee también una persona
			// cuando algo va mal.
			Files.writeString(temporal, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(contenido));
			Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			// 500: esto sí es un fallo del servidor, no del usuario.
			throw new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el ajuste de la pausa");
		}
	}

	// La pausa tal como estaba antes del ajuste: sin el campo "ajuste" (o con ajuste null, como la escribe el worker).
	// El mayor movimiento (en px) de cualquier borde entre el recuadro automático y el que mandó la persona.
	//
	// (el primer comentario quedó arriba del método equivocado; describe a quitarAjuste, que está más abajo)
	// Se mide el borde que MÁS se movió, no la suma ni el promedio: basta con que una esquina se haya
	// desplazado de verdad para que el ajuste cuente. Math.abs quita el signo, porque da igual hacia qué
	// lado se movió. Los dos Math.max anidados comparan los cuatro valores de dos en dos.
	// Es static y no private para que la prueba (PausaServiceUmbralTest) pueda llamarlo sin levantar nada.
	static int movimientoMayor(JsonNode automatico, int x0, int y0, int x1, int y1) {
		return Math.max(
				Math.max(Math.abs(x0 - automatico.path("x0").asInt()), Math.abs(y0 - automatico.path("y0").asInt())),
				Math.max(Math.abs(x1 - automatico.path("x1").asInt()), Math.abs(y1 - automatico.path("y1").asInt())));
	}

	// El recuadro automático de ese cilindro dentro de pausa.json, o null si la pausa no lo trae.
	// Búsqueda lineal sobre una lista de dos a cuatro elementos: más rápida que cualquier mapa y sin nada
	// que construir ni mantener.
	static JsonNode recuadroDe(JsonNode pausa, int cilindro) {
		for (JsonNode r : pausa.path("recuadros")) {
			if (r.path("cilindro").asInt() == cilindro) {
				return r;
			}
		}
		return null;
	}

	// Devuelve una copia de la pausa con el ajuste anulado. Se usa para deshacer cuando el estado ya había
	// cambiado. putNull y no remove: así el archivo conserva la misma forma que escribe el worker, con el
	// campo presente y en null.
	private static JsonNode quitarAjuste(JsonNode pausa) {
		ObjectNode copia = ((ObjectNode) pausa).deepCopy();
		copia.putNull("ajuste");
		return copia;
	}

	// Saca un número del cuerpo o lanza un 400 con el nombre del campo que falta.
	// "instanceof Number numero" es el instanceof con patrón: comprueba el tipo y declara la variable de
	// una vez. Number cubre Integer y Double, porque el JSON puede traer 10 o 10.0 y los dos valen.
	// El ! de delante atrapa los tres casos malos juntos: que falte la clave (get da null, y null no es
	// instancia de nada), que venga con otro tipo, o que venga un texto.
	private static int numero(Map<String, Object> cuerpo, String clave) {
		Object valor = cuerpo.get(clave);
		if (!(valor instanceof Number numero)) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "Falta " + clave + " en el recuadro");
		}
		// intValue() trunca un decimal: las coordenadas de la pantalla pueden llegar como 150.0.
		return numero.intValue();
	}
}
