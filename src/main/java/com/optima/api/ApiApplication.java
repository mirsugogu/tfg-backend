package com.optima.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicación.
 * Toda la configuración (datasource, JPA, seguridad, etc.) vive en
 * src/main/resources/application.properties; el main se mantiene limpio.
 *
 * COMUNICACION:
 * - La JVM invoca main() al arrancar el proceso.
 * - SpringApplication.run() crea el ApplicationContext, ejecuta el
 *   component scan (encuentra @Service, @RestController, @Component,
 *   @Configuration por debajo de este paquete), aplica auto-configuracion
 *   (DataSource, JPA, Tomcat embebido) y arranca el servidor en el
 *   puerto declarado en application.properties (8080 por defecto).
 *
 * Esta clase DEBE estar en el paquete raiz (com.optima.api) para que
 * el component scan baje por todos los modules/* y common/*.
 */
@SpringBootApplication
public class ApiApplication {

	/**
	 * Bootstrap. Lo invoca la JVM con los argumentos de linea de comando
	 * y delega todo a SpringApplication.run, lo unico que necesita
	 * Spring Boot para arrancar.
	 */
	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
