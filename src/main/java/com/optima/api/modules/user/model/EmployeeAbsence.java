package com.optima.api.modules.user.model;

import com.optima.api.modules.business.model.Membership;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Entidad que representa una ausencia puntual de un empleado. */
@Entity
@Table(name = "employee_absences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeAbsence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_absence")
    private Long id;

    /** Membership a la que pertenece la ausencia. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membership", nullable = false)
    private Membership membership;

    /** Fecha y hora en la que empieza la ausencia. */
    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDateTime;

    /** Fecha y hora en la que termina la ausencia. */
    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDateTime;

    /** Motivo opcional de la ausencia. */
    @Column(name = "reason", length = 255)
    private String reason;

    /** Fecha y hora de creacion de la ausencia. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Asigna la fecha de creacion antes de guardar. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}