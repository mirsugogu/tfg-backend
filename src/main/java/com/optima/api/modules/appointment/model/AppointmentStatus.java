package com.optima.api.modules.appointment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * representa un estado posible de una cita
 * se usa como catalogo comun
 */
@Entity
@Table(name = "appointment_statuses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_status")
    private Long id;

    @Column(name = "name", nullable = false, length = 30, unique = true)
    private String name;
}
