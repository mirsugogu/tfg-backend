package com.optima.api.modules.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que representa una ausencia o bloqueo en el calendario de un empleado.
 * Sobrescribe la disponibilidad habitual del empleado para evitar que
 * se le asignen citas durante este periodo (ej. vacaciones, cita médica).
 */
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

    /**
     * Empleado (usuario) que se ausenta.
     * La eliminación en cascada (ON DELETE CASCADE) está definida en BD,
     * pero aquí mapeamos la relación normalmente.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_employee", nullable = false)
    private User employee;

    /**
     * Fecha y hora en la que empieza la ausencia.
     */
    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDateTime;

    /**
     * Fecha y hora en la que termina la ausencia.
     */
    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDateTime;

    /**
     * Motivo de la ausencia (ej. "Cita médica", "Vacaciones").
     * Es opcional (nullable = true).
     */
    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Se ejecuta automáticamente antes de hacer INSERT en la BD.
     * Rellena la fecha de creación con el momento actual.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}