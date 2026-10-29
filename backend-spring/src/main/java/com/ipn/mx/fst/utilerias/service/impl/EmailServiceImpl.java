package com.ipn.mx.fst.utilerias.service.impl;

import com.ipn.mx.fst.utilerias.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// Igual que en la práctica: JavaMailSender (configurado con spring.mail.* en application.properties) y un
// SimpleMailMessage de texto plano. El servidor SMTP sale de las variables SMTP_* del .env.
@Service
public class EmailServiceImpl implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

	@Autowired
	private JavaMailSender mailSender;

	@Value("${app.correo.remitente}")
	private String remitente;

	@Override
	public void enviarCorreo(String destinatario, String asunto, String texto) {
		if (destinatario == null || destinatario.isBlank()) {
			log.warn("Correo sin destinatario, no se envía: '{}'", asunto);
			return;
		}

		SimpleMailMessage correo = new SimpleMailMessage();
		// El .env de la versión portable trae SMTP_FROM vacío hasta que se llena: con la variable vacía el remitente quedaría
		// en blanco (el valor por omisión de application.properties solo cubre la variable que no existe).
		correo.setFrom(remitente == null || remitente.isBlank() ? "no-reply@fst.local" : remitente);
		correo.setTo(destinatario);
		correo.setSubject(asunto);
		correo.setText(texto);

		// Si el correo falla, la operación sigue (regla de la guía, sección 11 punto 2): el error queda en
		// el log y la petición responde normal.
		try {
			mailSender.send(correo);
		} catch (MailException e) {
			log.error("No se pudo enviar el correo '{}' a {}: {}", asunto, destinatario, e.getMessage());
		}
	}

}
