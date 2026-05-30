package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * negocio del sistema
 * separa los datos entre negocios
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

    /** nombre comercial del negocio */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** identificador publico del negocio para enlaces */
    @Column(name = "slug", nullable = false, length = 150, unique = true)
    private String slug;

    /** email de contacto del negocio unico a nivel global */
    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    /** telefono de contacto opcional */
    @Column(name = "phone", length = 20)
    private String phone;

    /** direccion postal del negocio opcional */
    @Column(name = "address", length = 255)
    private String address;

    /** ciudad del negocio */
    @Column(name = "city", length = 100)
    private String city;

    /** estado o provincia opcional */
    @Column(name = "state", length = 100)
    private String state;

    /** pais opcional */
    @Column(name = "country", length = 100)
    private String country;

    /** codigo postal del negocio */
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    /** latitud calculada a partir de la direccion */
    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    /** longitud calculada a partir de la direccion */
    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    /** intervalo en minutos usado para generar huecos de citas */
    @Column(name = "appointment_interval", nullable = false)
    private Integer appointmentInterval = 30;

    /** indica si el negocio sigue activo */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** fecha y hora en que se creo el negocio */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** momento de la desactivacion sin valor mientras el negocio este activo */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** asigna la fecha de creacion antes de guardar */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
