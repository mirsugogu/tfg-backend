package com.optima.api;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiApplicationTests {

	@Autowired
	private EntityManager entityManager;

	/**
	 * Verifica que el contexto de Spring arranca correctamente y que
	 * Hibernate se inicializa con acceso al EntityManager.
	 */
	@Test
	void contextLoads() {
		assertThat(entityManager).isNotNull();
	}

	/**
	 * Fuerza la inicialización completa de Hibernate validando que todas
	 * las entidades registradas se pueden consultar. Si alguna entidad
	 * está mal mapeada al schema real de MySQL (columna inexistente,
	 * tipo incorrecto, FK mal, etc.), este test FALLARÁ.
	 *
	 * Solo funciona con spring.jpa.hibernate.ddl-auto=validate.
	 */
	@Test
	void allEntitiesMatchDatabaseSchema() {
		// Lista de todas las entidades que queremos validar
		Class<?>[] entities = {
				com.optima.api.business.Business.class,
				com.optima.api.role.Role.class,
				com.optima.api.user.User.class,
				com.optima.api.employeeschedule.EmployeeSchedule.class,
				com.optima.api.tax.Tax.class,
				com.optima.api.client.Client.class,
				com.optima.api.servicecategory.ServiceCategory.class,
				com.optima.api.businessservice.BusinessService.class,
				com.optima.api.appointmentstatus.AppointmentStatus.class,
				com.optima.api.appointment.Appointment.class,
				com.optima.api.bookedservice.BookedService.class
		};

		// Intenta ejecutar una consulta COUNT contra cada entidad.
		// Si el mapeo entidad → tabla es incorrecto, MySQL o Hibernate
		// lanzarán una excepción.
		for (Class<?> entity : entities) {
			String entityName = entity.getSimpleName();
			Long count = (Long) entityManager
					.createQuery("SELECT COUNT(e) FROM " + entityName + " e")
					.getSingleResult();

			// No validamos el número, solo que la consulta se ejecuta sin error
			assertThat(count).isNotNull();
		}
	}
}