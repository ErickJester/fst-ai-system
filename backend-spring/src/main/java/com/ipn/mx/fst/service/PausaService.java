package com.ipn.mx.fst.service;

import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;

// La pausa de un análisis (R-15, §4.3 y §6.1 de la guía): ver la imagen fija, ajustar el recuadro de un cilindro o
// cancelar. Lo escribe el worker en analisis/{id}/pausa.json y pausa.jpg; aquí solo se lee y se ajusta. Reglas:
// 404 si el análisis no es de ese experimento; 403 si quien pide no subió el video (D-107) ni es Administrador; 409 si el
// análisis ya no está 'en pausa'. Los cambios de estado van con la condición "solo si sigue en pausa" (regla b).
public interface PausaService {

	// {cilindro, desdeSegundo, segundosRestantes, ancho, alto, recuadros} de la pausa vigente.
	Map<String, Object> pausa(Integer idExperimento, Integer idAnalisis, Authentication authentication);

	// El JPG de la imagen fija del instante de la pausa.
	Resource imagen(Integer idExperimento, Integer idAnalisis, Authentication authentication);

	// Agrega el recuadro corregido a pausa.json y devuelve el análisis a 'en cola'. Conserva fechaAnalisis (D-79).
	void ajustar(Integer idExperimento, Integer idAnalisis, Map<String, Object> recuadro, Authentication authentication);

	// Cancela el análisis en pausa por decisión del usuario. No crea notificación (solo la crea el vencimiento).
	void cancelar(Integer idExperimento, Integer idAnalisis, Authentication authentication);
}
