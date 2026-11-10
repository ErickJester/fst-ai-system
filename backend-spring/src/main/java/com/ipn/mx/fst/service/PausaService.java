// Carpeta lógica de esta interfaz: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

import java.util.Map;
// Resource: una fuente de datos binarios (aquí, el JPG) que Spring vuelca a la respuesta.
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;

// La pausa de un análisis (R-15, §4.3 y §6.1 de la guía): ver la imagen fija, ajustar el recuadro de un cilindro o
// cancelar. Lo escribe el worker en analisis/{id}/pausa.json y pausa.jpg; aquí solo se lee y se ajusta. Reglas:
// 404 si el análisis no es de ese experimento; 403 si quien pide no subió el video (D-107) ni es Administrador; 409 si el
// análisis ya no está 'en pausa'. Los cambios de estado van con la condición "solo si sigue en pausa" (regla b).
//
// Qué es la pausa: cuando el worker no logra ubicar con seguridad un cilindro, no se arriesga a medir mal.
// Se detiene, guarda una foto del instante con el recuadro que propuso y espera a que una persona lo
// corrija, lo apruebe o cancele. Es el punto donde el sistema pide ayuda en vez de inventar datos.
//
// Fíjate en algo que no se parece a nada más del backend: el estado de la pausa NO está en la base, sino
// en dos archivos del volumen que escribe el worker (pausa.json y pausa.jpg). La base solo guarda que el
// análisis está 'en pausa'. Es un segundo canal de comunicación entre los dos programas, además de la
// tabla analisis.
//
// La "regla b" merece explicarse. Entre que se lee el estado y se escribe el nuevo puede pasar cualquier
// cosa: el worker puede haber reanudado, o alguien puede haber cancelado desde otra pestaña. Por eso los
// cambios se escriben con la condición incluida ("...WHERE estado = 'en pausa'") en lugar de leer primero
// y escribir después. Si la condición ya no se cumple, no se cambia nada y sale el 409.
// Ver la explicación de qué es la capa de servicio en VideoService.
public interface PausaService {

	// {cilindro, desdeSegundo, segundosRestantes, ancho, alto, recuadros} de la pausa vigente.
	// Sale de pausa.json. ancho y alto son los del video, y los necesita la pantalla para dibujar el
	// recuadro encima de la imagen a escala.
	// Leerlo lo puede cualquiera con sesión; para CAMBIAR algo sí hace falta ser quien subió el video.
	Map<String, Object> pausa(Integer idExperimento, Integer idAnalisis, Authentication authentication);

	// El JPG de la imagen fija del instante de la pausa.
	// Es lo que mira la persona para decidir: el fotograma donde el worker se atascó.
	// Devuelve Resource y no byte[] para que Spring lo vuelque sin cargarlo entero en memoria.
	Resource imagen(Integer idExperimento, Integer idAnalisis, Authentication authentication);

	// Agrega el recuadro corregido a pausa.json y devuelve el análisis a 'en cola'. Conserva fechaAnalisis (D-79).
	//
	// "Agrega" y no "reemplaza": el recuadro que propuso el worker se queda, y el corregido se suma. Así
	// queda constancia de en qué se equivocó el modelo, que es justo lo que sirve para reentrenarlo.
	// Volver a 'en cola' es cómo se le devuelve el trabajo al worker: él toma el 'en cola' más antiguo.
	// Conservar fechaAnalisis es lo que hace que no pierda su turno: si se pusiera la hora actual, se
	// iría al final de la cola y cada pausa lo retrasaría más (ver la entidad Analisis).
	// Aquí entra app.pausa.umbral-recuadro-px: mover el recuadro 3 px o menos no cuenta como ajuste (422).
	void ajustar(Integer idExperimento, Integer idAnalisis, Map<String, Object> recuadro, Authentication authentication);

	// «El recuadro está bien, continuar»: marca la pausa como aceptada en pausa.json y devuelve el análisis a 'en cola'
	// (fechaAnalisis intacta). El worker no vuelve a pausar esa racha. 409 si ya no estaba en pausa.
	//
	// "El worker no vuelve a pausar esa racha" es imprescindible: sin esa marca, el worker llegaría al
	// mismo punto dudoso, volvería a pausar y el análisis no avanzaría nunca.
	void continuar(Integer idExperimento, Integer idAnalisis, Authentication authentication);

	// Cancela el análisis en pausa por decisión del usuario. No crea notificación (solo la crea el vencimiento).
	//
	// La distinción tiene sentido: si cancelas tú, ya lo sabes y avisarte no aporta nada. La notificación
	// ANALISIS_CANCELADO se reserva para cuando una pausa caduca sola sin que nadie la atendiera, que es
	// cuando sí hay algo que contar.
	// El análisis queda en estado 'cancelado'; la fila no se borra, para que quede constancia.
	void cancelar(Integer idExperimento, Integer idAnalisis, Authentication authentication);
}
