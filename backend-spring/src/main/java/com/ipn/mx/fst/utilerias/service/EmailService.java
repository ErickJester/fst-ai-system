package com.ipn.mx.fst.utilerias.service;

// Envío de correo. A diferencia de la práctica (que recibía un Evento), recibe destinatario, asunto y
// texto, para reutilizarlo después en las notificaciones, la contraseña temporal y la recuperación.
public interface EmailService {

	void enviarCorreo(String destinatario, String asunto, String texto);

}
