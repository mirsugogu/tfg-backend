package com.optima.api.modules.catalog.model;

import com.optima.api.modules.business.model.Business;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que agrupa los servicios de un negocio por categoria.
 * El nombre no se puede repetir dentro del mismo negocio.
 */
@Entity
@Table(
        name = "service_categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_category_business_name",
                columnNames = {"id_business", "name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_category")
    private Long id;

    /** Negocio al que pertenece esta categoria. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** Nombre visible de la categoria. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Indica si la categoria sigue disponible. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Fecha y hora en que se creo la categoria (rellenado por @PrePersist). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Momento de la desactivacion (null mientras la categoria este activa). */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** Asigna la fecha de creacion antes de guardar. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}