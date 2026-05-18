package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa un impuesto de un negocio.
 * Cada negocio define sus propios impuestos (IVA general, IVA reducido, etc.).
 * El nombre es unico dentro de cada negocio (dos negocios distintos pueden
 * tener impuestos con el mismo nombre).
 *
 * Soft delete (is_active + deactivated_at): si se deja de usar un impuesto
 * se marca como inactivo en lugar de borrarlo, para no romper los servicios
 * que ya lo referencian ni los booked_services historicos que congelaron
 * su porcentaje. Alineado con businesses, users, clients, services y
 * service_categories.
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, TaxService.create manualmente.
 * - La consume: TaxResponse.from(), BusinessServiceService (cross-tenant
 *   al crear/editar servicios), BookedService (congela percentage).
 * - Tiene @ManyToOne con: Business.
 * - Es referenciada por: BusinessService.tax (@ManyToOne).
 *
 * Mapea a la tabla `taxes` (docs/schema_v20.sql; created_at anyadido en v18).
 * El uniqueConstraint uq_tax_business_name (id_business, name) impide dos
 * impuestos con el mismo nombre en el mismo negocio.
 */
@Entity
@Table(
        name = "taxes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tax_business_name",
                columnNames = {"id_business", "name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tax {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tax")
    private Long id;

    /**
     * Negocio al que pertenece este impuesto.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /**
     * Nombre visible del impuesto (ej. "IVA 21%", "IVA reducido"). Unico
     * dentro del mismo negocio.
     */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * Porcentaje del impuesto (0 a 100).
     * Se usa BigDecimal para precision exacta en calculos monetarios.
     */
    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    /**
     * Flag de soft delete: false oculta el impuesto del listado activo y
     * lo excluye de la asignacion a servicios nuevos, pero conserva los
     * booked_services historicos que congelaron su porcentaje.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Fecha y hora en que se creo el impuesto (rellenado por @PrePersist).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Momento de la desactivacion (null mientras el impuesto este activo).
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
