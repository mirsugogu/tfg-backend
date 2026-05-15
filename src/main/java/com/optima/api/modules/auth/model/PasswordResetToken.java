package com.optima.api.modules.auth.model;

import com.optima.api.modules.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad que representa un token efimero para reset de password.
 *
 * Cuando un usuario olvida su password, llama a
 * POST /api/auth/forgot-password con su email. El servicio
 * crea una fila aqui con el SHA-256 del token plano (que se envia por
 * email en el link). El usuario clica el link, llega a
 * POST /api/auth/reset-password con el token plano, el servicio
 * vuelve a hashear y busca por hash. Si existe, no caduco y no se uso,
 * se permite el cambio de password.
 *
 * Mapea a la tabla password_resets (docs/schema_v18.sql):
 *   id_reset (PK)
 *   id_user (FK -> users, CASCADE)
 *   token_hash (UNIQUE) - SHA-256(rawToken) en hex
 *   expires_at (DATETIME) - now()+1h al crear
 *   used_at (DATETIME, nullable) - se rellena al consumirse
 *   created_at (DATETIME)
 *
 * El password es de la identidad GLOBAL (users), no de una
 * membership: un reset afecta a todos los negocios donde la persona
 * es miembro.
 *
 * COMUNICACION:
 * - La instancia: PasswordResetService al recibir forgot-password.
 * - La consume: PasswordResetService al validar reset-password.
 * - Tiene @ManyToOne con: User.
 */
@Entity
@Table(name = "password_resets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reset")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
