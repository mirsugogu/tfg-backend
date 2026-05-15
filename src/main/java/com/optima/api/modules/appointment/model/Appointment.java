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
 * Entidad que representa una cita (reserva) de un cliente con un empleado de un negocio.
 *
 * Es la entidad central del sistema: los servicios se reservan a través de citas
 * (ver BookedService) y el flujo de una cita pasa por varios estados
 * (PENDING → CONFIRMED → IN_PROGRESS → COMPLETED, o CANCELLED / NO_SHOW).
 *
 * No utiliza soft delete porque su propio estado (id_status) cumple esa función:
 * las citas no se borran ni se desactivan, se marcan como CANCELLED o NO_SHOW.
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, AppointmentService.createAppointment
 *   manualmente.
 * - La consume: AppointmentResponse.from(), AppointmentService (queries
 *   y validaciones), AppointmentValidator.
 * - Tiene relaciones @ManyToOne con: Business, Client, User (employee),
 *   AppointmentStatus.
 * - Tiene relacion uno-a-muchos (no @OneToMany declarada explicitamente)
 *   con BookedService via id_appointment.
 *
 * Mapea a la tabla `appointments` (docs/schema_v18.sql) con FKs a
 * businesses, clients, users y appointment_statuses. Los bookedServices
 * estan en `appointment_services` con ON DELETE CASCADE.
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
     * Membership (usuario en este negocio con su rol) que atiende la cita.
     * [v16 membership] Antes apuntaba a User directamente; ahora apunta a
     * Membership para soportar que un mismo email trabaje en varios
     * negocios sin mezclar sus citas. El identificador externo se sigue
     * llamando "membershipId" en la API por compatibilidad, pero
     * internamente es el membership_id.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membership", nullable = false)
    private Membership membership;

    /**
     * Cabina (espacio fisico) donde se realiza la cita. Nullable: una
     * cita puede no tener cabina si el negocio no las usa o si el servicio
     * no la requiere. Cuando la cita tiene cabina, el service valida que
     * no esta ocupada en ese tramo (overlap check ortogonal al del empleado).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_booth")
    private Booth booth;

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

    /**
     * Fecha y hora en que se creo la cita (rellenado por @PrePersist).
     */
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