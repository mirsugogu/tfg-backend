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
 * representa una cita del negocio
 * guarda cliente empleado estado y horario
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

    /** negocio al que pertenece la cita */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** cliente que reserva la cita */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client", nullable = false)
    private Client client;

    /** empleado que atiende la cita */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_membership", nullable = false)
    private Membership membership;

    /** cabina usada cuando corresponde */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_booth")
    private Booth booth;

    /** estado actual de la cita */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_status", nullable = false)
    private AppointmentStatus status;

    /** marca si la cita esta pagada */
    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid = false;

    /** fecha y hora de inicio */
    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDateTime;

    /** fecha y hora de fin */
    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDateTime;

    /** notas internas de la cita */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** fecha de creacion */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** fecha de ultima modificacion */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** pone la fecha al crear */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /** actualiza la fecha al guardar cambios */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
