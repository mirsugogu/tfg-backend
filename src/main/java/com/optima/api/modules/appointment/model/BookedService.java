package com.optima.api.modules.appointment.model;

import com.optima.api.modules.catalog.model.BusinessService;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Entidad que representa un servicio reservado dentro de una cita.
 * Guarda el precio y el impuesto aplicados en el momento de la reserva.
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

    /** Cita a la que pertenece este servicio reservado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_appointment", nullable = false)
    private Appointment appointment;

    /** Servicio del catalogo que se esta reservando. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_service", nullable = false)
    private BusinessService service;

    /** Precio aplicado al crear la cita. */
    @Column(name = "applied_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal appliedPrice;

    /** Porcentaje de impuesto aplicado al crear la cita. */
    @Column(name = "applied_tax_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal appliedTaxPercentage;
}