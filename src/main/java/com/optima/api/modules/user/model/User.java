package com.optima.api.modules.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Entidad que representa la identidad global de una persona. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user")
    private Long id;

    /** Nombre completo de la persona. */
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** Email unico de la identidad. */
    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    /** Hash BCrypt de la contraseña. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** Telefono de contacto opcional. */
    @Column(name = "phone", length = 20)
    private String phone;

    /** Indica si la identidad puede iniciar sesion. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Fecha y hora de creacion de la identidad. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Momento en que se desactivo la identidad. */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** Asigna la fecha de creacion antes de guardar. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}