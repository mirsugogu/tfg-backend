package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa un impuesto definido por un negocio.
 * El nombre del impuesto es unico dentro de cada negocio.
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

    /** Negocio al que pertenece este impuesto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** Nombre visible del impuesto. */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** Porcentaje aplicado al impuesto. */
    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    /** Indica si el impuesto sigue disponible. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Fecha y hora en que se creo el impuesto (rellenado por @PrePersist). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Momento de la desactivacion (null mientras el impuesto este activo). */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** Asigna la fecha de creacion antes de guardar. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
