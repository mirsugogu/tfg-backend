package com.optima.api.modules.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que representa la IDENTIDAD de una persona en el sistema.
 * Un User no pertenece a un negocio concreto; su pertenencia a uno o
 * varios negocios (con su rol en cada uno) vive en la tabla
 * `memberships` (entidad com.optima.api.modules.business.model.Membership).
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar filas, AuthService.register
 *   manualmente, UserService.create (cuando da de alta a un empleado
 *   nuevo cuyo email no existia aun).
 * - La consume: UserService, AuthService (verifica password en login),
 *   MeController, Membership (FK id_user), UserResponse / AppointmentResponse
 *   / ScheduleBlockResponse para mostrar fullName.
 *
 * [v16 membership] Antes contenia @ManyToOne business + @ManyToOne role
 * con UNIQUE(id_business, email). Tras el refactor, el email es UNIQUE
 * GLOBAL (una persona = una identidad) y la relacion con negocio+rol se
 * delega a Membership.
 *
 * Soft delete: cuando se "borra" un usuario, NO se hace DELETE FROM users,
 * solo se pone is_active=false y se rellena deactivated_at. Asi se
 * preservan citas pasadas que apuntan a memberships de este usuario.
 */
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

    /**
     * Nombre completo de la persona.
     */
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /**
     * Email de la identidad. UNIQUE GLOBAL desde v16: una persona = una sola
     * identidad, aunque trabaje en varios negocios via memberships distintas.
     */
    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    /**
     * Hash BCrypt de la contraseña. Nunca se guarda la contraseña en texto plano.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Teléfono de contacto (opcional).
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * Flag de soft delete: false significa que la identidad esta desactivada
     * (no acepta logins), pero sus memberships y citas historicas se preservan.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Fecha y hora en que se creo la identidad (rellenado por @PrePersist).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Momento de la desactivacion (null mientras la identidad este activa).
     */
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