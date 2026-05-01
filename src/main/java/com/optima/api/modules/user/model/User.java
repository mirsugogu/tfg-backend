package com.optima.api.modules.user.model;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que representa un usuario del sistema (administrador o empleado de un negocio).
 * Cada usuario pertenece a un único negocio (multi-tenant) y tiene un único rol.
 * El email es único dentro de cada negocio (dos negocios distintos pueden tener
 * usuarios con el mismo email).
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_business_email",
                columnNames = {"id_business", "email"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user")
    private Long id;

    /**
     * Negocio al que pertenece este usuario.
     * Relación muchos-a-uno: muchos usuarios pueden pertenecer al mismo negocio.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /**
     * Rol del usuario dentro del negocio (ADMIN o EMPLOYEE).
     * Relación muchos-a-uno: muchos usuarios pueden tener el mismo rol.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_role", nullable = false)
    private Role role;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    /**
     * Hash BCrypt de la contraseña. Nunca se guarda la contraseña en texto plano.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /**
     * Se ejecuta automáticamente antes de hacer INSERT en la BD.
     * Rellena la fecha de creación con el momento actual.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}