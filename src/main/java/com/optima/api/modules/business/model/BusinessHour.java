package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Entidad que representa el horario de apertura semanal de un negocio,
 * con una fila por cada dia de la semana.
 *
 * El campo dayOfWeek sigue la convencion ISO: 1 = lunes, 7 = domingo.
 * Si isClosed es true, las horas pueden ser nulas (el negocio
 * esta cerrado ese dia). Si es false, startTime y endTime
 * deben estar informadas y respetar startTime < endTime (lo asegura el
 * CHECK del schema SQL).
 *
 * Sin createdAt: BusinessHour es configuracion estatica del negocio
 * (un tramo por dia, editable), no un evento que ocurre. Misma decision
 * que EmployeeSchedule. No utiliza soft delete tampoco (hard delete):
 * si un tramo deja de aplicar, se borra y se vuelve a crear.
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, BusinessHourService.create
 *   manualmente.
 * - La consume: BusinessHourResponse.from(), AvailabilityService (resuelve
 *   las horas validas del dia).
 * - Tiene @ManyToOne con: Business.
 *
 * Mapea a `business_hours` (docs/schema_v20.sql). Anteriormente
 * day_of_week era TINYINT y rompia la validacion de Hibernate
 * (Integer en JPA); se cambio a INT en el schema para alinear.
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
