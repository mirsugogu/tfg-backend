package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Entidad que representa el horario de apertura semanal de un negocio,
 * con una fila por cada día de la semana.
 *
 * El campo {@code dayOfWeek} sigue la convención ISO: 1 = lunes, 7 = domingo.
 * Si {@code isClosed} es true, las horas pueden ser nulas (el negocio
 * está cerrado ese día). Si es false, {@code startTime} y {@code endTime}
 * deben estar informadas y respetar startTime &lt; endTime (lo asegura el
 * CHECK del schema SQL).
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

    /**
     * Negocio al que pertenece este tramo horario.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /**
     * Día de la semana (1 = lunes ... 7 = domingo).
     */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    /**
     * Hora de apertura. Puede ser nula si el negocio está cerrado ese día.
     */
    @Column(name = "start_time")
    private LocalTime startTime;

    /**
     * Hora de cierre. Puede ser nula si el negocio está cerrado ese día.
     */
    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * Indica si el negocio está cerrado ese día. Por defecto false.
     */
    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed = false;
}
