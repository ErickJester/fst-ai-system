package com.ipn.mx.fst;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Equivale a CORS(app) de Flask-Cors en el backend de Python. */
@Configuration
public class ConfiguracionCors implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins("http://localhost:5173", "http://localhost:4200")
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
	}
}
