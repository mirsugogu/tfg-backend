package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * horario semanal de apertura de un negocio
 * cada registro corresponde a un dia de la semana
 */
@Entity
@Table(name = "business_hours")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BusinessHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_business_hour")
    private Long id;

    /** negocio al que pertenece este tramo horario */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** dia de la semana: 1=lunes y 7=domingo */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    /** hora de apertura del negocio */
    @Column(name = "start_time")
    private LocalTime startTime;

    /** hora de cierre del negocio */
    @Column(name = "end_time")
    private LocalTime endTime;

    /** indica si el negocio cierra ese dia */
    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed = false;
}
