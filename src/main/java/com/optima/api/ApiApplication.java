package com.optima.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Clase principal del backend; desde aqui Spring detecta los componentes. */
@SpringBootApplication
public class ApiApplication {

	/**
	 * Arranca Spring Boot y crea el contexto de la aplicacion.
	 */
	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
