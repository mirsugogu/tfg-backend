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
 * servicio ofrecido por un negocio
 * nombre separado del servicio interno
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

    /** negocio al que pertenece este servicio */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** categoria a la que pertenece este servicio */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_category", nullable = false)
    private ServiceCategory category;

    /** impuesto aplicado al precio de este servicio */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tax", nullable = false)
    private Tax tax;

    /** nombre visible del servicio ej "corte de pelo" "tinte completo" */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** descripcion opcional del servicio */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** precio base del servicio sin impuesto aplicado */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** duracion del servicio en minutos debe ser mayor que 0 */
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /** indica si el servicio sigue disponible en el catalogo */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** fecha de creacion del servicio */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** momento de la desactivacion sin valor mientras el servicio este activo */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** asigna la fecha de creacion antes de guardar */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}