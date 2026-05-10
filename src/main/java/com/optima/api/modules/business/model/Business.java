package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa un negocio (tenant) del SaaS.
 * Cada fila de la tabla "businesses" es un negocio distinto,
 * con sus propios usuarios, clientes, servicios, etc.
 *
 * Mapea a la tabla `businesses` (docs/schema_v13.sql):
 *   id_business (PK)
 *   name, slug (unique), email (unique)
 *   phone, address, city, state, country, postal_code
 *   latitude, longitude (DECIMAL, rellenado por GeocodingService)
 *   appointment_interval (15/30/45/60 min)
 *   is_active, created_at, deactivated_at (soft delete)
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, BusinessService.create manualmente.
 * - La consume: BusinessService (la convierte en BusinessResponse),
 *   AuthService (verifica isActive en login), UserService (FK).
 * - Es referenciada por: User.business, Client.business, BookedService
 *   (catalogo).business, etc. Es la raiz del multi-tenancy.
 *
 * Soft delete: nunca borramos fisicamente un negocio: rompiamos todas
 * las FK (users, clients, appointments...). Marcamos is_active=false.
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

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, length = 150, unique = true)
    private String slug;

    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    // Para DECIMAL(10,8) y DECIMAL(11,8) usamos BigDecimal
    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "appointment_interval", nullable = false)
    private Integer appointmentInterval = 30;

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