package com.optima.api.modules.user.model;

import com.optima.api.modules.business.model.Membership;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Entidad que representa el horario semanal de trabajo de un empleado.
 * Cada fila indica un tramo horario de un día concreto (ej: lunes de 09:00 a 14:00).
 * Un empleado puede tener varios horarios (varios tramos en un mismo día o en días distintos).
 * day_of_week: 1 = lunes, 2 = martes, ..., 7 = domingo.
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, EmployeeScheduleService.create
 *   manualmente.
 * - La consume: EmployeeScheduleResponse.from(), AppointmentValidator.validateEmployeeSchedule
 *   (verifica que la cita cae dentro de uno de los tramos).
 * - Tiene @ManyToOne con: Membership (pertenencia usuario-negocio).
 *
 * [v16 membership] Antes apuntaba directamente a User; ahora apunta a
 * Membership para que el horario sea por (usuario, negocio) y no por
 * usuario global. Un mismo email puede trabajar en dos negocios con
 * horarios distintos en cada uno.
 *
 * Sin uniqueConstraint en (membership, dayOfWeek) porque un empleado
 * puede tener turno partido (ej: Lunes 09-13 + Lunes 16-20). El service
 * valida overlap entre tramos del mismo dia.
 *
 * Hard delete + sin createdAt: un horario se reemplaza, no se conserva
 * historico. A diferencia de EmployeeAbsence (que registra un evento
 * puntual en el tiempo), un tramo de horario es configuracion
 * estructural; si cambia, se sustituye y el anterior carece de valor
 * historico.
 */
@Entity
@Table(name = "employee_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_schedule")
    private Long id;

    /**
     * Membership (usuario-en-negocio) a la que pertenece este horario.
     * Relación muchos-a-uno: una membership puede tener varios horarios.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membership", nullable = false)
    private Membership membership;

    /**
     * Dia de la semana (1=lunes, 2=martes, ..., 7=domingo).
     */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    /**
     * Hora de inicio del tramo (formato HH:mm).
     */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /**
     * Hora de fin del tramo. El service valida startTime < endTime.
     */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}