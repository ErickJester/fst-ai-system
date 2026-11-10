// Carpeta lógica de esta clase: com/ipn/mx/fst/utilerias.
package com.ipn.mx.fst.utilerias;

// HttpStatus: el enumerado con los códigos HTTP. Esta excepción carga uno para saber con qué responder.
import org.springframework.http.HttpStatus;

// Los servicios la lanzan cuando una regla de negocio rechaza la petición (404, 409, 403...). El controller
// no la atrapa: ManejadorErrores la convierte en el envoltorio {data, error, status} con este código HTTP.
//
// Esta clase es la que permite que los controllers no tengan try/catch. El servicio dice "esto está mal y
// es un 409" lanzando la excepción, y el controller ni se entera: la excepción lo atraviesa y la recoge
// ManejadorErrores. Sin esto, cada método de cada controller tendría que comprobar valores de retorno.
//
// extends RuntimeException: es una excepción NO comprobada. Es decir, un método puede lanzarla sin
// declararla en su firma con "throws" y sin que quien lo llama esté obligado a atraparla. Eso es justo lo
// que se busca aquí: que viaje sola desde el servicio hasta ManejadorErrores atravesando los controllers.
public class ErrorApi extends RuntimeException {

	// serialVersionUID: el número de versión que usa Java si alguna vez serializa el objeto. No afecta al
	// funcionamiento; se pone porque las clases serializables deben declararlo y así el compilador no avisa.
	private static final long serialVersionUID = 1L;

	// El código HTTP con el que hay que responder. final: se fija al construir y ya no cambia.
	private final HttpStatus status;

	// El constructor recibe el código y el mensaje.
	// super(mensaje) guarda el texto en la excepción, que es de donde lo saca ManejadorErrores con
	// getMessage(). Ese texto llega tal cual al usuario, así que se escribe pensando en él y nunca
	// lleva detalles internos.
	public ErrorApi(HttpStatus status, String mensaje) {
		super(mensaje);
		this.status = status;
	}

	// El getter que lee ManejadorErrores para saber con qué código responder.
	public HttpStatus getStatus() {
		return status;
	}

}
