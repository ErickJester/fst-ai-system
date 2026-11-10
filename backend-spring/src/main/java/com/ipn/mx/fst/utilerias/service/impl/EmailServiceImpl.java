// Carpeta lógica de esta clase: com/ipn/mx/fst/utilerias/service/impl.
package com.ipn.mx.fst.utilerias.service.impl;

// La interfaz que esta clase implementa (el contrato).
import com.ipn.mx.fst.utilerias.service.EmailService;
// El registro (log): aquí es importante, porque un correo que falla NO se le avisa al usuario y el log es
// el único rastro que queda.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// @Autowired: Spring inyecta el objeto ya construido.
import org.springframework.beans.factory.annotation.Autowired;
// @Value: inyecta una propiedad de application.properties.
import org.springframework.beans.factory.annotation.Value;
// MailException: la excepción raíz de todos los fallos de correo de Spring (no hay servidor, lo rechazó,
// la autenticación falló...). Atrapar la raíz cubre todos los casos de una vez.
import org.springframework.mail.MailException;
// SimpleMailMessage: un correo de texto plano (sin HTML ni archivos adjuntos). Basta para lo que manda
// este sistema: enlaces de recuperación y avisos.
import org.springframework.mail.SimpleMailMessage;
// JavaMailSender: el que de verdad habla SMTP con el servidor de correo. Spring Boot lo construye solo a
// partir de las propiedades spring.mail.* ; aquí nadie lo configura a mano.
import org.springframework.mail.javamail.JavaMailSender;
// @Service: marca la clase como un servicio para que Spring la cree y la inyecte donde se pida un EmailService.
import org.springframework.stereotype.Service;

// Igual que en la práctica: JavaMailSender (configurado con spring.mail.* en application.properties) y un
// SimpleMailMessage de texto plano. El servidor SMTP sale de las variables SMTP_* del .env.
//
// En desarrollo apunta a MailHog (localhost:1025), un servidor de correo falso que no manda nada a nadie:
// recoge los correos y los enseña en una página web, en http://localhost:8025. Así se prueban los enlaces
// de recuperación sin mandar correos de verdad.
@Service
public class EmailServiceImpl implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

	@Autowired
	private JavaMailSender mailSender;

	// El remitente (el "De:" del correo), de la propiedad app.correo.remitente, que a su vez sale de SMTP_FROM.
	@Value("${app.correo.remitente}")
	private String remitente;

	// @Override: implementa el método del contrato EmailService.
	@Override
	public void enviarCorreo(String destinatario, String asunto, String texto) {
		// Sin destinatario no hay nada que mandar. Se avisa en el log y se sale sin lanzar excepción: quien
		// llamó está en medio de otra operación (crear una cuenta, por ejemplo) y esa operación debe seguir.
		if (destinatario == null || destinatario.isBlank()) {
			log.warn("Correo sin destinatario, no se envía: '{}'", asunto);
			return;
		}

		SimpleMailMessage correo = new SimpleMailMessage();
		// El .env de la versión portable trae SMTP_FROM vacío hasta que se llena: con la variable vacía el remitente quedaría
		// en blanco (el valor por omisión de application.properties solo cubre la variable que no existe).
		//
		// La distinción es fina y vale la pena entenderla: ${SMTP_FROM:no-reply@fst.local} aplica el valor
		// de la derecha solo si la variable NO EXISTE. Si existe pero está vacía, gana el vacío. Por eso
		// hace falta esta segunda comprobación aquí, con el mismo valor repetido.
		correo.setFrom(remitente == null || remitente.isBlank() ? "no-reply@fst.local" : remitente);
		correo.setTo(destinatario);
		correo.setSubject(asunto);
		correo.setText(texto);

		// Si el correo falla, la operación sigue (regla de la guía, sección 11 punto 2): el error queda en
		// el log y la petición responde normal.
		//
		// Es una decisión de diseño, no un descuido: si el servidor SMTP está caído, crear una cuenta debe
		// funcionar igual. El administrador puede reenviar el correo después; perder la cuenta sería peor.
		// log.error y no warn porque esto sí requiere que alguien mire: alguien se quedó sin su correo.
		try {
			mailSender.send(correo);
		} catch (MailException e) {
			// e.getMessage() y no la excepción entera: el log queda legible, sin el rastro de pila completo.
			log.error("No se pudo enviar el correo '{}' a {}: {}", asunto, destinatario, e.getMessage());
		}
	}

}
