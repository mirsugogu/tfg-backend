package com.optima.api.modules.appointment.model;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.client.model.Client;
import com.optima.api.modules.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que representa una cita (reserva) de un cliente con un empleado de un negocio.
 * * Es la entidad central del sistema: los servicios se reservan a través de citas
 * (ver BookedService) y el flujo de una cita pasa por varios estados
 * (PENDING → CONFIRMED → IN_PROGRESS → COMPLETED, o CANCELLED / NO_SHOW).
 * * No utiliza soft delete porque su propio estado (id_status) cumple esa función:
 * las citas no se borran ni se desactivan, se marcan como CANCELLED o NO_SHOW.
 */
@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_appointment")
    private Long id;

    /**
     * Negocio al que pertenece la cita.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /**
     * Cliente que ha reservado la cita.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client", nullable = false)
    private Client client;

    /**
     * Empleado (usuario del negocio) que atiende la cita.
     * Normalmente será un usuario con rol EMPLOYEE, aunque un ADMIN
     * también puede atender citas si el negocio lo permite.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_employee", nullable = false)
    private User employee;

    /**
     * Estado actual de la cita (PENDING, CONFIRMED, IN_PROGRESS,
     * COMPLETED, CANCELLED o NO_SHOW).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_status", nullable = false)
    private AppointmentStatus status;

    /**
     * Control de pagos para los filtros del calendario.
     */
    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid = false;

    /**
     * Fecha y hora de inicio de la cita.
     */
    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDateTime;

    /**
     * Fecha y hora de fin de la cita.
     * Se calcula a partir de la suma de duraciones de los servicios reservados.
     */
    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDateTime;

    /**
     * Notas internas sobre la cita (TEXT: admite texto largo).
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Se ejecuta automáticamente antes de hacer INSERT en la BD.
     * Rellena la fecha de creación con el momento actual.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}