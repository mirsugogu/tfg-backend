package com.optima.api.servicecategory;

import com.optima.api.business.Business;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que representa una categoría de servicios de un negocio.
 * Permite agrupar los servicios ofertados (ej: "Peluquería", "Barbería", "Estética").
 * El nombre es único dentro de cada negocio.
 * Si se deja de usar una categoría, se marca como inactiva en lugar de borrarla,
 * para no romper los servicios que ya pertenecen a ella.
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

    /**
     * Negocio al que pertenece esta categoría.
     * Relación muchos-a-uno: un negocio puede tener varias categorías.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;
}