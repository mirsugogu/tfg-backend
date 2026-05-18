package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad que representa un rol de usuario en el sistema.
 * Catálogo global (compartido por todos los negocios).
 * Valores insertados en el schema SQL: ADMIN, EMPLOYEE.
 *
 * Mapea a la tabla `roles` (docs/schema_v20.sql):
 *   CREATE TABLE roles (
 *       id_role BIGINT AUTO_INCREMENT PRIMARY KEY,
 *       name    VARCHAR(30) NOT NULL UNIQUE
 *   );
 *
 * COMUNICACION:
 * - La instancia: Hibernate, al hidratar filas leidas de MySQL.
 * - La consume: RoleService (la convierte en RoleResponse).
 * - Es referenciada por: Membership.role (@ManyToOne) - cada membresia
 *   (usuario en un negocio) tiene exactamente un rol.
 *
 * Anotaciones Lombok:
 *   @Getter @Setter         getters/setters de los 2 campos.
 *   @NoArgsConstructor      OBLIGATORIO para JPA (lo usa por reflexion
 *                           al hidratar entidades).
 *   @AllArgsConstructor     util para tests y construccion manual.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_role")
    private Long id;

    @Column(name = "name", nullable = false, length = 30, unique = true)
    private String name;
}