package com.optima.api.modules.appointment.model;

import com.optima.api.modules.catalog.model.BusinessService;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * representa un servicio guardado dentro de una cita
 * mantiene el precio y el impuesto usados en ese momento
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

    /** cita a la que pertenece */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_appointment", nullable = false)
    private Appointment appointment;

    /** servicio que se reservo */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_service", nullable = false)
    private BusinessService service;

    /** precio usado al reservar */
    @Column(name = "applied_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal appliedPrice;

    /** impuesto usado al reservar */
    @Column(name = "applied_tax_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal appliedTaxPercentage;
}
