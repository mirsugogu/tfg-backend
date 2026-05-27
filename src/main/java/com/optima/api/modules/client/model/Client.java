package com.optima.api.modules.client.model;

import com.optima.api.modules.business.model.Business;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Entidad que representa a un cliente de un negocio. */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_client")
    private Long id;

    /** Negocio al que pertenece este cliente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** Nombre completo del cliente. */
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** Email de contacto opcional. */
    @Column(name = "email", length = 150)
    private String email;

    /** Telefono de contacto opcional. */
    @Column(name = "phone", length = 20)
    private String phone;

    /** Notas internas del negocio sobre el cliente. */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Indica si el cliente sigue activo. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Fecha y hora de creacion del cliente. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Momento en que se desactivo el cliente. */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** Asigna la fecha de creacion antes de guardar. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}