package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad que representa un bloqueo de agenda por dias completos.
 * Puede afectar a todo el negocio, a un empleado o a una cabina.
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

    /** Negocio al que pertenece el bloqueo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** Empleado afectado por el bloqueo, si aplica. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membership")
    private Membership membership;

    /** Cabina afectada por el bloqueo, si aplica. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_booth")
    private Booth booth;

    /** Primer dia bloqueado. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Ultimo dia bloqueado. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Etiqueta opcional ("San Isidro", "Vacaciones Ana", "Mantenimiento"). */
    @Column(name = "reason", length = 255)
    private String reason;

    /** Fecha y hora en que se creo el bloqueo (rellenado por @PrePersist). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Asigna la fecha de creacion antes de guardar. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
