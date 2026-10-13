package com.ipn.mx.fst;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Punto de entrada del backend FST. Spring busca controladores, servicios y repositorios en este
// paquete (com.ipn.mx.fst) y en los de abajo.
@SpringBootApplication
public class FstBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FstBackendApplication.class, args);
	}

}
