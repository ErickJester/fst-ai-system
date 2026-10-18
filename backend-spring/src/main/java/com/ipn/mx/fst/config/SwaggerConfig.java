package com.ipn.mx.fst.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Le dice a Swagger UI que la API usa un token Bearer: aparece el botón "Authorize" y, ya autorizado, manda
// la cabecera Authorization en cada prueba. Se declara a nivel global porque casi todas las rutas lo piden.
@Configuration
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SwaggerConfig {

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI().addSecurityItem(new SecurityRequirement().addList("bearer"));
	}

}
