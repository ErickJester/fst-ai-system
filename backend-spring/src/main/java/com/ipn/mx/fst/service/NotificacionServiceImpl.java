package com.ipn.mx.fst.service;

import com.ipn.mx.fst.utilerias.ErrorApi;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacionServiceImpl implements NotificacionService {

	private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

	// La campanita muestra solo las más recientes: sin tope, una cuenta con meses de uso traería miles de
	// avisos en cada consulta.
	private static final int TOPE = 100;

	// Las columnas que ven la lista y el PATCH, en el mismo orden para el mismo RowMapper.
	private static final String COLUMNAS = "idnotificacion, tipo, mensaje, leido, fechacreacion, idexperimento";

	@Autowired
	private JdbcTemplate jdbc;

	// {idNotificacion, tipo, mensaje, leido, fechaCreacion, idExperimento}. La fecha va en la zona de México,
	// como fechaAnalisis; idExperimento es null en las que no son de un experimento (la alerta de disco).
	private static final RowMapper<Map<String, Object>> A_MAPA = (rs, fila) -> {
		Map<String, Object> notificacion = new LinkedHashMap<>();
		notificacion.put("idNotificacion", rs.getInt("idnotificacion"));
		notificacion.put("tipo", rs.getString("tipo"));
		notificacion.put("mensaje", rs.getString("mensaje"));
		notificacion.put("leido", rs.getBoolean("leido"));
		notificacion.put("fechaCreacion", rs.getObject("fechacreacion", OffsetDateTime.class)
				.atZoneSameInstant(ZONA).toOffsetDateTime());
		notificacion.put("idExperimento", rs.getObject("idexperimento", Integer.class));
		return notificacion;
	};

	@Override
	@Transactional(readOnly = true)
	public List<Map<String, Object>> listar(String idInstitucional) {
		return jdbc.query("SELECT " + COLUMNAS + " FROM notificacion WHERE idinstitucional = ? "
				+ "ORDER BY fechacreacion DESC, idnotificacion DESC LIMIT ?", A_MAPA, idInstitucional, TOPE);
	}

	// Un solo UPDATE ... RETURNING: solo toca la fila si es de esta cuenta, y devuelve la notificación ya
	// actualizada. Si no devuelve nada, la notificación no existe o es de otra cuenta, y en los dos casos es
	// el mismo 404, para no revelar que existe.
	@Override
	@Transactional
	public Map<String, Object> marcar(String idInstitucional, Integer idNotificacion, boolean leido) {
		List<Map<String, Object>> actualizadas = jdbc.query("UPDATE notificacion SET leido = ? "
				+ "WHERE idnotificacion = ? AND idinstitucional = ? RETURNING " + COLUMNAS,
				A_MAPA, leido, idNotificacion, idInstitucional);
		if (actualizadas.isEmpty()) {
			throw new ErrorApi(HttpStatus.NOT_FOUND, "No existe esa notificación.");
		}
		return actualizadas.get(0);
	}

}
