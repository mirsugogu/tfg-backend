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
 * con sus propios usuarios, clientes, servicios, etc. Es la raiz del
 * multi-tenancy: practicamente todas las demas entidades tienen un
 * @ManyToOne hacia Business directa o indirectamente.
 *
 * Soft delete (is_active + deactivated_at): nunca borramos fisicamente
 * un negocio porque rompiamos todas las FKs (users, clients, appointments,
 * etc.). Marcamos is_active=false y se conserva todo el historico.
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, BusinessService.create manualmente.
 * - La consume: BusinessResponse.from(), AuthService (login y register),
 *   GeocodingService (rellena latitude/longitude best-effort), todos los
 *   services tenant-scoped (validacion cross-tenant).
 * - Es referenciada por: Membership, Client, Tax, Booth, BusinessHour,
 *   ScheduleBlock, ServiceCategory, BusinessService, Appointment...
 *
 * Mapea a la tabla `businesses` (docs/schema_v20.sql). slug y email
 * son UNIQUE global; latitude/longitude pueden ser null si Nominatim no
 * pudo resolver la direccion.
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

    /**
     * Nombre comercial del negocio (visible al cliente final).
     */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /**
     * Identificador URL-friendly del negocio (lowercase, sin espacios).
     * UNIQUE global; inmutable una vez creado (forma parte de URLs publicas).
     */
    @Column(name = "slug", nullable = false, length = 150, unique = true)
    private String slug;

    /**
     * Email de contacto del negocio. UNIQUE global.
     */
    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    /**
     * Telefono de contacto (opcional).
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * Direccion postal del negocio (opcional).
     */
    @Column(name = "address", length = 255)
    private String address;

    /**
     * Ciudad. Junto con postalCode alimenta a Nominatim para geocodificar.
     */
    @Column(name = "city", length = 100)
    private String city;

    /**
     * Estado o provincia (opcional).
     */
    @Column(name = "state", length = 100)
    private String state;

    /**
     * Pais (opcional).
     */
    @Column(name = "country", length = 100)
    private String country;

    /**
     * Codigo postal. Junto con city alimenta a Nominatim para geocodificar.
     */
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    /**
     * Latitud resuelta por GeocodingService (Nominatim). Nullable: si la
     * direccion no se pudo geocodificar (best-effort) queda en null.
     * DECIMAL(10,8) en SQL: 8 decimales de precision para ~1mm a nivel terrestre.
     */
    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    /**
     * Longitud resuelta por GeocodingService (Nominatim). Misma logica que
     * latitude. DECIMAL(11,8) en SQL (un digito mas para cubrir +/-180).
     */
    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    /**
     * Intervalo en minutos para slots de citas (15, 30, 45 o 60).
     * Default 30; validado en el service.
     */
    @Column(name = "appointment_interval", nullable = false)
    private Integer appointmentInterval = 30;

    /**
     * Flag de soft delete: false significa que el negocio esta desactivado
     * (no acepta logins ni nuevas citas), pero todo su historico se conserva.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Fecha y hora en que se creo el negocio (rellenado por @PrePersist).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Momento de la desactivacion (null mientras el negocio este activo).
     */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /**
     * Se ejecuta automaticamente antes de hacer INSERT en la BD.
     * Rellena la fecha de creacion con el momento actual.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
