package com.optima.api.modules.client.model;

import com.optima.api.modules.business.model.Business;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que representa un cliente final de un negocio (la persona que reserva citas).
 * Cada cliente pertenece a un único negocio (multi-tenant).
 * Si se deja de atender a un cliente, se marca como inactivo en lugar de borrarlo,
 * para no romper el histórico de citas asociadas.
 *
 * COMUNICACION:
 * - La instancia: Hibernate al hidratar, ClientService.create manualmente.
 * - La consume: ClientResponse.from(), AppointmentService (verifica
 *   activo + cross-tenant antes de crear cita).
 * - Tiene @ManyToOne con: Business.
 * - Es referenciada por: Appointment.client (@ManyToOne) - cada cita
 *   apunta a un cliente.
 *
 * Mapea a la tabla `clients` (docs/schema_v20.sql). Email y telefono
 * son opcionales: dos clientes del mismo negocio pueden compartir email
 * (familias, etc.).
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_client")
    private Long id;

    /**
     * Negocio al que pertenece este cliente.
     * Relación muchos-a-uno: un negocio puede tener muchos clientes.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_business", nullable = false)
    private Business business;

    /**
     * Nombre completo del cliente (obligatorio).
     */
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /**
     * Email de contacto (opcional). Dos clientes del mismo negocio pueden
     * compartir email (familias, etc.) — no es UNIQUE.
     */
    @Column(name = "email", length = 150)
    private String email;

    /**
     * Teléfono de contacto (opcional).
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * Notas internas del negocio sobre el cliente (alergias, preferencias, etc.).
     * Columna TEXT: admite texto largo sin límite de VARCHAR.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Flag de soft delete: false oculta al cliente del listado activo y lo
     * excluye de poder reservar citas nuevas, pero conserva las citas
     * historicas que lo referencian.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Fecha y hora en que se creo el cliente (rellenado por @PrePersist).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Momento de la desactivacion (null mientras el cliente este activo).
     */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /**
     * Se ejecuta automáticamente antes de hacer INSERT en la BD.
     * Rellena la fecha de creación con el momento actual.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}