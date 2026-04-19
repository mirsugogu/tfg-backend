package com.optima.api.bookedservice;

import com.optima.api.appointment.Appointment;
import com.optima.api.businessservice.BusinessService;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Entidad puente entre Appointment y BusinessService.
 * Representa un servicio concreto reservado dentro de una cita concreta.

 * Una cita puede incluir varios servicios, y un servicio puede aparecer
 * en muchas citas distintas (relación muchos-a-muchos).

 * Los campos applied_price y applied_tax_percentage se "congelan" en el
 * momento de la reserva: guardan el precio y el porcentaje de impuesto
 * que tenía el servicio cuando se creó la cita. De esta forma, si más
 * adelante se cambia el precio del servicio o el porcentaje del impuesto,
 * las citas históricas siguen mostrando los valores originales.

 * Se nombra BookedService (no AppointmentService) para evitar confusión
 * con la capa de lógica de negocio AppointmentService.
 */
@Entity
@Table(name = "appointment_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookedService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_appointment_service")
    private Long id;

    /**
     * Cita a la que pertenece este servicio reservado.
     * Si se borra la cita, la BD borra automáticamente los BookedService
     * asociados (ON DELETE CASCADE en el schema SQL).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_appointment", nullable = false)
    private Appointment appointment;

    /**
     * Servicio del negocio que se está reservando.
     * Se mantiene la relación para poder consultar el servicio original
     * (nombre, categoría, etc.), aunque los valores económicos se leen
     * de los campos "applied" de esta misma entidad.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_service", nullable = false)
    private BusinessService service;

    /**
     * Precio aplicado en el momento de la reserva (congelado).
     * No se modifica aunque cambie el precio del servicio original.
     */
    @Column(name = "applied_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal appliedPrice;

    /**
     * Porcentaje de impuesto aplicado en el momento de la reserva (congelado).
     * No se modifica aunque cambie el porcentaje del impuesto original.
     */
    @Column(name = "applied_tax_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal appliedTaxPercentage;
}