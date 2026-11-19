// Prueba unitaria del aviso que se crea al cancelar un análisis en pausa desde su pantalla. Sin base de datos ni Docker:
// ./mvnw test -Dtest=PausaCancelacionAvisoTest
package com.ipn.mx.fst.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipn.mx.fst.domain.valores.TipoNotificacion;
import com.ipn.mx.fst.utilerias.service.EmailService;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.util.ReflectionTestUtils;

class PausaCancelacionAvisoTest {

	@Test
	void elTextoDicaGrupoTandaExperimentoYDeDondeSeCancelo() {
		assertEquals("El análisis de Control · Tanda B del experimento 'Prueba 3' se canceló desde la pantalla de pausa.",
				PausaServiceImpl.mensajeDeCancelacion("Control", 2, "Prueba 3"));
	}

	@Test
	void alCancelarSeCreaLaNotificacionDeAnalisisCanceladoParaQuienCreoElExperimento() throws Exception {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		PausaServiceImpl servicio = new PausaServiceImpl();
		EmailService correo = mock(EmailService.class);
		ReflectionTestUtils.setField(servicio, "jdbc", jdbc);
		ReflectionTestUtils.setField(servicio, "emailService", correo);

		ResultSet fila = mock(ResultSet.class);
		when(fila.getString("idinstitucional")).thenReturn("2020630001");
		when(fila.getInt("idexperimento")).thenReturn(7);
		when(fila.getString("correo")).thenReturn("responsable@ejemplo.com");
		when(fila.getString("nombre")).thenReturn("Prueba 3");
		when(fila.getString("etiqueta")).thenReturn("Control");
		when(fila.getInt("ordinal")).thenReturn(1);
		// La consulta entrega su única fila al manejador, como lo haría la base.
		org.mockito.Mockito.doAnswer(inv -> {
			((RowCallbackHandler) inv.getArgument(1)).processRow(fila);
			return null;
		}).when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq(15));

		ReflectionTestUtils.invokeMethod(servicio, "avisarCancelacion", 15);

		ArgumentCaptor<Object> valores = ArgumentCaptor.forClass(Object.class);
		verify(jdbc).update(anyString(), valores.capture(), valores.capture(), valores.capture(), valores.capture());
		assertEquals("2020630001", valores.getAllValues().get(0));
		assertEquals(7, valores.getAllValues().get(1));
		assertEquals(TipoNotificacion.ANALISIS_CANCELADO.valor(), valores.getAllValues().get(2));
		assertEquals("El análisis de Control · Tanda A del experimento 'Prueba 3' se canceló desde la pantalla de pausa.",
				valores.getAllValues().get(3));
		// Y una copia por correo al mismo responsable, con el mismo texto (sin transacción, sale al momento).
		verify(correo).enviarCorreo("responsable@ejemplo.com", "Análisis cancelado",
				"El análisis de Control · Tanda A del experimento 'Prueba 3' se canceló desde la pantalla de pausa.");
	}

	@Test
	void siElCorreoFallaLaCancelacionNoSeDeshace() throws Exception {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		PausaServiceImpl servicio = new PausaServiceImpl();
		EmailService correo = mock(EmailService.class);
		org.mockito.Mockito.doThrow(new RuntimeException("SMTP caído")).when(correo).enviarCorreo(anyString(), anyString(), anyString());
		ReflectionTestUtils.setField(servicio, "jdbc", jdbc);
		ReflectionTestUtils.setField(servicio, "emailService", correo);
		ResultSet fila = mock(ResultSet.class);
		when(fila.getString("correo")).thenReturn("responsable@ejemplo.com");
		org.mockito.Mockito.doAnswer(inv -> {
			((RowCallbackHandler) inv.getArgument(1)).processRow(fila);
			return null;
		}).when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq(15));

		// No lanza: el error del correo se registra y la notificación ya quedó insertada.
		ReflectionTestUtils.invokeMethod(servicio, "avisarCancelacion", 15);
		verify(jdbc).update(anyString(), any(), any(), any(), any());
	}
}
