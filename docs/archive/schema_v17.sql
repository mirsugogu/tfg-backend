-- ============================================================
-- Optima SaaS - Database Schema v16
-- Multi-tenant (Shared DB, Shared Schema)
-- No superadmins | Taxes per business | English naming
--
-- Diferencias respecto a v13:
--   [v14 cabinas] Tabla nueva `booths` (espacio fisico tenant-scoped).
--   [v14 cabinas] Columna `appointments.id_booth` (nullable, FK -> booths).
--   [v14 cabinas] Seed: 2 cabinas demo para business 1 ("Sala 1", "Sala 2").
--
-- Diferencias respecto a v14:
--   [v15 bloqueos] Tabla nueva `schedule_blocks` (dias completos en los
--                  que NO se permite agendar citas: festivos globales,
--                  vacaciones por empleado, mantenimiento por cabina).
--                  Convive con `employee_absences` (ese sigue cubriendo
--                  el caso de bloqueos parciales por horas).
--   [v15 bloqueos] Seed: 1 bloqueo global de prueba para business 1
--                  ("San Isidro" del 2027-05-15).
--
-- Diferencias respecto a v15 (REFACTOR ARQUITECTONICO):
--   [v16 membership] users desacoplado del negocio:
--                    - Quitadas las columnas id_business + id_role de users.
--                    - Cambiado el UNIQUE(id_business, email) por UNIQUE(email)
--                      global. Un email = una sola identidad.
--   [v16 membership] Tabla nueva `memberships` (id_user, id_business, id_role,
--                    is_active). Modela que un usuario puede pertenecer a N
--                    negocios con N roles. UNIQUE(id_user, id_business).
--   [v16 membership] FKs reapuntadas a memberships:
--                    - employee_schedules.id_user    -> id_membership
--                    - employee_absences.id_employee -> id_membership
--                    - appointments.id_employee      -> id_membership
--                    Asi los horarios, ausencias y citas son por relacion
--                    (usuario en negocio) y NO por identidad global.
--   [v16 membership] Seed migrado: por cada user existente se crea 1
--                    membership con su (id_business, id_role) anteriores.
-- ============================================================

DROP DATABASE IF EXISTS optima_db;
CREATE DATABASE optima_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE optima_db;


