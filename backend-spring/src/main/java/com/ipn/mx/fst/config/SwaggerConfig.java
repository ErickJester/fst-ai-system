// "package" dice en qué carpeta lógica vive esta clase. Tiene que coincidir con la ruta de carpetas reales:
// com/ipn/mx/fst/config. Spring usa estos nombres para encontrar las clases al arrancar.
package com.ipn.mx.fst.config;

// "import" trae una clase de otra biblioteca para poder nombrarla sin escribir su ruta completa.
// SecuritySchemeType: enumeración de Swagger con los tipos de seguridad que entiende (HTTP, APIKEY, OAUTH2...).
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
// @SecurityScheme: la anotación con la que se DECLARA un esquema de seguridad (aquí, el token Bearer).
import io.swagger.v3.oas.annotations.security.SecurityScheme;
// OpenAPI: el objeto que representa el documento completo de la especificación OpenAPI (lo que pinta Swagger UI).
import io.swagger.v3.oas.models.OpenAPI;
// SecurityRequirement: dice "esta operación EXIGE tal esquema de seguridad". Declarar el esquema no basta;
// además hay que exigirlo, y eso es este objeto.
import io.swagger.v3.oas.models.security.SecurityRequirement;
// @Bean: marca un método cuyo resultado Spring guarda y reparte a quien lo necesite.
import org.springframework.context.annotation.Bean;
// @Configuration: marca una clase que aporta configuración (métodos @Bean) en lugar de lógica de negocio.
import org.springframework.context.annotation.Configuration;

// Le dice a Swagger UI que la API usa un token Bearer: aparece el botón "Authorize" y, ya autorizado, manda
// la cabecera Authorization en cada prueba. Se declara a nivel global porque casi todas las rutas lo piden.
//
// @Configuration: Spring lee esta clase al arrancar y ejecuta sus métodos @Bean. No es un @RestController
// (no atiende peticiones) ni un @Service (no tiene reglas de negocio): solo arma objetos de configuración.
@Configuration
// @SecurityScheme DECLARA el esquema, es decir, describe cómo se autentica la API. Sus partes:
//   name = "bearer"       el nombre interno del esquema; se usa abajo en addList("bearer") para referirse a él.
//   type = HTTP           la autenticación va en una cabecera HTTP estándar (y no en una cookie ni en la URL).
//   scheme = "bearer"     la cabecera será "Authorization: Bearer <token>".
//   bearerFormat = "JWT"  solo informativo, para que Swagger muestre al lector que ese token es un JWT.
// Esto NO protege nada por sí solo: la protección real está en SeguridadConfig. Esto es documentación.
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SwaggerConfig {

	// @Bean: Spring llama a este método una sola vez al arrancar, guarda el OpenAPI que devuelve y se lo
	// entrega a springdoc, que es quien construye la página de Swagger UI.
	@Bean
	public OpenAPI openApi() {
		// new OpenAPI()            crea el documento vacío.
		// .addSecurityItem(...)    le añade un requisito de seguridad GLOBAL: toda operación documentada
		//                          exige el esquema llamado "bearer" salvo que una ruta diga lo contrario.
		// new SecurityRequirement().addList("bearer")  es ese requisito, apuntando al esquema por su nombre.
		// Resultado práctico: en Swagger UI sale el botón "Authorize"; pegas ahí el token una vez y todas
		// las pruebas que hagas desde la página lo mandan en la cabecera.
		return new OpenAPI().addSecurityItem(new SecurityRequirement().addList("bearer"));
	}

}
