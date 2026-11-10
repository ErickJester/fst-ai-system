// Carpeta lógica de esta interfaz: com/ipn/mx/fst/utilerias/service.
package com.ipn.mx.fst.utilerias.service;

// Envío de correo. A diferencia de la práctica (que recibía un Evento), recibe destinatario, asunto y
// texto, para reutilizarlo después en las notificaciones, la contraseña temporal y la recuperación.
//
// Qué es una interfaz: un contrato. Dice QUÉ se puede pedir, no CÓMO se hace. La implementación está en
// EmailServiceImpl, y es la que Spring inyecta donde alguien pida un EmailService.
// Por qué separar interfaz e implementación: quien usa el correo (AuthServiceImpl, NotificacionServiceImpl)
// depende solo del contrato. Cambiar el envío por otra biblioteca, o sustituirlo por uno falso en una
// prueba, no obliga a tocar a los que lo usan. Es el mismo patrón de todo el paquete service.
public interface EmailService {

	// Los métodos de una interfaz no llevan cuerpo: terminan en punto y coma.
	// Son públicos y abstractos aunque no se escriba: en una interfaz es lo que se da por hecho.
	// Fíjate en que devuelve void y no un booleano de "se envió": a propósito. Si el correo falla, la
	// operación que lo pidió sigue adelante (ver el try/catch de EmailServiceImpl), así que a quien llama
	// no le sirve de nada saberlo.
	void enviarCorreo(String destinatario, String asunto, String texto);

}
