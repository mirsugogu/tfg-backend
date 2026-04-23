package com.optima.api.modules.user.model;

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
     * Usuario (empleado) al que pertenece este horario.
     * Relación muchos-a-uno: un empleado puede tener varios horarios.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    @Column(name = "day_of_week", nullable = false)
    private Byte dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}