-- ------------------------------------------------------------
-- 1. BUSINESSES (tenants of the SaaS platform)
-- ------------------------------------------------------------
CREATE TABLE businesses (
                            id_business          BIGINT AUTO_INCREMENT PRIMARY KEY,
                            name                 VARCHAR(150)  NOT NULL,
                            slug                 VARCHAR(150)  NOT NULL UNIQUE,
                            email                VARCHAR(150)  NOT NULL UNIQUE,
                            phone                VARCHAR(20),

    -- Campos de dirección desglosados y geolocalización
                            address              VARCHAR(255),
                            city                 VARCHAR(100),
                            state                VARCHAR(100),
                            country              VARCHAR(100),
                            postal_code          VARCHAR(20),
                            latitude             DECIMAL(10, 8),
                            longitude            DECIMAL(11, 8),

    -- Configuración operativa
                            appointment_interval INT           NOT NULL DEFAULT 30,
                            is_active            BOOLEAN       NOT NULL DEFAULT TRUE,
                            created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            deactivated_at       DATETIME      NULL,

                            CONSTRAINT chk_appointment_interval
                                CHECK (appointment_interval IN (15, 30, 45, 60))
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 2. ROLES (global catalog: ADMIN, EMPLOYEE)
-- ------------------------------------------------------------
CREATE TABLE roles (
                       id_role BIGINT AUTO_INCREMENT PRIMARY KEY,
                       name    VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT INTO roles (name) VALUES
                             ('ADMIN'),
                             ('EMPLOYEE');

-- ------------------------------------------------------------
-- 3. USERS (identidad global; pertenece a N negocios via memberships)
-- [v16 membership] users deja de ser por-tenant. Quitadas id_business
-- y id_role; el email es ahora unique GLOBAL (una persona = una identidad,
-- aunque trabaje en varios negocios). La relacion (usuario, negocio, rol)
-- vive en la tabla `memberships`.
-- ------------------------------------------------------------
CREATE TABLE users (
                       id_user        BIGINT       AUTO_INCREMENT PRIMARY KEY,
                       full_name      VARCHAR(150) NOT NULL,
                       email          VARCHAR(150) NOT NULL UNIQUE,
                       password_hash  VARCHAR(255) NOT NULL,
                       phone          VARCHAR(20),
                       is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       deactivated_at DATETIME     NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- [v16 membership] 3.5. MEMBERSHIPS (relacion usuario <-> negocio + rol)
-- Una membership representa "el usuario X es ROLE en el negocio Y".
-- Un mismo usuario puede tener varias memberships (empleado en
-- peluqueria A + admin en clinica B). El login devuelve identity token,
-- y el endpoint /api/auth/select-business/{id} intercambia esa identity
-- por un tenant token con (businessId, role) embebidos.
-- ------------------------------------------------------------
CREATE TABLE memberships (
                            id_membership BIGINT   AUTO_INCREMENT PRIMARY KEY,
                            id_user       BIGINT   NOT NULL,
                            id_business   BIGINT   NOT NULL,
                            id_role       BIGINT   NOT NULL,
                            is_active     BOOLEAN  NOT NULL DEFAULT TRUE,
                            created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT fk_membership_user
                                FOREIGN KEY (id_user) REFERENCES users(id_user),
                            CONSTRAINT fk_membership_business
                                FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                            CONSTRAINT fk_membership_role
                                FOREIGN KEY (id_role) REFERENCES roles(id_role),
                            CONSTRAINT uq_membership_user_business
                                UNIQUE (id_user, id_business)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 4. EMPLOYEE SCHEDULES (weekly working hours)
-- day_of_week: 1=Monday, 2=Tuesday, ..., 7=Sunday
-- [v16 membership] id_user -> id_membership. El horario laboral pertenece
-- a la relacion (usuario, negocio): si el mismo usuario trabaja en dos
-- negocios distintos, tendra dos sets de horarios independientes.
-- ------------------------------------------------------------
CREATE TABLE employee_schedules (
                                    id_schedule   BIGINT  AUTO_INCREMENT PRIMARY KEY,
                                    id_membership BIGINT  NOT NULL,
                                    day_of_week   INT     NOT NULL,
                                    start_time    TIME    NOT NULL,
                                    end_time      TIME    NOT NULL,
                                    CONSTRAINT fk_schedule_membership
                                        FOREIGN KEY (id_membership) REFERENCES memberships(id_membership),
                                    CONSTRAINT chk_day_of_week
                                        CHECK (day_of_week BETWEEN 1 AND 7),
                                    CONSTRAINT chk_schedule_times
                                        CHECK (start_time < end_time)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 5. TAXES (each business defines its own taxes)
-- ------------------------------------------------------------
CREATE TABLE taxes (
                       id_tax         BIGINT        AUTO_INCREMENT PRIMARY KEY,
                       id_business    BIGINT        NOT NULL,
                       name           VARCHAR(50)   NOT NULL,
                       percentage     DECIMAL(5,2)  NOT NULL,
                       is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
                       deactivated_at DATETIME      NULL,
                       CONSTRAINT fk_tax_business
                           FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                       CONSTRAINT uq_tax_business_name
                           UNIQUE (id_business, name),
                       CONSTRAINT chk_tax_percentage
                           CHECK (percentage >= 0 AND percentage <= 100)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 6. CLIENTS (customers of each business)
-- ------------------------------------------------------------
CREATE TABLE clients (
                         id_client      BIGINT       AUTO_INCREMENT PRIMARY KEY,
                         id_business    BIGINT       NOT NULL,
                         full_name      VARCHAR(150) NOT NULL,
                         email          VARCHAR(150),
                         phone          VARCHAR(20),
                         notes          TEXT,
                         is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
                         created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         deactivated_at DATETIME     NULL,
                         CONSTRAINT fk_client_business
                             FOREIGN KEY (id_business) REFERENCES businesses(id_business)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 7. SERVICE CATEGORIES (grouping of services per business)
-- ------------------------------------------------------------
CREATE TABLE service_categories (
                                    id_category    BIGINT       AUTO_INCREMENT PRIMARY KEY,
                                    id_business    BIGINT       NOT NULL,
                                    name           VARCHAR(100) NOT NULL,
                                    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
                                    deactivated_at DATETIME     NULL,
                                    CONSTRAINT fk_category_business
                                        FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                                    CONSTRAINT uq_category_business_name
                                        UNIQUE (id_business, name)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 8. SERVICES (offered by each business)
-- ------------------------------------------------------------
CREATE TABLE services (
                          id_service       BIGINT        AUTO_INCREMENT PRIMARY KEY,
                          id_business      BIGINT        NOT NULL,
                          id_category      BIGINT        NOT NULL,
                          id_tax           BIGINT        NOT NULL,
                          name             VARCHAR(150)  NOT NULL,
                          description      TEXT,
                          price            DECIMAL(10,2) NOT NULL,
                          duration_minutes INT           NOT NULL,
                          is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
                          deactivated_at   DATETIME      NULL,
                          CONSTRAINT fk_service_business
                              FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                          CONSTRAINT fk_service_category
                              FOREIGN KEY (id_category) REFERENCES service_categories(id_category),
                          CONSTRAINT fk_service_tax
                              FOREIGN KEY (id_tax) REFERENCES taxes(id_tax),
                          CONSTRAINT chk_service_price
                              CHECK (price >= 0),
                          CONSTRAINT chk_service_duration
                              CHECK (duration_minutes > 0)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- [v14 cabinas] 8.5. BOOTHS (espacios fisicos tenant-scoped)
-- Una "cabina" es un espacio donde se realiza la cita (sala, silla, bahia,
-- box). Es una restriccion fisica independiente del empleado: dos
-- empleados libres no sirven si solo hay una cabina libre.
-- ------------------------------------------------------------
CREATE TABLE booths (
                        id_booth       BIGINT       AUTO_INCREMENT PRIMARY KEY,
                        id_business    BIGINT       NOT NULL,
                        name           VARCHAR(80)  NOT NULL,
                        is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
                        deactivated_at DATETIME     NULL,
                        created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_booth_business
                            FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                        CONSTRAINT uq_booth_business_name
                            UNIQUE (id_business, name)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 9. APPOINTMENT STATUSES (global catalog)
-- State flow: PENDING -> CONFIRMED -> IN_PROGRESS -> COMPLETED
-- Alternative ends: CANCELLED, NO_SHOW
-- ------------------------------------------------------------
CREATE TABLE appointment_statuses (
                                      id_status BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      name      VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT INTO appointment_statuses (name) VALUES
                                            ('PENDING'),
                                            ('CONFIRMED'),
                                            ('IN_PROGRESS'),
                                            ('COMPLETED'),
                                            ('CANCELLED'),
                                            ('NO_SHOW');


-- ------------------------------------------------------------
-- 10. APPOINTMENTS
-- id_employee: the user (role EMPLOYEE or ADMIN) who attends the appointment
-- [v14 cabinas] id_booth: cabina fisica donde se realiza la cita (NULLABLE).
--                          Si la cita no usa cabina (negocio sin cabinas o
--                          servicio que no la requiere) queda NULL.
-- ------------------------------------------------------------
-- [v16 membership] id_employee -> id_membership. La cita la atiende una
-- membership concreta (un usuario en su rol dentro de este negocio), no
-- la identidad global. Asi un mismo usuario que trabaje en dos negocios
-- jamas mezclara las citas de ambos.
CREATE TABLE appointments (
                              id_appointment BIGINT   AUTO_INCREMENT PRIMARY KEY,
                              id_business    BIGINT   NOT NULL,
                              id_client      BIGINT   NOT NULL,
                              id_membership  BIGINT   NOT NULL,                 -- [v16 membership]
                              id_booth       BIGINT   NULL,                     -- [v14 cabinas]
                              id_status      BIGINT   NOT NULL,

    -- NUEVO CAMPO: Control de pagos para los filtros del calendario
                              is_paid        BOOLEAN  NOT NULL DEFAULT FALSE,

                              start_datetime DATETIME NOT NULL,
                              end_datetime   DATETIME NOT NULL,
                              notes          TEXT,
                              created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT fk_appointment_business
                                  FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                              CONSTRAINT fk_appointment_client
                                  FOREIGN KEY (id_client) REFERENCES clients(id_client),
                              CONSTRAINT fk_appointment_membership              -- [v16 membership]
                                  FOREIGN KEY (id_membership) REFERENCES memberships(id_membership),
                              CONSTRAINT fk_appointment_booth                   -- [v14 cabinas]
                                  FOREIGN KEY (id_booth) REFERENCES booths(id_booth),
                              CONSTRAINT fk_appointment_status
                                  FOREIGN KEY (id_status) REFERENCES appointment_statuses(id_status),
                              CONSTRAINT chk_appointment_times
                                  CHECK (start_datetime < end_datetime)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 11. APPOINTMENT SERVICES (services booked in each appointment)
-- applied_price and applied_tax_percentage are frozen at booking time
-- so future changes to service.price or tax.percentage don't affect history
-- ------------------------------------------------------------
CREATE TABLE appointment_services (
                                      id_appointment_service BIGINT        AUTO_INCREMENT PRIMARY KEY,
                                      id_appointment         BIGINT        NOT NULL,
                                      id_service             BIGINT        NOT NULL,
                                      applied_price          DECIMAL(10,2) NOT NULL,
                                      applied_tax_percentage DECIMAL(5,2)  NOT NULL,
                                      CONSTRAINT fk_appsvc_appointment
                                          FOREIGN KEY (id_appointment) REFERENCES appointments(id_appointment)
                                              ON DELETE CASCADE,
                                      CONSTRAINT fk_appsvc_service
                                          FOREIGN KEY (id_service) REFERENCES services(id_service),
                                      CONSTRAINT chk_appsvc_applied_price
                                          CHECK (applied_price >= 0),
                                      CONSTRAINT chk_appsvc_applied_tax
                                          CHECK (applied_tax_percentage >= 0 AND applied_tax_percentage <= 100)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 12. BUSINESS HOURS (Global operating hours for the business)
-- ------------------------------------------------------------
CREATE TABLE business_hours (
                                id_business_hour BIGINT AUTO_INCREMENT PRIMARY KEY,
                                id_business      BIGINT  NOT NULL,
                                day_of_week      INT     NOT NULL, -- 1=Lunes, 2=Martes, ..., 7=Domingo
                                start_time       TIME    NULL,     -- Puede ser nulo si el local está cerrado
                                end_time         TIME    NULL,     -- Puede ser nulo si el local está cerrado
                                is_closed        BOOLEAN NOT NULL DEFAULT FALSE,

                                CONSTRAINT fk_business_hours_business
                                    FOREIGN KEY (id_business) REFERENCES businesses(id_business)
                                        ON DELETE CASCADE,

                                CONSTRAINT chk_bh_day_of_week
                                    CHECK (day_of_week BETWEEN 1 AND 7),

                                CONSTRAINT chk_bh_times_logic
                                    -- Si está cerrado, las horas no importan. Si está abierto, debe haber horas válidas.
                                    CHECK (
                                        is_closed = TRUE
                                            OR (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time)
                                        )
) ENGINE=InnoDB;


-- ------------------------------------------------------------
-- 13. EMPLOYEE ABSENCES (Bloqueos puntuales o vacaciones)
-- Sobrescribe la disponibilidad de employee_schedules
-- [v16 membership] id_employee -> id_membership. Misma logica que con
-- employee_schedules: la ausencia es de "usuario en este negocio".
-- ------------------------------------------------------------
CREATE TABLE employee_absences (
                                   id_absence     BIGINT       AUTO_INCREMENT PRIMARY KEY,
                                   id_membership  BIGINT       NOT NULL,
                                   start_datetime DATETIME     NOT NULL,
                                   end_datetime   DATETIME     NOT NULL,
                                   reason         VARCHAR(255) NULL, -- Ej: "Cita médica", "Vacaciones"
                                   created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT fk_absence_membership
                                       FOREIGN KEY (id_membership) REFERENCES memberships(id_membership)
                                           ON DELETE CASCADE,

                                   CONSTRAINT chk_absence_times
                                       CHECK (start_datetime < end_datetime)
) ENGINE=InnoDB;


-- ------------------------------------------------------------
-- 14. SCHEDULE BLOCKS [v15 bloqueos] (bloqueos de agenda por dia completo)
-- Tres tipos segun FKs:
--   - Global (festivo del negocio):   id_employee=NULL, id_booth=NULL.
--   - Por empleado (vacaciones):      id_employee=X,    id_booth=NULL.
--   - Por cabina (mantenimiento):     id_employee=NULL, id_booth=Y.
-- Convive con employee_absences: ese cubre rangos por horas (parciales);
-- schedule_blocks cubre dias completos.
-- ------------------------------------------------------------
-- [v16 membership] id_employee -> id_membership. Asi "vacaciones de Ana"
-- en la peluqueria A no bloquean la agenda de Ana en su otro negocio.
CREATE TABLE schedule_blocks (
                                 id_block       BIGINT       AUTO_INCREMENT PRIMARY KEY,
                                 id_business    BIGINT       NOT NULL,
                                 id_membership  BIGINT       NULL,
                                 id_booth       BIGINT       NULL,
                                 start_date     DATE         NOT NULL,
                                 end_date       DATE         NOT NULL,
                                 reason         VARCHAR(255) NULL,
                                 created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT fk_block_business
                                     FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                                 CONSTRAINT fk_block_membership
                                     FOREIGN KEY (id_membership) REFERENCES memberships(id_membership),
                                 CONSTRAINT fk_block_booth
                                     FOREIGN KEY (id_booth) REFERENCES booths(id_booth),
                                 CONSTRAINT chk_block_dates
                                     CHECK (start_date <= end_date)
) ENGINE=InnoDB;


-- ------------------------------------------------------------
-- 15. PASSWORD RESETS [v17 reset]
-- Tokens efimeros (1 hora) para que un usuario que olvido su password
-- pueda fijar uno nuevo sin login previo.
--
-- Diseno:
--   - id_user: el password es de la identidad GLOBAL (users), no de
--     una membership. Un reset cambia la password en todos los
--     negocios donde la persona es miembro.
--   - token_hash: guardamos SHA-256(rawToken) en lugar del token plano.
--     Si robasen la BD no podrian usar los tokens. UNIQUE para impedir
--     colisiones.
--   - expires_at: el caller pone now()+1h.
--   - used_at: NULL hasta consumirse. Un token solo se usa una vez
--     (anti-replay).
--   - ON DELETE CASCADE: si se borra el usuario, sus resets se van.
-- ------------------------------------------------------------
CREATE TABLE password_resets (
                                 id_reset    BIGINT       AUTO_INCREMENT PRIMARY KEY,
                                 id_user     BIGINT       NOT NULL,
                                 token_hash  VARCHAR(255) NOT NULL UNIQUE,
                                 expires_at  DATETIME     NOT NULL,
                                 used_at     DATETIME     NULL,
                                 created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT fk_reset_user
                                     FOREIGN KEY (id_user) REFERENCES users(id_user)
                                         ON DELETE CASCADE
) ENGINE=InnoDB;


-- ------------------------------------------------------------
-- SEED: negocio demo + admin para login + segundo negocio con un
-- cliente para que los tests de cross-tenant de la coleccion Postman
-- tengan referentes reales (admin de "demo" no debe poder acceder a
-- ningun recurso bajo /businesses/2/...).
-- Credenciales del demo:
--   email:        admin@optima.com   (UNIQUE global desde v16)
--   password:     12345678
-- (hash BCrypt cost 10 generado offline; Spring acepta $2b$ y $2a$
-- indistintamente en BCryptPasswordEncoder.matches)
--
-- [v16 membership] Cada user del seed genera UNA membership con su
-- (id_business, id_role) anteriores. Por la secuencia INSERT, los
-- AUTO_INCREMENT de users y memberships coinciden 1:1 (u1<->m1, u2<->m2,
-- u3<->m3, u4<->m4). Eso permite que los INSERTs de employee_schedules,
-- employee_absences y appointments mantengan los mismos numeros despues
-- del rename id_user -> id_membership.
-- ------------------------------------------------------------
INSERT INTO businesses (name, slug, email, appointment_interval)
VALUES ('Demo', 'demo', 'demo@optima.com', 30);

INSERT INTO businesses (name, slug, email, appointment_interval)
VALUES ('Otro', 'otro', 'otro@optima.com', 30);

-- u1: Admin Demo (identidad)
INSERT INTO users (full_name, email, password_hash)
VALUES ('Admin Demo', 'admin@optima.com',
        '$2a$10$XLihaXA2hZhv9fmi1KkYHewNFyhdnAexajF2fDQS9TZSa3a1gke4q');
-- m1: Admin Demo en business 1 con rol ADMIN
INSERT INTO memberships (id_user, id_business, id_role) VALUES (1, 1, 1);

-- u2: Empleado Demo (mismo password "12345678", mismo hash BCrypt;
-- el salt va embebido en el hash). Sirve para probar el rol EMPLOYEE
-- en la coleccion Postman.
INSERT INTO users (full_name, email, password_hash)
VALUES ('Empleado Demo', 'empleado@optima.com',
        '$2a$10$XLihaXA2hZhv9fmi1KkYHewNFyhdnAexajF2fDQS9TZSa3a1gke4q');
-- m2: Empleado Demo en business 1 con rol EMPLOYEE
INSERT INTO memberships (id_user, id_business, id_role) VALUES (2, 1, 2);

INSERT INTO clients (id_business, full_name, is_active)
VALUES (2, 'Cliente Ajeno Seed', TRUE);


-- ============================================================
-- SEED ENRIQUECIDO PARA DEMO: el negocio 1 (slug=demo) queda con
-- catalogo completo, agenda multi-empleado y citas en distintos
-- estados. Asi la defensa en vivo arranca con datos realistas.
-- ============================================================

-- ------------------------------------------------------------
-- 2 empleados mas (mismo password "12345678", mismo hash BCrypt).
-- [v16 membership] Cada user va seguido de su membership con (b1, role
-- EMPLOYEE) para mantener la coincidencia 1:1 user_id <-> membership_id.
-- ------------------------------------------------------------
-- u3: Maria
INSERT INTO users (full_name, email, password_hash, phone)
VALUES ('Maria Garcia', 'maria@optima.com',
        '$2a$10$XLihaXA2hZhv9fmi1KkYHewNFyhdnAexajF2fDQS9TZSa3a1gke4q',
        '600111001');
-- m3: Maria en business 1 con rol EMPLOYEE
INSERT INTO memberships (id_user, id_business, id_role) VALUES (3, 1, 2);

-- u4: Carlos
INSERT INTO users (full_name, email, password_hash, phone)
VALUES ('Carlos Lopez', 'carlos@optima.com',
        '$2a$10$XLihaXA2hZhv9fmi1KkYHewNFyhdnAexajF2fDQS9TZSa3a1gke4q',
        '600111002');
-- m4: Carlos en business 1 con rol EMPLOYEE
INSERT INTO memberships (id_user, id_business, id_role) VALUES (4, 1, 2);

-- ------------------------------------------------------------
-- Horarios del negocio: Lun-Vie 09-18, Sabado 10-14, Domingo cerrado
-- ------------------------------------------------------------
INSERT INTO business_hours (id_business, day_of_week, start_time, end_time, is_closed) VALUES
    (1, 1, '09:00:00', '18:00:00', FALSE),
    (1, 2, '09:00:00', '18:00:00', FALSE),
    (1, 3, '09:00:00', '18:00:00', FALSE),
    (1, 4, '09:00:00', '18:00:00', FALSE),
    (1, 5, '09:00:00', '18:00:00', FALSE),
    (1, 6, '10:00:00', '14:00:00', FALSE),
    (1, 7, NULL, NULL, TRUE);

-- ------------------------------------------------------------
-- Impuestos (IVA general 21% e IVA reducido 10%)
-- ------------------------------------------------------------
INSERT INTO taxes (id_business, name, percentage, is_active) VALUES
    (1, 'IVA21', 21.00, TRUE),
    (1, 'IVA10', 10.00, TRUE);

-- ------------------------------------------------------------
-- Categorias de servicios
-- ------------------------------------------------------------
INSERT INTO service_categories (id_business, name, is_active) VALUES
    (1, 'Cortes', TRUE),
    (1, 'Tintes', TRUE),
    (1, 'Peinados', TRUE);

-- ------------------------------------------------------------
-- Servicios (todos con IVA21 y categorias correspondientes)
-- ------------------------------------------------------------
INSERT INTO services (id_business, id_category, id_tax, name, description, price, duration_minutes, is_active) VALUES
    (1, 1, 1, 'Corte caballero',  'Corte clasico de pelo para hombre',           15.00, 30, TRUE),
    (1, 1, 1, 'Corte senora',     'Corte y secado para mujer',                   25.00, 45, TRUE),
    (1, 2, 1, 'Tinte completo',   'Aplicacion de tinte en todo el cabello',      50.00, 90, TRUE),
    (1, 2, 1, 'Mechas',           'Mechas iluminadoras con papel de aluminio',   60.00,120, TRUE),
    (1, 3, 1, 'Peinado evento',   'Peinado para boda o evento especial',         35.00, 60, TRUE);

-- ------------------------------------------------------------
-- [v14 cabinas] Cabinas del negocio demo
-- ------------------------------------------------------------
INSERT INTO booths (id_business, name, is_active) VALUES
    (1, 'Sala 1', TRUE),
    (1, 'Sala 2', TRUE);

-- ------------------------------------------------------------
-- Horarios laborales de los 3 empleados:
-- empleado@optima.com (id=2): Lun-Vie 09-13 + 15-18
-- maria@optima.com (id=3): Lun-Vie 09-13 + 15-18, Sabado 10-14
-- carlos@optima.com (id=4): Mar-Sab 10-14 + 16-19
-- ------------------------------------------------------------
-- [v16 membership] id_user -> id_membership. Los valores siguen iguales
-- porque membership_id coincide con user_id por orden de INSERT.
INSERT INTO employee_schedules (id_membership, day_of_week, start_time, end_time) VALUES
    -- Empleado Demo (membership=2)
    (2, 1, '09:00:00', '13:00:00'), (2, 1, '15:00:00', '18:00:00'),
    (2, 2, '09:00:00', '13:00:00'), (2, 2, '15:00:00', '18:00:00'),
    (2, 3, '09:00:00', '13:00:00'), (2, 3, '15:00:00', '18:00:00'),
    (2, 4, '09:00:00', '13:00:00'), (2, 4, '15:00:00', '18:00:00'),
    (2, 5, '09:00:00', '13:00:00'), (2, 5, '15:00:00', '18:00:00'),
    -- Maria Garcia (membership=3)
    (3, 1, '09:00:00', '13:00:00'), (3, 1, '15:00:00', '18:00:00'),
    (3, 2, '09:00:00', '13:00:00'), (3, 2, '15:00:00', '18:00:00'),
    (3, 3, '09:00:00', '13:00:00'), (3, 3, '15:00:00', '18:00:00'),
    (3, 4, '09:00:00', '13:00:00'), (3, 4, '15:00:00', '18:00:00'),
    (3, 5, '09:00:00', '13:00:00'), (3, 5, '15:00:00', '18:00:00'),
    (3, 6, '10:00:00', '14:00:00'),
    -- Carlos Lopez (membership=4)
    (4, 2, '10:00:00', '14:00:00'), (4, 2, '16:00:00', '19:00:00'),
    (4, 3, '10:00:00', '14:00:00'), (4, 3, '16:00:00', '19:00:00'),
    (4, 4, '10:00:00', '14:00:00'), (4, 4, '16:00:00', '19:00:00'),
    (4, 5, '10:00:00', '14:00:00'), (4, 5, '16:00:00', '19:00:00'),
    (4, 6, '10:00:00', '14:00:00');

-- ------------------------------------------------------------
-- Clientes del negocio 1 (5 clientes con datos realistas)
-- ------------------------------------------------------------
INSERT INTO clients (id_business, full_name, email, phone, notes, is_active) VALUES
    (1, 'Ana Garcia',     'ana.garcia@email.com',     '600222001', 'Prefiere citas por la manana',          TRUE),
    (1, 'Pedro Martinez', 'pedro.martinez@email.com', '600222002', 'Alergico al amoniaco',                  TRUE),
    (1, 'Laura Sanchez',  'laura.sanchez@email.com',  '600222003', NULL,                                    TRUE),
    (1, 'Miguel Lopez',   'miguel.lopez@email.com',   '600222004', 'Cliente fiel desde 2024',               TRUE),
    (1, 'Carmen Ruiz',    'carmen.ruiz@email.com',    '600222005', NULL,                                    TRUE);

-- ------------------------------------------------------------
-- Citas en distintos estados (id_status: 1=PENDING, 2=CONFIRMED,
-- 3=IN_PROGRESS, 4=COMPLETED, 5=CANCELLED, 6=NO_SHOW).
-- Clientes (business 1): id=2 Ana, id=3 Pedro, id=4 Laura, id=5 Miguel, id=6 Carmen
-- (recuerda: id=1 es 'Cliente Ajeno Seed' del business 2).
-- [v14 cabinas] Algunas citas se asignan a Sala 1 (id=1) o Sala 2 (id=2)
-- para tener datos realistas; las del pasado se dejan sin cabina para
-- demostrar que el campo es nullable.
-- ------------------------------------------------------------

-- Cita PENDING: Ana con empleado el lunes 2027-03-15 a las 10:00 en Sala 1
-- Servicio: Corte senora (id=2) -> 45 min, end 10:45
INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid,
                          start_datetime, end_datetime, notes)
VALUES (1, 2, 2, 1, 1, FALSE,
        '2027-03-15 10:00:00', '2027-03-15 10:45:00',
        'Cita confirmada por telefono');
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage)
VALUES (1, 2, 25.00, 21.00);

-- Cita CONFIRMED: Pedro con Maria el lunes 2027-03-15 a las 11:00 en Sala 2
-- Servicio: Corte caballero (id=1) -> 30 min, end 11:30
INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid,
                          start_datetime, end_datetime, notes)
VALUES (1, 3, 3, 2, 2, FALSE,
        '2027-03-15 11:00:00', '2027-03-15 11:30:00',
        'Cliente habitual');
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage)
VALUES (2, 1, 15.00, 21.00);

-- Cita IN_PROGRESS: Laura con Carlos el martes 2027-03-16 a las 16:00 en Sala 1
-- Servicio: Tinte completo (id=3) -> 90 min, end 17:30
INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid,
                          start_datetime, end_datetime, notes)
VALUES (1, 4, 4, 1, 3, FALSE,
        '2027-03-16 16:00:00', '2027-03-16 17:30:00',
        'Tinte de mantenimiento');
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage)
VALUES (3, 3, 50.00, 21.00);

-- Cita COMPLETED: Miguel con empleado el 2024-06-15 (en el pasado), SIN cabina
-- [v14 cabinas] La cita historica se queda sin cabina para demostrar nullable.
-- Servicios: Corte caballero (30min) + Peinado evento (60min) = 90min, end 11:30
INSERT INTO appointments (id_business, id_client, id_membership, id_status, is_paid,
                          start_datetime, end_datetime, notes)
VALUES (1, 5, 2, 4, TRUE,
        '2024-06-15 10:00:00', '2024-06-15 11:30:00',
        'Pagado en efectivo');
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES
    (4, 1, 15.00, 21.00),
    (4, 5, 35.00, 21.00);

-- ------------------------------------------------------------
-- [v15 bloqueos] Bloqueo global de prueba en business 1:
-- "San Isidro" el 2027-05-15. Cualquier intento de crear una cita ese
-- dia en el negocio 1 debe rechazarse con 409.
-- ------------------------------------------------------------
INSERT INTO schedule_blocks (id_business, id_membership, id_booth, start_date, end_date, reason)
VALUES (1, NULL, NULL, '2027-05-15', '2027-05-15', 'San Isidro');
