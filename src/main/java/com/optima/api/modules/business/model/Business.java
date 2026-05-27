package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa un negocio del sistema.
 * Es la base del aislamiento de datos entre negocios.
 */
@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_business")
    private Long id;

    /** Nombre comercial del negocio (visible al cliente final). */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Identificador publico del negocio para URLs. */
    @Column(name = "slug", nullable = false, length = 150, unique = true)
    private String slug;

    /** Email de contacto del negocio. UNIQUE global. */
    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    /** Telefono de contacto (opcional). */
    @Column(name = "phone", length = 20)
    private String phone;

    /** Direccion postal del negocio (opcional). */
    @Column(name = "address", length = 255)
    private String address;

    /** Ciudad del negocio. */
    @Column(name = "city", length = 100)
    private String city;

    /** Estado o provincia (opcional). */
    @Column(name = "state", length = 100)
    private String state;

    /** Pais (opcional). */
    @Column(name = "country", length = 100)
    private String country;

    /** Codigo postal del negocio. */
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    /** Latitud calculada a partir de la direccion. */
    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    /** Longitud calculada a partir de la direccion. */
    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    /** Intervalo en minutos usado para generar huecos de citas. */
    @Column(name = "appointment_interval", nullable = false)
    private Integer appointmentInterval = 30;

    /** Indica si el negocio sigue activo. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Fecha y hora en que se creo el negocio (rellenado por @PrePersist). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Momento de la desactivacion (null mientras el negocio este activo). */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** Asigna la fecha de creacion antes de guardar. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
