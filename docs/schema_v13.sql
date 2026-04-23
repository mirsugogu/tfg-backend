-- ============================================================
-- Optima SaaS - Database Schema
-- Multi-tenant (Shared DB, Shared Schema)
-- No superadmins | Taxes per business | English naming
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
-- 3. USERS (admins and employees of each business)
-- ------------------------------------------------------------
CREATE TABLE users (
                       id_user        BIGINT       AUTO_INCREMENT PRIMARY KEY,
                       id_business    BIGINT       NOT NULL,
                       id_role        BIGINT       NOT NULL,
                       full_name      VARCHAR(150) NOT NULL,
                       email          VARCHAR(150) NOT NULL,
                       password_hash  VARCHAR(255) NOT NULL,
                       phone          VARCHAR(20),
                       is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       deactivated_at DATETIME     NULL,
                       CONSTRAINT fk_user_business
                           FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                       CONSTRAINT fk_user_role
                           FOREIGN KEY (id_role) REFERENCES roles(id_role),
                       CONSTRAINT uq_user_business_email
                           UNIQUE (id_business, email)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 4. EMPLOYEE SCHEDULES (weekly working hours)
-- day_of_week: 1=Monday, 2=Tuesday, ..., 7=Sunday
-- ------------------------------------------------------------
CREATE TABLE employee_schedules (
                                    id_schedule BIGINT  AUTO_INCREMENT PRIMARY KEY,
                                    id_user     BIGINT  NOT NULL,
                                    day_of_week TINYINT NOT NULL,
                                    start_time  TIME    NOT NULL,
                                    end_time    TIME    NOT NULL,
                                    CONSTRAINT fk_schedule_user
                                        FOREIGN KEY (id_user) REFERENCES users(id_user),
                                    CONSTRAINT chk_day_of_week
                                        CHECK (day_of_week BETWEEN 1 AND 7),
                                    CONSTRAINT chk_schedule_times
                                        CHECK (start_time < end_time)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 5. TAXES (each business defines its own taxes)
-- ------------------------------------------------------------
CREATE TABLE taxes (
                       id_tax      BIGINT        AUTO_INCREMENT PRIMARY KEY,
                       id_business BIGINT        NOT NULL,
                       name        VARCHAR(50)   NOT NULL,
                       percentage  DECIMAL(5,2)  NOT NULL,
                       is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
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
-- ------------------------------------------------------------
CREATE TABLE appointments (
                              id_appointment BIGINT   AUTO_INCREMENT PRIMARY KEY,
                              id_business    BIGINT   NOT NULL,
                              id_client      BIGINT   NOT NULL,
                              id_employee    BIGINT   NOT NULL,
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
                              CONSTRAINT fk_appointment_employee
                                  FOREIGN KEY (id_employee) REFERENCES users(id_user),
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
-- 1.1. BUSINESS HOURS (Global operating hours for the business)
-- ------------------------------------------------------------
CREATE TABLE business_hours (
                                id_business_hour BIGINT AUTO_INCREMENT PRIMARY KEY,
                                id_business      BIGINT  NOT NULL,
                                day_of_week      TINYINT NOT NULL, -- 1=Lunes, 2=Martes, ..., 7=Domingo
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
-- 12. EMPLOYEE ABSENCES (Bloqueos puntuales o vacaciones)
-- Sobrescribe la disponibilidad de employee_schedules
-- ------------------------------------------------------------
CREATE TABLE employee_absences (
                                   id_absence     BIGINT       AUTO_INCREMENT PRIMARY KEY,
                                   id_employee    BIGINT       NOT NULL,
                                   start_datetime DATETIME     NOT NULL,
                                   end_datetime   DATETIME     NOT NULL,
                                   reason         VARCHAR(255) NULL, -- Ej: "Cita médica", "Vacaciones"
                                   created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT fk_absence_employee
                                       FOREIGN KEY (id_employee) REFERENCES users(id_user)
                                           ON DELETE CASCADE,

                                   CONSTRAINT chk_absence_times
                                       CHECK (start_datetime < end_datetime)
) ENGINE=InnoDB;