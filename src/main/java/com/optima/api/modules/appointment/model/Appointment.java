package com.optima.api.modules.appointment.model;

import com.optima.api.modules.business.model.Booth;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.client.model.Client;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que representa una cita de un cliente con un empleado.
 * El estado de la cita sustituye al borrado logico.
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

    /** Negocio al que pertenece la cita. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** Cliente que ha reservado la cita. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client", nullable = false)
    private Client client;

    /** Membership (usuario en este negocio con su rol) que atiende la cita. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membership", nullable = false)
    private Membership membership;

    /** Cabina donde se realiza la cita, si aplica. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_booth")
    private Booth booth;

    /** Estado actual de la cita. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_status", nullable = false)
    private AppointmentStatus status;

    /** Control de pagos para los filtros del calendario. */
    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid = false;

    /** Fecha y hora de inicio de la cita. */
    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDateTime;

    /** Fecha y hora de fin de la cita. */
    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDateTime;

    /** Notas internas sobre la cita (TEXT: admite texto largo). */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Fecha y hora en que se creo la cita (rellenado por @PrePersist). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Fecha y hora de la ultima modificacion. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Asigna la fecha de creacion antes de guardar. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /** Actualiza la fecha de modificacion antes de guardar cambios. */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}