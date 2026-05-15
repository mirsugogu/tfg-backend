package com.optima.api.modules.user.model;

import com.optima.api.modules.business.model.Membership;
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
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, EmployeeAbsenceService.create
 *   manualmente.
 * - La consume: EmployeeAbsenceResponse.from(), EmployeeAbsenceService,
 *   AvailabilityService (restar ausencias a los tramos libres del dia).
 * - Tiene @ManyToOne con: Membership (pertenencia usuario-negocio).
 *
 * [v16 membership] Antes apuntaba a User directamente con FK
 * `id_employee`; ahora apunta a Membership via `id_membership` para que
 * la ausencia sea por (usuario, negocio). Asi un mismo email que trabaja
 * en dos negocios puede tener ausencias distintas en cada uno.
 *
 * Mapea a `employee_absences` con FK ON DELETE CASCADE: si la membership
 * se borra, sus ausencias desaparecen automaticamente. (En la practica
 * las memberships se desactivan, no se borran, asi que el cascade es solo
 * defensa en profundidad.)
 *
 * Hard delete: una ausencia se cancela borrandola; no se conserva
 * historico de ausencias canceladas porque no aporta valor de auditoria
 * al negocio.
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
     * Membership (usuario en este negocio) a la que pertenece la ausencia.
     * La eliminación en cascada (ON DELETE CASCADE) está definida en BD.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membership", nullable = false)
    private Membership membership;

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