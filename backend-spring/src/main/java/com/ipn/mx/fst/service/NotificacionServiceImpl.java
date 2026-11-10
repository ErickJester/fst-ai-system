// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// ErrorApi: la excepción con el código HTTP dentro.
import com.ipn.mx.fst.utilerias.ErrorApi;
// OffsetDateTime: fecha y hora con zona. ZoneId: una zona horaria concreta.
import java.time.OffsetDateTime;
import java.time.ZoneId;
// LinkedHashMap: mapa que conserva el orden de inserción, para que el JSON salga ordenado.
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
// JdbcTemplate: SQL directo, sin JPA.
import org.springframework.jdbc.core.JdbcTemplate;
// RowMapper: convierte una fila del resultado en un objeto de Java.
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// La implementación de NotificacionService, donde está el contrato completo de los tres métodos.
//
// Fíjate en que aquí NO se usa JPA ni NotificacionRepository: todo va con JdbcTemplate y SQL a mano.
// Tiene sentido porque lo que se devuelve es un mapa plano para el JSON, no entidades con relaciones: al
// no haber que navegar nada, cargar la entidad entera y traducirla después sería trabajo de más.
@Service
public class NotificacionServiceImpl implements NotificacionService {

	// La zona del laboratorio. Fijarla, en vez de usar la del servidor, hace que la hora que ve el usuario
	// sea siempre la misma, corra el contenedor donde corra.
	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

	// La campanita muestra solo las más recientes: sin tope, una cuenta con meses de uso traería miles de
	// avisos en cada consulta.
	private static final int TOPE = 100;

	// Las columnas que ven la lista y el PATCH, en el mismo orden para el mismo RowMapper.
	//
	// Es lo que permite reusar A_MAPA en las dos consultas de abajo. Si una seleccionara columnas
	// distintas, el conversor fallaría al buscar una que no está. Teniéndolas en una constante, no pueden
	// separarse por descuido.
	private static final String COLUMNAS = "idnotificacion, tipo, mensaje, leido, fechacreacion, idexperimento";

	@Autowired
	private JdbcTemplate jdbc;

	// {idNotificacion, tipo, mensaje, leido, fechaCreacion, idExperimento}. La fecha va en la zona de México,
	// como fechaAnalisis; idExperimento es null en las que no son de un experimento (la alerta de disco).
	//
	// Un RowMapper es una función que recibe una fila (rs) y su número, y devuelve el objeto. Se escribe
	// como lambda y se guarda en una constante: se crea una vez y se reutiliza en las dos consultas.
	// Fíjate en el cambio de nombres: la base usa minúsculas pegadas (idnotificacion) y el JSON usa
	// mayúscula intercalada (idNotificacion). Esta es la frontera donde se traduce.
	private static final RowMapper<Map<String, Object>> A_MAPA = (rs, fila) -> {
		Map<String, Object> notificacion = new LinkedHashMap<>();
		notificacion.put("idNotificacion", rs.getInt("idnotificacion"));
		// tipo y mensaje salen como texto crudo; el tipo es un valor del dominio tipo_notificacion.
		notificacion.put("tipo", rs.getString("tipo"));
		notificacion.put("mensaje", rs.getString("mensaje"));
		notificacion.put("leido", rs.getBoolean("leido"));
		// atZoneSameInstant traduce el MISMO instante a otra zona: no cambia el momento, cambia cómo se
		// escribe. Así, un aviso creado a las 15:00 de México se lee como las 15:00 aunque el servidor
		// esté en UTC.
		notificacion.put("fechaCreacion", rs.getObject("fechacreacion", OffsetDateTime.class)
				.atZoneSameInstant(ZONA).toOffsetDateTime());
		// getObject(..., Integer.class) y no getInt: getInt devolvería 0 para un NULL, y aquí el null
		// significa "esta notificación no es de ningún experimento" (la alerta de disco). Un 0 sería un
		// id de experimento falso.
		notificacion.put("idExperimento", rs.getObject("idexperimento", Integer.class));
		return notificacion;
	};

	@Override
	// readOnly = true: no escribe nada; se le avisa a la base para que sea más barata.
	@Transactional(readOnly = true)
	public List<Map<String, Object>> listar(String idInstitucional) {
		// El WHERE por idinstitucional es lo que impide ver las notificaciones de otro. Ese valor sale del
		// token, no de la URL (ver NotificacionController).
		// El doble criterio de orden desempata las creadas en el mismo instante, que es posible porque el
		// worker inserta varias de golpe. Sin el segundo, su orden sería arbitrario y podría cambiar entre
		// dos cargas de la pantalla.
		return jdbc.query("SELECT " + COLUMNAS + " FROM notificacion WHERE idinstitucional = ? "
				+ "ORDER BY fechacreacion DESC, idnotificacion DESC LIMIT ?", A_MAPA, idInstitucional, TOPE);
	}

	// El total de no leídas de la cuenta, sin el tope de listar: la campanita lo muestra aunque la lista tenga cien.
	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> contarNoLeidas(String idInstitucional) {
		// "NOT leido" aprovecha que la columna es booleana: no hace falta compararla con true.
		Long noLeidas = jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE idinstitucional = ? AND NOT leido",
				Long.class, idInstitucional);
		Map<String, Object> datos = new LinkedHashMap<>();
		// El null nunca ocurre con un COUNT(*), pero el tipo lo permite y así no hay forma de tropezar.
		datos.put("noLeidas", noLeidas == null ? 0L : noLeidas);
		return datos;
	}

	// Un solo UPDATE ... RETURNING: solo toca la fila si es de esta cuenta, y devuelve la notificación ya
	// actualizada. Si no devuelve nada, la notificación no existe o es de otra cuenta, y en los dos casos es
	// el mismo 404, para no revelar que existe.
	//
	// Es el patrón que evita la condición de carrera de "leer, comprobar y escribir". La alternativa
	// (buscar la notificación, ver si es de esta cuenta, y luego actualizarla) deja un hueco entre la
	// comprobación y la escritura. Metiendo la condición dentro del propio UPDATE, la base lo resuelve
	// en un solo paso atómico.
	// RETURNING es de PostgreSQL: hace que un UPDATE devuelva las filas que cambió, como si fuera un
	// SELECT. Ahorra la segunda consulta para leer el resultado.
	@Override
	// Sin readOnly: este sí escribe.
	@Transactional
	public Map<String, Object> marcar(String idInstitucional, Integer idNotificacion, boolean leido) {
		// jdbc.query y no jdbc.update, aunque sea un UPDATE: con RETURNING devuelve filas, así que se lee
		// como una consulta.
		List<Map<String, Object>> actualizadas = jdbc.query("UPDATE notificacion SET leido = ? "
				+ "WHERE idnotificacion = ? AND idinstitucional = ? RETURNING " + COLUMNAS,
				A_MAPA, leido, idNotificacion, idInstitucional);
		// Lista vacía = no se actualizó nada. Las dos causas posibles dan el mismo 404, por la regla de
		// no confirmar la existencia de datos ajenos.
		if (actualizadas.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "No existe esa notificación.");
		}
		// Solo puede haber una: idnotificacion es la llave primaria.
		return actualizadas.get(0);
	}

}
