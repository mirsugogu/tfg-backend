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
}
