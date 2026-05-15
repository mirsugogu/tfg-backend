package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad que representa un "bloqueo de agenda": un rango de DIAS COMPLETOS
 * en los que el sistema no permite agendar citas.
 *
 * Tres tipos segun los FKs:
 *   - Global (festivo del negocio):   membership=null, booth=null.
 *   - Por empleado (vacaciones):      membership=X,    booth=null.
 *   - Por cabina (mantenimiento):     membership=null, booth=Y.
 *
 * Convive con EmployeeAbsence: ese cubre bloqueos POR HORAS dentro
 * de un dia ("la doctora esta en reunion de 10:00 a 12:00"); este cubre
 * dias completos ("vacaciones de Ana del 1 al 15 de agosto").
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, ScheduleBlockService.create.
 * - La consume: ScheduleBlockResponse.from(), AppointmentValidator
 *   (rechaza la cita si la fecha esta bloqueada).
 * - Tiene @ManyToOne con: Business, Membership (opcional),
 *   Booth (opcional).
 *
 * [v16 membership] El bloqueo "por empleado" apunta ahora a Membership
 * via id_membership (no a User directamente). Asi si una persona deja un
 * negocio sus bloqueos de agenda en otros negocios no se ven afectados.
 *
 * Mapea a la tabla `schedule_blocks` (docs/schema_v18.sql, anyadida en v15). Sin soft
 * delete: los bloqueos son eventos puntuales; si el ADMIN se equivoca,
 * borra y vuelve a crear.
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

    /**
     * Negocio al que pertenece el bloqueo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /**
     * Membership (empleado en este negocio) afectada (opcional).
     * Si null + booth null -> bloqueo global.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membership")
    private Membership membership;

    /**
     * Cabina afectada (opcional). Si null + employee null -> bloqueo global.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_booth")
    private Booth booth;

    /**
     * Primer dia bloqueado (incluido). El service valida startDate <= endDate.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Ultimo dia bloqueado (incluido). Para un bloqueo de un solo dia,
     * startDate == endDate.
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Etiqueta opcional ("San Isidro", "Vacaciones Ana", "Mantenimiento").
     */
    @Column(name = "reason", length = 255)
    private String reason;

    /**
     * Fecha y hora en que se creo el bloqueo (rellenado por @PrePersist).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Se ejecuta automaticamente antes de hacer INSERT en la BD.
     * Rellena la fecha de creacion con el momento actual.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
