package com.optima.api.modules.user.model;

import com.optima.api.modules.business.model.Membership;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/** tramo semanal de trabajo de un empleado */
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

    /** relacion a la que pertenece este horario */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membership", nullable = false)
    private Membership membership;

    /** dia de la semana: 1=lunes y 7=domingo */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    /** hora de inicio del tramo */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** hora de fin del tramo */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}