package com.optima.api.modules.appointment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad que representa un estado posible de una cita.
 * Catálogo global (compartido por todos los negocios).
 * Valores insertados en el schema SQL:
 *   PENDING      → cita creada, pendiente de confirmación.
 *   CONFIRMED    → cita confirmada por el negocio.
 *   IN_PROGRESS  → cita en curso (el cliente está siendo atendido).
 *   COMPLETED    → cita finalizada correctamente.
 *   CANCELLED    → cita cancelada antes de su inicio.
 *   NO_SHOW      → el cliente no se presentó.
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar.
 * - La consume: AppointmentStatusResponse.from(), AppointmentService
 *   (lookup por nombre al crear/transicionar), AppointmentValidator
 *   (mapa de transiciones validas usa los nombres como string).
 *
 * Las transiciones validas estan codificadas en
 * AppointmentValidator.VALID_TRANSITIONS (mapa estatico). NO se modela
 * en BD porque el set es pequeno y estable.
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