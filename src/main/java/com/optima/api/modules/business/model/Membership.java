package com.optima.api.modules.business.model;

import com.optima.api.modules.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que representa la relacion (usuario, negocio, rol).
 *
 * Hasta v15 el modelo era "un usuario pertenece a UN negocio con UN rol".
 * Desde v16, la identidad (User) se separa de la pertenencia: un mismo
 * email puede tener varias memberships, una por cada negocio en el que
 * trabaje, con un rol distinto en cada uno.
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, AuthService.register /
 *   UserService.create al crear empleado de negocio.
 * - La consumen: AppointmentService (membershipId del request es realmente
 *   membershipId), EmployeeScheduleService, EmployeeAbsenceService,
 *   ScheduleBlockService, UserResponse.
 *
 * Es referenciada por:
 *   employee_schedules.id_membership (FK)
 *   employee_absences.id_membership  (FK)
 *   appointments.id_membership       (FK)
 *   schedule_blocks.id_membership    (FK, nullable)
 *
 * UNIQUE(id_user, id_business): un usuario no puede tener dos memberships
 * en el mismo negocio (si quiere cambiar de rol, se edita la membership;
 * no se crea otra).
 *
 * Soft delete: la membership se desactiva (is_active=false) cuando un
 * empleado deja un negocio, asi se preservan las citas historicas que la
 * referenciaban. El User queda intacto y sus otras memberships tampoco
 * se ven afectadas.
 *
 * Mapea a la tabla `memberships` (docs/schema_v18.sql, anyadida en v16).
 * Sin deactivated_at: solo se necesita el flag is_active para excluir las
 * memberships inactivas del login y de los listados activos; la fecha de
 * cese del empleo no es informacion que el sistema necesite conservar.
 */
@Entity
@Table(
        name = "memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_membership_user_business",
                columnNames = {"id_user", "id_business"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_membership")
    private Long id;

    /** Identidad: a que persona pertenece esta membresia. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    /** Negocio (tenant) donde el usuario tiene la membresia. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /** Rol del usuario en este negocio (ADMIN o EMPLOYEE). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_role", nullable = false)
    private Role role;

    /**
     * Flag de soft delete: false significa que el usuario ya no trabaja
     * en este negocio (excluido del login y de los listados activos), pero
     * sus citas historicas y bloqueos conservan la referencia.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Fecha y hora en que se creo la membresia (rellenado por @PrePersist).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Se ejecuta automaticamente antes de hacer INSERT en la BD.
     * Rellena la fecha de creacion con el momento actual.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
