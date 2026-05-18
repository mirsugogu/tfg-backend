package com.optima.api.modules.business.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que representa una "cabina" (booth) de un negocio: un espacio
 * fisico donde se realiza la cita. Ejemplos: silla de peluqueria, sala de
 * consulta, bahia de taller, cabina de estetica.
 *
 * Es una restriccion fisica INDEPENDIENTE del empleado: dos empleados
 * libres no sirven si solo hay una cabina libre. Para una cita el sistema
 * exige (cabina libre) AND (empleado libre) en el mismo tramo.
 *
 * No utiliza @OneToMany inverso a Appointment: el lado dueno esta en
 * Appointment.booth (@ManyToOne, nullable). Una cita puede no tener
 * cabina si el negocio no las ha configurado.
 *
 * Soft delete (is_active + deactivated_at): una cabina con citas
 * historicas se desactiva, nunca se borra fisicamente. Alineado con el
 * patron de businesses, users, clients, taxes, services y service_categories.
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, BoothService.create manualmente.
 * - La consume: BoothResponse.from(), AppointmentService (cross-tenant +
 *   activo + overlap check al crear cita).
 * - Tiene @ManyToOne con: Business.
 * - Es referenciada por: Appointment.booth (@ManyToOne, nullable).
 *
 * Mapea a la tabla `booths` (docs/schema_v20.sql, anyadida en v14). El
 * uniqueConstraint uq_booth_business_name (id_business, name) impide que
 * dos cabinas del mismo negocio compartan nombre, pero "Sala 1" si puede
 * coexistir en negocios distintos.
 */
@Entity
@Table(
        name = "booths",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_booth_business_name",
                columnNames = {"id_business", "name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Booth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_booth")
    private Long id;

    /**
     * Negocio al que pertenece esta cabina.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /**
     * Nombre visible de la cabina (ej. "Sala 1", "Silla A"). Unico
     * dentro del mismo negocio.
     */
    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /**
     * Flag de soft delete: false oculta la cabina del listado activo
     * y la excluye de la asignacion de citas, pero conserva las citas
     * historicas que la referencian.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Fecha y hora en que se creo la cabina (rellenado por @PrePersist).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Momento de la desactivacion (null mientras la cabina este activa).
     */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /**
     * Se ejecuta automaticamente antes de hacer INSERT en la BD.
     * Rellena la fecha de creacion con el momento actual.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
