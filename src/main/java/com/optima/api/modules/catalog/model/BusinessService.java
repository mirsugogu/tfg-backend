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
 * Entidad que representa un servicio ofrecido por un negocio.
 * Se llama BusinessService para no confundirse con @Service de Spring.
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

    /** Negocio al que pertenece este servicio. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** Categoría a la que pertenece este servicio. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_category", nullable = false)
    private ServiceCategory category;

    /** Impuesto aplicado al precio de este servicio. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tax", nullable = false)
    private Tax tax;

    /** Nombre visible del servicio (ej. "Corte de pelo", "Tinte completo"). */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Descripción opcional del servicio (TEXT: admite texto largo). */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Precio base del servicio, sin impuesto aplicado. */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Duración del servicio en minutos. Debe ser mayor que 0. */
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /** Indica si el servicio sigue disponible en el catalogo. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Fecha y hora en que se creo el servicio (rellenado por @PrePersist). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Momento de la desactivacion (null mientras el servicio este activo). */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** Asigna la fecha de creacion antes de guardar. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}