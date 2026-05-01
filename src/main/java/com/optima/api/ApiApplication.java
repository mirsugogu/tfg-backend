package com.optima.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Properties;

@SpringBootApplication
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ApiApplication.class);
		Properties props = new Properties();

		// Base de Datos
		props.setProperty("spring.datasource.url", "jdbc:mysql://localhost:3307/optima_db");
		props.setProperty("spring.datasource.username", "optima_user");
		props.setProperty("spring.datasource.password", "optima_pass");

		// SEGURIDAD (Esto quita el 403)
		props.setProperty("spring.main.allow-bean-definition-overriding", "true");
		props.setProperty("spring.jpa.hibernate.ddl-auto", "none");
		props.setProperty("spring.security.csrf.disable", "true");

		app.setDefaultProperties(props);
		app.run(args);
	}

}
