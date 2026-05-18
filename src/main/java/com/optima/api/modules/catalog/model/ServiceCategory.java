package com.optima.api.modules.catalog.model;

import com.optima.api.modules.business.model.Business;
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
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, ServiceCategoryService.create
 *   manualmente.
 * - La consume: ServiceCategoryResponse.from(), BusinessServiceService (al
 *   crear/editar un servicio).
 * - Es referenciada por: BusinessService.category (@ManyToOne).
 *
 * uniqueConstraints uq_category_business_name (id_business, name): la
 * BD impide dos categorias con el mismo nombre en el mismo negocio.
 *
 * Mapea a la tabla `service_categories` (docs/schema_v20.sql). El campo
 * createdAt se anadio en v20 para alinear con la regla 7 del patron
 * canonico de entidad (@PrePersist para createdAt); paralelo a la
 * migracion v18 que lo introdujo en taxes.
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

    /**
     * Nombre visible de la categoria (ej. "Peluqueria", "Estetica"). Unico
     * dentro del mismo negocio (uniqueConstraint uq_category_business_name).
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Flag de soft delete: false oculta la categoria del listado activo y la
     * excluye al crear/editar servicios, pero conserva los servicios
     * historicos que la referencian.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Fecha y hora en que se creo la categoria (rellenado por @PrePersist).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Momento de la desactivacion (null mientras la categoria este activa).
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