package com.optima.api.modules.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** identidad global de una persona */
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

    /** nombre completo de la persona */
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** email unico de la identidad */
    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    /** huella de la contrasena */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** telefono de contacto opcional */
    @Column(name = "phone", length = 20)
    private String phone;

    /** indica si la identidad puede iniciar sesion */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** fecha y hora de creacion de la identidad */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** momento en que se desactivo la identidad */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** asigna la fecha de creacion antes de guardar */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}