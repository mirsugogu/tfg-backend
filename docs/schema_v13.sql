-- ============================================================
-- Optima SaaS - Schema v13
-- Multi-tenant (Shared DB, Shared Schema)
-- No superadmins | Taxes per business | English naming
-- ============================================================

DROP DATABASE IF EXISTS optima_db;
CREATE DATABASE optima_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE optima_db;

-- 1. BUSINESSES
CREATE TABLE businesses (
                            id_business          BIGINT AUTO_INCREMENT PRIMARY KEY,
                            name                 VARCHAR(150) NOT NULL,
                            slug                 VARCHAR(150) NOT NULL UNIQUE,
                            email                VARCHAR(150) NOT NULL,
                            phone                VARCHAR(20),
                            address              VARCHAR(255),
                            appointment_interval INT NOT NULL DEFAULT 30,
                            is_active            BOOLEAN NOT NULL DEFAULT TRUE,
                            created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            deactivated_at       DATETIME NULL,
                            CONSTRAINT chk_appointment_interval CHECK (appointment_interval IN (15, 30, 45, 60))
) ENGINE=InnoDB;

-- 2. ROLES
CREATE TABLE roles (
                       id_role BIGINT AUTO_INCREMENT PRIMARY KEY,
                       name    VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT INTO roles (name) VALUES ('ADMIN'), ('EMPLOYEE');

-- 3. USERS
CREATE TABLE users (
                       id_user        BIGINT AUTO_INCREMENT PRIMARY KEY,
                       id_business    BIGINT NOT NULL,
                       id_role        BIGINT NOT NULL,
                       full_name      VARCHAR(150) NOT NULL,
                       email          VARCHAR(150) NOT NULL,
                       password_hash  VARCHAR(255) NOT NULL,
                       phone          VARCHAR(20),
                       is_active      BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       deactivated_at DATETIME NULL,
                       CONSTRAINT fk_user_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                       CONSTRAINT fk_user_role     FOREIGN KEY (id_role)     REFERENCES roles(id_role),
                       CONSTRAINT uq_user_email_business UNIQUE (id_business, email)
) ENGINE=InnoDB;

-- 4. EMPLOYEE SCHEDULES
CREATE TABLE employee_schedules (
                                    id_schedule BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    id_user     BIGINT NOT NULL,
                                    day_of_week TINYINT NOT NULL,
                                    start_time  TIME NOT NULL,
                                    end_time    TIME NOT NULL,
                                    CONSTRAINT fk_schedule_user FOREIGN KEY (id_user) REFERENCES users(id_user),
                                    CONSTRAINT chk_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
                                    CONSTRAINT chk_schedule_times CHECK (start_time < end_time)
) ENGINE=InnoDB;

-- 5. TAXES (per business)
CREATE TABLE taxes (
                       id_tax      BIGINT AUTO_INCREMENT PRIMARY KEY,
                       id_business BIGINT NOT NULL,
                       name        VARCHAR(50) NOT NULL,
                       percentage  DECIMAL(5,2) NOT NULL,
                       is_active   BOOLEAN NOT NULL DEFAULT TRUE,
                       CONSTRAINT fk_tax_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                       CONSTRAINT chk_tax_percentage CHECK (percentage >= 0 AND percentage <= 100)
) ENGINE=InnoDB;

-- 6. CLIENTS
CREATE TABLE clients (
                         id_client      BIGINT AUTO_INCREMENT PRIMARY KEY,
                         id_business    BIGINT NOT NULL,
                         full_name      VARCHAR(150) NOT NULL,
                         email          VARCHAR(150),
                         phone          VARCHAR(20),
                         notes          TEXT,
                         is_active      BOOLEAN NOT NULL DEFAULT TRUE,
                         created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         deactivated_at DATETIME NULL,
                         CONSTRAINT fk_client_business FOREIGN KEY (id_business) REFERENCES businesses(id_business)
) ENGINE=InnoDB;

-- 7. SERVICE CATEGORIES
CREATE TABLE service_categories (
                                    id_category    BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    id_business    BIGINT NOT NULL,
                                    name           VARCHAR(100) NOT NULL,
                                    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
                                    deactivated_at DATETIME NULL,
                                    CONSTRAINT fk_category_business FOREIGN KEY (id_business) REFERENCES businesses(id_business)
) ENGINE=InnoDB;

-- 8. SERVICES
CREATE TABLE services (
                          id_service       BIGINT AUTO_INCREMENT PRIMARY KEY,
                          id_business      BIGINT NOT NULL,
                          id_category      BIGINT NOT NULL,
                          id_tax           BIGINT NOT NULL,
                          name             VARCHAR(150) NOT NULL,
                          description      TEXT,
                          price            DECIMAL(10,2) NOT NULL,
                          duration_minutes INT NOT NULL,
                          is_active        BOOLEAN NOT NULL DEFAULT TRUE,
                          deactivated_at   DATETIME NULL,
                          CONSTRAINT fk_service_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                          CONSTRAINT fk_service_category FOREIGN KEY (id_category) REFERENCES service_categories(id_category),
                          CONSTRAINT fk_service_tax      FOREIGN KEY (id_tax)      REFERENCES taxes(id_tax),
                          CONSTRAINT chk_service_price CHECK (price >= 0),
                          CONSTRAINT chk_service_duration CHECK (duration_minutes > 0)
) ENGINE=InnoDB;

-- 9. APPOINTMENT STATUSES
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

-- 10. APPOINTMENTS
CREATE TABLE appointments (
                              id_appointment BIGINT AUTO_INCREMENT PRIMARY KEY,
                              id_business    BIGINT NOT NULL,
                              id_client      BIGINT NOT NULL,
                              id_user        BIGINT NOT NULL,
                              id_status      BIGINT NOT NULL,
                              start_datetime DATETIME NOT NULL,
                              end_datetime   DATETIME NOT NULL,
                              notes          TEXT,
                              created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT fk_appointment_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
                              CONSTRAINT fk_appointment_client   FOREIGN KEY (id_client)   REFERENCES clients(id_client),
                              CONSTRAINT fk_appointment_user     FOREIGN KEY (id_user)     REFERENCES users(id_user),
                              CONSTRAINT fk_appointment_status   FOREIGN KEY (id_status)   REFERENCES appointment_statuses(id_status),
                              CONSTRAINT chk_appointment_times CHECK (start_datetime < end_datetime)
) ENGINE=InnoDB;

-- 11. APPOINTMENT SERVICES (many-to-many)
CREATE TABLE appointment_services (
                                      id_appointment_service BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      id_appointment         BIGINT NOT NULL,
                                      id_service             BIGINT NOT NULL,
                                      applied_price          DECIMAL(10,2) NOT NULL,
                                      applied_tax_percentage DECIMAL(5,2) NOT NULL,
                                      CONSTRAINT fk_appsvc_appointment FOREIGN KEY (id_appointment) REFERENCES appointments(id_appointment) ON DELETE CASCADE,
                                      CONSTRAINT fk_appsvc_service     FOREIGN KEY (id_service)     REFERENCES services(id_service)
) ENGINE=InnoDB;