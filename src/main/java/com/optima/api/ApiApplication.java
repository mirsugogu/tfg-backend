package com.optima.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** clase principal del servidor */
@SpringBootApplication
public class ApiApplication {

	/**
	 * arranca la aplicacion del servidor
	 */
	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
