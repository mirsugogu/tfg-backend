package com.optima.api.modules.business.model;

import com.optima.api.modules.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * relacion entre un usuario con un negocio y un rol
 * una persona puede estar en varios negocios
 */
@Entity
@Table(
        name = "memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_membership_user_business",
                columnNames = {"id_user", "id_business"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_membership")
    private Long id;

    /** identidad: a que persona pertenece esta membresia */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    /** negocio donde el usuario tiene la membresia */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** rol del usuario en este negocio admin o employee */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_role", nullable = false)
    private Role role;

    /** indica si la membresia sigue activa */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** color usado por la interfaz para identificar al empleado */
    @Column(name = "color")
    private String color;

    /** fecha y hora en que se creo la membresia */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** asigna la fecha de creacion antes de guardar */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
