package com.optima.api.role;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad que representa un rol de usuario en el sistema.
 * Catálogo global (compartido por todos los negocios).
 * Valores insertados en el schema SQL: ADMIN, EMPLOYEE.
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