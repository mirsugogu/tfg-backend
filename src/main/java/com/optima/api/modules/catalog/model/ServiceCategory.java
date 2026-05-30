package com.optima.api.modules.catalog.model;

import com.optima.api.modules.business.model.Business;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * grupo de servicios del negocio
 * nombre unico dentro del negocio
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

    /** negocio al que pertenece esta categoria */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** nombre visible de la categoria */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** indica si la categoria sigue disponible */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** fecha y hora en que se creo la categoria */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** momento de la desactivacion sin valor mientras la categoria este activa */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** asigna la fecha de creacion antes de guardar */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}