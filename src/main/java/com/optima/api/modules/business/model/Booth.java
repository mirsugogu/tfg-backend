package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * cabina o espacio fisico del negocio
 * para controlar que dos citas no ocupen la misma cabina
 */
@Entity
@Table(
        name = "booths",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_booth_business_name",
                columnNames = {"id_business", "name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Booth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_booth")
    private Long id;

    /** negocio al que pertenece esta cabina */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** nombre visible de la cabina */
    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** color usado por la interfaz para mostrar la cabina */
    @Column(name = "color")
    private String color;

    /** indica si la cabina sigue disponible */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** fecha y hora en que se creo la cabina */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** momento de la desactivacion */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** asigna la fecha de creacion antes de guardar */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
