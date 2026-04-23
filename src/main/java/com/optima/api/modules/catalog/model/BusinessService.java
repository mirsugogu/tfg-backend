package com.optima.api.modules.catalog.model;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Tax;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa un servicio ofertado por un negocio
 * (ej: "Corte de pelo", "Tinte", "Manicura").

 * Cada servicio pertenece a un negocio, está asignado a una categoría
 * y lleva un impuesto aplicado. El precio se guarda como BigDecimal
 * para garantizar precisión exacta en cálculos monetarios.

 * Si se deja de ofrecer un servicio, se marca como inactivo en lugar
 * de borrarlo, para no romper las citas históricas que ya lo referencian.

 * Se nombra BusinessService (no Service) para evitar confusión con la
 * anotación @Service de Spring, que se usa en la capa de lógica de negocio.
 */
@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BusinessService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_service")
    private Long id;

    /**
     * Negocio al que pertenece este servicio.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /**
     * Categoría a la que pertenece este servicio.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_category", nullable = false)
    private ServiceCategory category;

    /**
     * Impuesto aplicado al precio de este servicio.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tax", nullable = false)
    private Tax tax;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /**
     * Descripción opcional del servicio (TEXT: admite texto largo).
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Precio del servicio (sin impuesto aplicado).
     * DECIMAL(10, 2): hasta 99.999.999,99 €.
     */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Duración del servicio en minutos. Debe ser mayor que 0.
     */
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;
}