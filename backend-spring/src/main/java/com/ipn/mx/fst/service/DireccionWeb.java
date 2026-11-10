// Carpeta lógica de esta clase: com/ipn/mx/fst/service.
package com.ipn.mx.fst.service;

// @Value: inyecta una propiedad de application.properties o del entorno.
import org.springframework.beans.factory.annotation.Value;
// @Component: Spring crea esta clase y la inyecta donde se pida.
import org.springframework.stereotype.Component;

// La dirección de la web con la que se arman los enlaces de los correos (restablecer contraseña, primer acceso). Sale de WEB_URL;
// si está vacía o no existe, se usa la primera de CORS_ORIGINS, que es desde donde se abre la web.
//
// El problema que resuelve: el backend tiene que escribir en un correo un enlace completo
// (https://fst.ejemplo.mx/restablecer?token=...), pero él no sabe por qué dirección entra la gente a la
// web. Está detrás de un nginx, y la petición que le llega no lleva esa información de forma fiable.
// La solución es decírselo por configuración, con un respaldo razonable: los orígenes de CORS son, por
// definición, las páginas desde las que se usa la API, así que el primero suele ser la dirección buena.
// Es un @Component y no un @Service porque no tiene reglas de negocio: es una utilidad de configuración.
@Component
public class DireccionWeb {

	// De WEB_URL. El ":" sin nada detrás la deja vacía si la variable no existe, en vez de impedir el arranque.
	@Value("${app.web.url:}")
	private String configurada;

	// La lista de CORS, como texto con comas. Fíjate en que aquí se pide como String y no como List<String>,
	// al revés que en SeguridadConfig: ahí interesaba la lista entera, aquí solo el primero.
	@Value("${app.cors.origenes}")
	private String origenesCors;

	// Sin diagonal final.
	//
	// Ese detalle importa más de lo que parece: quien usa este método va a pegarle "/restablecer" detrás.
	// Si la base terminara en diagonal saldría "https://sitio//restablecer", que algunos servidores
	// rechazan. Normalizar aquí evita tener que acordarse en cada sitio donde se arma un enlace.
	public String base() {
		// Si WEB_URL no está o está vacía, se usa el primer origen de CORS.
		// split(",")[0] parte por comas y toma el primero; trim() le quita los espacios, porque la lista
		// suele escribirse con espacio después de la coma.
		String url = configurada == null || configurada.isBlank() ? origenesCors.split(",")[0].trim() : configurada.trim();
		// Y aquí se quita la diagonal final si la hay. substring(0, largo - 1) devuelve el texto sin su
		// último carácter.
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
