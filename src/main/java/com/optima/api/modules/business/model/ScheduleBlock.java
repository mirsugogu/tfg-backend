package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * bloqueo de agenda por dias completos
 * afecta a negocio empleado o cabina
 */
@Entity
@Table(name = "schedule_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_block")
    private Long id;

    /** negocio al que pertenece el bloqueo */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** empleado afectado por el bloqueo si aplica */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membership")
    private Membership membership;

    /** cabina afectada por el bloqueo si aplica */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_booth")
    private Booth booth;

    /** primer dia bloqueado */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** ultimo dia bloqueado */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** etiqueta opcional */
    @Column(name = "reason", length = 255)
    private String reason;

    /** fecha y hora en que se creo el bloqueo */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** asigna la fecha de creacion antes de guardar */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
