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
 * El nombre es único dentro de cada negocio (dos negocios distintos pueden
 * tener impuestos con el mismo nombre).
 * Si se deja de usar un impuesto, se marca como inactivo en lugar de borrarlo,
 * para no romper los servicios que ya lo referencian.
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
     * Relación muchos-a-uno: un negocio puede tener varios impuestos.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * Porcentaje del impuesto (0 a 100).
     * Se usa BigDecimal para precisión exacta en cálculos monetarios.
     */
    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;
}