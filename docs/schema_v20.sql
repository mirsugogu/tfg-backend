-- Esquema relacional de Optima (v20)
-- Base de datos multi-tenant para gestion de citas

DROP DATABASE IF EXISTS optima_db;
CREATE DATABASE optima_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE optima_db;


-- Negocio del sistema, unidad de separacion de datos (tenant)
CREATE TABLE businesses (
id_business BIGINT AUTO_INCREMENT PRIMARY KEY,
	name VARCHAR(150) NOT NULL,
	slug VARCHAR(150) NOT NULL UNIQUE,
	email VARCHAR(150)  NOT NULL UNIQUE,
	phone VARCHAR(20),
	address VARCHAR(255),
	city VARCHAR(100),
	state VARCHAR(100),
	country VARCHAR(100),
	postal_code VARCHAR(20),
	latitude DECIMAL(10, 8),
	longitude DECIMAL(11, 8),
	appointment_interval INT NOT NULL DEFAULT 30,
	is_active BOOLEAN NOT NULL DEFAULT TRUE,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	deactivated_at DATETIME NULL,
	CONSTRAINT chk_appointment_interval CHECK (appointment_interval IN (15, 30, 45, 60))
) ENGINE=InnoDB;

-- Catalogo fijo de roles del sistema
CREATE TABLE roles (
	id_role BIGINT AUTO_INCREMENT PRIMARY KEY,
	name VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT INTO roles (name) VALUES ('ADMIN'), ('EMPLOYEE');

-- Usuarios del sistema con credenciales de acceso
CREATE TABLE users (
	id_user BIGINT AUTO_INCREMENT PRIMARY KEY,
	full_name VARCHAR(150) NOT NULL,
	email VARCHAR(150) NOT NULL UNIQUE,
	password_hash VARCHAR(255) NOT NULL,
	phone VARCHAR(20),
	is_active BOOLEAN NOT NULL DEFAULT TRUE,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	deactivated_at DATETIME NULL
) ENGINE=InnoDB;



-- Relacion entre usuario, negocio y rol (una persona puede estar en varios negocios)
CREATE TABLE memberships (
	id_membership BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_user BIGINT NOT NULL,
	id_business BIGINT NOT NULL,
	id_role BIGINT NOT NULL,
	is_active BOOLEAN  NOT NULL DEFAULT TRUE,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	color VARCHAR(20) NULL,
	CONSTRAINT fk_membership_user FOREIGN KEY (id_user) REFERENCES users(id_user),
	CONSTRAINT fk_membership_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
	CONSTRAINT fk_membership_role FOREIGN KEY (id_role) REFERENCES roles(id_role),
	CONSTRAINT uq_membership_user_business UNIQUE (id_user, id_business)
) ENGINE=InnoDB;


-- Tramos del horario semanal de cada empleado
CREATE TABLE employee_schedules (
	id_schedule BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_membership BIGINT NOT NULL,
	day_of_week INT NOT NULL,
	start_time TIME NOT NULL,
	end_time TIME NOT NULL,
	CONSTRAINT fk_schedule_membership FOREIGN KEY (id_membership) REFERENCES memberships(id_membership),
	CONSTRAINT chk_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
	CONSTRAINT chk_schedule_times CHECK (start_time < end_time)
) ENGINE=InnoDB;


-- Impuestos configurados por cada negocio
CREATE TABLE taxes (
	id_tax BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_business BIGINT NOT NULL,
	name VARCHAR(50) NOT NULL,
	percentage DECIMAL(5,2) NOT NULL,
	is_active BOOLEAN NOT NULL DEFAULT TRUE,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	deactivated_at DATETIME NULL,
	CONSTRAINT fk_tax_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
	CONSTRAINT uq_tax_business_name UNIQUE (id_business, name),
	CONSTRAINT chk_tax_percentage CHECK (percentage >= 0 AND percentage <= 100)
) ENGINE=InnoDB;


-- Clientes registrados dentro de un negocio
CREATE TABLE clients (
	id_client BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_business BIGINT NOT NULL,
	full_name VARCHAR(150) NOT NULL,
	email VARCHAR(150),
	phone VARCHAR(20),
	notes TEXT,
	is_active BOOLEAN NOT NULL DEFAULT TRUE,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	deactivated_at DATETIME NULL,
	CONSTRAINT fk_client_business FOREIGN KEY (id_business) REFERENCES businesses(id_business)
) ENGINE=InnoDB;


-- Categorias para agrupar los servicios del catalogo
CREATE TABLE service_categories (
	id_category BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_business BIGINT NOT NULL,
	name VARCHAR(100) NOT NULL,
	is_active BOOLEAN NOT NULL DEFAULT TRUE,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	deactivated_at DATETIME NULL,
	CONSTRAINT fk_category_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
	CONSTRAINT uq_category_business_name UNIQUE (id_business, name)
) ENGINE=InnoDB;


-- Servicios ofrecidos por el negocio con precio y duracion
CREATE TABLE services (
	id_service BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_business BIGINT NOT NULL,
	id_category BIGINT NOT NULL,
	id_tax BIGINT NOT NULL,
	name VARCHAR(150) NOT NULL,
	description TEXT,
	price DECIMAL(10,2) NOT NULL,
	duration_minutes INT NOT NULL,
	is_active BOOLEAN NOT NULL DEFAULT TRUE,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	deactivated_at DATETIME NULL,
	CONSTRAINT fk_service_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
	CONSTRAINT fk_service_category FOREIGN KEY (id_category) REFERENCES service_categories(id_category),
	CONSTRAINT fk_service_tax FOREIGN KEY (id_tax) REFERENCES taxes(id_tax),
	CONSTRAINT uq_service_business_name UNIQUE (id_business, name),
	CONSTRAINT chk_service_price CHECK (price >= 0),
	CONSTRAINT chk_service_duration CHECK (duration_minutes > 0)
) ENGINE=InnoDB;


-- Cabinas o espacios fisicos donde se realizan los servicios
CREATE TABLE booths (
	id_booth BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_business BIGINT NOT NULL,
	name VARCHAR(80) NOT NULL,
	color VARCHAR(20) NULL,
	is_active BOOLEAN NOT NULL DEFAULT TRUE,
	deactivated_at DATETIME NULL,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT fk_booth_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
	CONSTRAINT uq_booth_business_name UNIQUE (id_business, name)
) ENGINE=InnoDB;


-- Catalogo fijo de estados de una cita
CREATE TABLE appointment_statuses (
	id_status BIGINT AUTO_INCREMENT PRIMARY KEY,
	name VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT INTO appointment_statuses (name) VALUES ('PENDING'), ('CONFIRMED'), ('IN_PROGRESS'), ('COMPLETED'), ('CANCELLED'), ('NO_SHOW');


-- Citas del negocio con columnas virtuales para evitar doble reserva
CREATE TABLE appointments (
	id_appointment BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_business BIGINT NOT NULL,
	id_client BIGINT NOT NULL,
	id_membership BIGINT NOT NULL,
	id_booth BIGINT NULL,
	id_status BIGINT NOT NULL,
	is_paid BOOLEAN NOT NULL DEFAULT FALSE,
	start_datetime DATETIME NOT NULL,
	end_datetime DATETIME NOT NULL,
	notes TEXT,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at DATETIME NULL,

	active_slot_key VARCHAR(50) GENERATED ALWAYS AS (
		CASE WHEN id_status IN (1, 2, 3)
			THEN CONCAT(id_membership, '_', start_datetime)
			ELSE NULL END
		) VIRTUAL,
	active_booth_slot_key VARCHAR(50) GENERATED ALWAYS AS (
		CASE WHEN id_booth IS NOT NULL AND id_status IN (1, 2, 3)
			THEN CONCAT(id_booth, '_', start_datetime)
			ELSE NULL END
		) VIRTUAL,
	CONSTRAINT fk_appointment_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
	CONSTRAINT fk_appointment_client FOREIGN KEY (id_client) REFERENCES clients(id_client),
	CONSTRAINT fk_appointment_membership FOREIGN KEY (id_membership) REFERENCES memberships(id_membership),
	CONSTRAINT fk_appointment_booth FOREIGN KEY (id_booth) REFERENCES booths(id_booth),
	CONSTRAINT fk_appointment_status FOREIGN KEY (id_status) REFERENCES appointment_statuses(id_status),
	CONSTRAINT chk_appointment_times CHECK (start_datetime < end_datetime),
	CONSTRAINT uq_appointment_active_slot UNIQUE (active_slot_key),
	CONSTRAINT uq_appointment_active_booth_slot UNIQUE (active_booth_slot_key)
) ENGINE=InnoDB;


-- Servicios contratados en cada cita con precio e impuesto aplicados
CREATE TABLE appointment_services (
	id_appointment_service BIGINT        AUTO_INCREMENT PRIMARY KEY,
	id_appointment         BIGINT        NOT NULL,
	id_service             BIGINT        NOT NULL,
	applied_price          DECIMAL(10,2) NOT NULL,
	applied_tax_percentage DECIMAL(5,2)  NOT NULL,
	CONSTRAINT fk_appsvc_appointment FOREIGN KEY (id_appointment) REFERENCES appointments(id_appointment) ON DELETE CASCADE,
	CONSTRAINT fk_appsvc_service FOREIGN KEY (id_service) REFERENCES services(id_service),
	CONSTRAINT chk_appsvc_applied_price CHECK (applied_price >= 0),
	CONSTRAINT chk_appsvc_applied_tax CHECK (applied_tax_percentage >= 0 AND applied_tax_percentage <= 100)
) ENGINE=InnoDB;


-- Horario de apertura del negocio por dia de la semana
CREATE TABLE business_hours (
	id_business_hour BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_business BIGINT NOT NULL,
	day_of_week INT NOT NULL,
	start_time TIME NULL,
	end_time TIME NULL,
	is_closed BOOLEAN NOT NULL DEFAULT FALSE,
	CONSTRAINT fk_business_hours_business FOREIGN KEY (id_business) REFERENCES businesses(id_business) ON DELETE CASCADE,
	CONSTRAINT chk_bh_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
	CONSTRAINT chk_bh_times_logic CHECK ( is_closed = TRUE OR (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time))
) ENGINE=InnoDB;


-- Ausencias puntuales de empleados con rango de fecha y hora
CREATE TABLE employee_absences (
	id_absence BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_membership BIGINT NOT NULL,
	start_datetime DATETIME NOT NULL,
	end_datetime DATETIME NOT NULL,
	reason VARCHAR(255) NULL,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT fk_absence_membership FOREIGN KEY (id_membership) REFERENCES memberships(id_membership) ON DELETE CASCADE,
	CONSTRAINT chk_absence_times CHECK (start_datetime < end_datetime)
) ENGINE=InnoDB;


-- Bloqueos de agenda por dias completos (global, por empleado o por cabina)
CREATE TABLE schedule_blocks (
	id_block BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_business BIGINT NOT NULL,
	id_membership BIGINT NULL,
	id_booth BIGINT NULL,
	start_date DATE NOT NULL,
	end_date DATE NOT NULL,
	reason VARCHAR(255) NULL,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT fk_block_business FOREIGN KEY (id_business) REFERENCES businesses(id_business),
	CONSTRAINT fk_block_membership
	FOREIGN KEY (id_membership) REFERENCES memberships(id_membership),
	CONSTRAINT fk_block_booth FOREIGN KEY (id_booth) REFERENCES booths(id_booth),
	CONSTRAINT chk_block_dates CHECK (start_date <= end_date),
	CONSTRAINT chk_block_target CHECK (id_membership IS NULL OR id_booth IS NULL)
) ENGINE=InnoDB;


-- Codigos temporales para recuperacion de contrasena
CREATE TABLE password_resets (
	id_reset BIGINT AUTO_INCREMENT PRIMARY KEY,
	id_user BIGINT NOT NULL,
	token_hash VARCHAR(255) NOT NULL UNIQUE,
	expires_at DATETIME NOT NULL,
	used_at DATETIME NULL,
	created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT fk_reset_user FOREIGN KEY (id_user) REFERENCES users(id_user) ON DELETE CASCADE
) ENGINE=InnoDB;



-- Indices para acelerar las consultas mas frecuentes
CREATE INDEX idx_appt_membership_start ON appointments (id_membership, start_datetime);

CREATE INDEX idx_appt_business_start ON appointments (id_business, start_datetime);

CREATE INDEX idx_appt_booth_start ON appointments (id_booth, start_datetime);

CREATE INDEX idx_schedule_membership_day ON employee_schedules (id_membership, day_of_week);









INSERT INTO businesses (name, slug, email, phone,
                        address, city, state, country, postal_code,
                        appointment_interval)
VALUES ('Demo', 'demo', 'demo@optima.com', '911234567',
        'Calle Mayor 10', 'Madrid', 'Madrid', 'Espana', '28013', 30);

INSERT INTO businesses (name, slug, email, phone,
                        address, city, state, country, postal_code,
                        appointment_interval)
VALUES ('Centro de Estetica Aura', 'aura', 'aura@optima.com', '912345678',
        'Calle Gran Via 28', 'Madrid', 'Madrid', 'Espana', '28013', 30);

INSERT INTO users (full_name, email, password_hash)
VALUES ('Admin Demo', 'admin@optima.com',
        '$2a$10$PqBj6CFmvPqwYJetBkVQA.w062mh3mrb2DxF78lIQj2dI/XqjptI.');
INSERT INTO memberships (id_user, id_business, id_role) VALUES (1, 1, 1);

INSERT INTO users (full_name, email, password_hash)
VALUES ('Empleado Demo', 'empleado@optima.com',
        '$2a$10$fCI9ZhcMUj5Z.fmPX2nZ7.SrSn22K42fxU8dvf8GCm8NUDoGud8xq');
INSERT INTO memberships (id_user, id_business, id_role) VALUES (2, 1, 2);

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
VALUES (2, 'Beatriz Navarro', 'beatriz.navarro@email.com', '600333001',
        'Clienta VIP, prefiere cabina tranquila', TRUE);



INSERT INTO users (full_name, email, password_hash, phone)
VALUES ('Maria Garcia', 'maria@optima.com',
        '$2a$10$Gs/mSNCqSc5puTJzCA0NIe23YqUEJtCG/YZ4WVep9L9SZZTb.DSy6',
        '600111001');
INSERT INTO memberships (id_user, id_business, id_role) VALUES (3, 1, 2);

INSERT INTO users (full_name, email, password_hash, phone)
VALUES ('Carlos Lopez', 'carlos@optima.com',
        '$2a$10$zBo7AGlqJF08rLwjLaUXV.D4aAFyFjwhutIlrPes6lVaJmbrvSP1u',
        '600111002');
INSERT INTO memberships (id_user, id_business, id_role) VALUES (4, 1, 2);

INSERT INTO business_hours (id_business, day_of_week, start_time, end_time, is_closed) VALUES
    (1, 1, '09:00:00', '18:00:00', FALSE),
    (1, 2, '09:00:00', '18:00:00', FALSE),
    (1, 3, '09:00:00', '18:00:00', FALSE),
    (1, 4, '09:00:00', '18:00:00', FALSE),
    (1, 5, '09:00:00', '18:00:00', FALSE),
    (1, 6, '10:00:00', '14:00:00', FALSE),
    (1, 7, NULL, NULL, TRUE);

INSERT INTO taxes (id_business, name, percentage, is_active) VALUES
    (1, 'IVA21', 21.00, TRUE),
    (1, 'IVA10', 10.00, TRUE);

INSERT INTO service_categories (id_business, name, is_active) VALUES
    (1, 'Cortes', TRUE),
    (1, 'Tintes', TRUE),
    (1, 'Peinados', TRUE);

INSERT INTO services (id_business, id_category, id_tax, name, description, price, duration_minutes, is_active) VALUES
    (1, 1, 1, 'Corte caballero',  'Corte clasico de pelo para hombre',           15.00, 30, TRUE),
    (1, 1, 1, 'Corte senora',     'Corte y secado para mujer',                   25.00, 45, TRUE),
    (1, 2, 1, 'Tinte completo',   'Aplicacion de tinte en todo el cabello',      50.00, 90, TRUE),
    (1, 2, 1, 'Mechas',           'Mechas iluminadoras con papel de aluminio',   60.00,120, TRUE),
    (1, 3, 1, 'Peinado evento',   'Peinado para boda o evento especial',         35.00, 60, TRUE);

INSERT INTO booths (id_business, name, is_active) VALUES
    (1, 'Sala 1', TRUE),
    (1, 'Sala 2', TRUE);

INSERT INTO employee_schedules (id_membership, day_of_week, start_time, end_time) VALUES
    (2, 1, '09:00:00', '13:00:00'), (2, 1, '15:00:00', '18:00:00'),
    (2, 2, '09:00:00', '13:00:00'), (2, 2, '15:00:00', '18:00:00'),
    (2, 3, '09:00:00', '13:00:00'), (2, 3, '15:00:00', '18:00:00'),
    (2, 4, '09:00:00', '13:00:00'), (2, 4, '15:00:00', '18:00:00'),
    (2, 5, '09:00:00', '13:00:00'), (2, 5, '15:00:00', '18:00:00'),
    (3, 1, '09:00:00', '13:00:00'), (3, 1, '15:00:00', '18:00:00'),
    (3, 2, '09:00:00', '13:00:00'), (3, 2, '15:00:00', '18:00:00'),
    (3, 3, '09:00:00', '13:00:00'), (3, 3, '15:00:00', '18:00:00'),
    (3, 4, '09:00:00', '13:00:00'), (3, 4, '15:00:00', '18:00:00'),
    (3, 5, '09:00:00', '13:00:00'), (3, 5, '15:00:00', '18:00:00'),
    (3, 6, '10:00:00', '14:00:00'),
    (4, 2, '10:00:00', '14:00:00'), (4, 2, '16:00:00', '19:00:00'),
    (4, 3, '10:00:00', '14:00:00'), (4, 3, '16:00:00', '19:00:00'),
    (4, 4, '10:00:00', '14:00:00'), (4, 4, '16:00:00', '19:00:00'),
    (4, 5, '10:00:00', '14:00:00'), (4, 5, '16:00:00', '19:00:00'),
    (4, 6, '10:00:00', '14:00:00');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active) VALUES
    (1, 'Ana Garcia',     'ana.garcia@email.com',     '600222001', 'Prefiere citas por la manana',          TRUE),
    (1, 'Pedro Martinez', 'pedro.martinez@email.com', '600222002', 'Alergico al amoniaco',                  TRUE),
    (1, 'Laura Sanchez',  'laura.sanchez@email.com',  '600222003', NULL,                                    TRUE),
    (1, 'Miguel Lopez',   'miguel.lopez@email.com',   '600222004', 'Cliente fiel desde 2024',               TRUE),
    (1, 'Carmen Ruiz',    'carmen.ruiz@email.com',    '600222005', NULL,                                    TRUE);


INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid,
                          start_datetime, end_datetime, notes)
VALUES (1, 2, 2, 1, 1, FALSE,
        '2027-03-15 10:00:00', '2027-03-15 10:45:00',
        'Cita confirmada por telefono');
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage)
VALUES (1, 2, 25.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid,
                          start_datetime, end_datetime, notes)
VALUES (1, 3, 3, 2, 2, FALSE,
        '2027-03-15 11:00:00', '2027-03-15 11:30:00',
        'Cliente habitual');
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage)
VALUES (2, 1, 15.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid,
                          start_datetime, end_datetime, notes)
VALUES (1, 4, 4, 1, 3, FALSE,
        '2027-03-16 16:00:00', '2027-03-16 17:30:00',
        'Tinte de mantenimiento');
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage)
VALUES (3, 3, 50.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_status, is_paid,
                          start_datetime, end_datetime, notes)
VALUES (1, 5, 2, 4, TRUE,
        '2024-06-15 10:00:00', '2024-06-15 11:30:00',
        'Pagado en efectivo');
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES
    (4, 1, 15.00, 21.00),
    (4, 5, 35.00, 21.00);

INSERT INTO schedule_blocks (id_business, id_membership, id_booth, start_date, end_date, reason)
VALUES (1, NULL, NULL, '2027-05-15', '2027-05-15', 'San Isidro');

INSERT INTO employee_absences (id_membership, start_datetime, end_datetime, reason)
VALUES (3, '2027-03-17 09:00:00', '2027-03-17 13:00:00', 'Cita médica');



INSERT INTO users (full_name, email, password_hash, phone)
VALUES ('Lucia Fernandez', 'lucia@optima.com',
        '$2a$10$fCI9ZhcMUj5Z.fmPX2nZ7.SrSn22K42fxU8dvf8GCm8NUDoGud8xq',
        '600444001');
INSERT INTO users (full_name, email, password_hash, phone)
VALUES ('Javier Moreno', 'javier@optima.com',
        '$2a$10$Gs/mSNCqSc5puTJzCA0NIe23YqUEJtCG/YZ4WVep9L9SZZTb.DSy6',
        '600444002');
INSERT INTO users (full_name, email, password_hash, phone)
VALUES ('Sofia Romero', 'sofia@optima.com',
        '$2a$10$zBo7AGlqJF08rLwjLaUXV.D4aAFyFjwhutIlrPes6lVaJmbrvSP1u',
        '600444003');

INSERT INTO memberships (id_user, id_business, id_role) VALUES (1, 2, 2);
INSERT INTO memberships (id_user, id_business, id_role) VALUES (5, 2, 1);
INSERT INTO memberships (id_user, id_business, id_role) VALUES (6, 2, 2);
INSERT INTO memberships (id_user, id_business, id_role) VALUES (7, 2, 2);

INSERT INTO business_hours (id_business, day_of_week, start_time, end_time, is_closed) VALUES
    (2, 1, '10:00:00', '20:00:00', FALSE),
    (2, 2, '10:00:00', '20:00:00', FALSE),
    (2, 3, '10:00:00', '20:00:00', FALSE),
    (2, 4, '10:00:00', '20:00:00', FALSE),
    (2, 5, '10:00:00', '20:00:00', FALSE),
    (2, 6, '10:00:00', '15:00:00', FALSE),
    (2, 7, NULL, NULL, TRUE);

INSERT INTO taxes (id_business, name, percentage, is_active) VALUES
    (2, 'IVA21', 21.00, TRUE),
    (2, 'IVA10', 10.00, TRUE);

INSERT INTO service_categories (id_business, name, is_active) VALUES
    (2, 'Manicura y pedicura',  TRUE),
    (2, 'Tratamientos faciales', TRUE),
    (2, 'Masajes',              TRUE),
    (2, 'Depilacion',           TRUE);

INSERT INTO services (id_business, id_category, id_tax, name, description, price, duration_minutes, is_active, deactivated_at) VALUES
    (2, 4, 3, 'Manicura express',             'Limado y esmaltado rapido',               18.00,  30, TRUE,  NULL),
    (2, 4, 3, 'Manicura semipermanente',      'Esmaltado de larga duracion',             28.00,  45, TRUE,  NULL),
    (2, 4, 3, 'Pedicura spa',                 'Pedicura completa con exfoliacion',       35.00,  60, TRUE,  NULL),
    (2, 5, 3, 'Limpieza facial profunda',     'Higiene facial con extraccion',           45.00,  60, TRUE,  NULL),
    (2, 5, 3, 'Tratamiento antiedad',         'Tratamiento facial reafirmante',          70.00,  75, TRUE,  NULL),
    (2, 5, 3, 'Peeling facial',               'Exfoliacion quimica renovadora',          55.00,  50, TRUE,  NULL),
    (2, 6, 3, 'Masaje relajante',             'Masaje corporal de relajacion',           40.00,  50, TRUE,  NULL),
    (2, 6, 3, 'Masaje descontracturante',     'Masaje terapeutico de espalda',           50.00,  60, TRUE,  NULL),
    (2, 6, 3, 'Masaje con piedras calientes', 'Masaje con piedras volcanicas',           65.00,  75, TRUE,  NULL),
    (2, 7, 3, 'Depilacion con cera',          'Depilacion media pierna con cera tibia',  20.00,  30, TRUE,  NULL),
    (2, 7, 3, 'Depilacion facial',            'Depilacion de labio y ceja',              12.00,  20, FALSE, '2026-04-30 12:00:00');

INSERT INTO booths (id_business, name, is_active) VALUES
    (2, 'Cabina 1', TRUE),
    (2, 'Cabina 2', TRUE),
    (2, 'Cabina 3', TRUE);

INSERT INTO employee_schedules (id_membership, day_of_week, start_time, end_time) VALUES
    (5, 1, '10:00:00', '14:00:00'), (5, 1, '16:00:00', '20:00:00'),
    (5, 2, '10:00:00', '14:00:00'), (5, 2, '16:00:00', '20:00:00'),
    (5, 3, '10:00:00', '14:00:00'), (5, 3, '16:00:00', '20:00:00'),
    (5, 4, '10:00:00', '14:00:00'), (5, 4, '16:00:00', '20:00:00'),
    (5, 5, '10:00:00', '14:00:00'), (5, 5, '16:00:00', '20:00:00'),
    (6, 1, '10:00:00', '15:00:00'),
    (6, 2, '10:00:00', '15:00:00'),
    (6, 3, '10:00:00', '15:00:00'),
    (6, 4, '10:00:00', '15:00:00'),
    (6, 5, '10:00:00', '15:00:00'),
    (7, 1, '11:00:00', '15:00:00'), (7, 1, '16:00:00', '20:00:00'),
    (7, 2, '11:00:00', '15:00:00'), (7, 2, '16:00:00', '20:00:00'),
    (7, 3, '11:00:00', '15:00:00'), (7, 3, '16:00:00', '20:00:00'),
    (7, 4, '11:00:00', '15:00:00'), (7, 4, '16:00:00', '20:00:00'),
    (7, 5, '11:00:00', '15:00:00'), (7, 5, '16:00:00', '20:00:00'),
    (7, 6, '10:00:00', '15:00:00'),
    (8, 2, '10:00:00', '14:00:00'), (8, 2, '15:00:00', '19:00:00'),
    (8, 3, '10:00:00', '14:00:00'), (8, 3, '15:00:00', '19:00:00'),
    (8, 4, '10:00:00', '14:00:00'), (8, 4, '15:00:00', '19:00:00'),
    (8, 5, '10:00:00', '14:00:00'), (8, 5, '15:00:00', '19:00:00'),
    (8, 6, '10:00:00', '14:00:00'), (8, 6, '15:00:00', '19:00:00');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active, deactivated_at) VALUES
    (1, 'Raquel Ortega',   'raquel.ortega@email.com',   '600222006', 'Viene cada 3 semanas',      TRUE,  NULL),
    (1, 'David Castro',    'david.castro@email.com',    '600222007', NULL,                        TRUE,  NULL),
    (1, 'Elena Vidal',    'elena.vidal@email.com',     '600222008', 'Prefiere a Maria',          TRUE,  NULL),
    (1, 'Sergio Ramos',    'sergio.ramos@email.com',    '600222009', NULL,                        TRUE,  NULL),
    (1, 'Marta Gil',       'marta.gil@email.com',       '600222010', 'Paga siempre con tarjeta',  TRUE,  NULL),
    (1, 'Cliente Antiguo', 'cliente.antiguo@email.com', '600222011', 'Cuenta dada de baja',       FALSE, '2026-03-10 09:00:00');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active, deactivated_at) VALUES
    (2, 'Cristina Mora',  'cristina.mora@email.com',  '600333002', 'Piel sensible',           TRUE,  NULL),
    (2, 'Alberto Diaz',   'alberto.diaz@email.com',   '600333003', NULL,                      TRUE,  NULL),
    (2, 'Nuria Pascual',  'nuria.pascual@email.com',  '600333004', 'Bono de 5 masajes',       TRUE,  NULL),
    (2, 'Hugo Serrano',   'hugo.serrano@email.com',   '600333005', NULL,                      TRUE,  NULL),
    (2, 'Patricia Leon',  'patricia.leon@email.com',  '600333006', 'Alergica a la parafina',  TRUE,  NULL),
    (2, 'Andres Gomez',   'andres.gomez@email.com',   '600333007', NULL,                      TRUE,  NULL),
    (2, 'Lorena Campos',  'lorena.campos@email.com',  '600333008', 'Cita recurrente mensual', TRUE,  NULL),
    (2, 'Ivan Herrero',   'ivan.herrero@email.com',   '600333009', NULL,                      TRUE,  NULL),
    (2, 'Cliente Baja',   'cliente.baja@email.com',   '600333010', 'Cuenta dada de baja',     FALSE, '2026-04-05 18:00:00');

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes) VALUES
    (1,  2, 2, NULL, 4, TRUE,  '2026-04-13 10:00:00', '2026-04-13 10:30:00', 'Corte de mantenimiento'),
    (1,  3, 4, NULL, 4, TRUE,  '2026-04-15 16:00:00', '2026-04-15 18:00:00', 'Tinte y corte'),
    (1,  4, 3, NULL, 4, FALSE, '2026-04-21 11:00:00', '2026-04-21 13:00:00', 'Completada, pendiente de cobro'),
    (1,  5, 2, NULL, 5, FALSE, '2026-04-28 09:30:00', '2026-04-28 10:15:00', 'Anulada por el cliente'),
    (1,  6, 3, NULL, 6, FALSE, '2026-05-05 17:00:00', '2026-05-05 17:45:00', 'El cliente no se presento'),
    (1,  7, 4, NULL, 4, TRUE,  '2026-05-12 10:00:00', '2026-05-12 11:45:00', 'Peinado y corte para evento'),
    (1,  8, 2, 1,    3, FALSE, '2026-05-21 10:00:00', '2026-05-21 10:30:00', 'Cliente en sala'),
    (1,  9, 3, 2,    2, FALSE, '2026-05-21 11:00:00', '2026-05-21 12:30:00', 'Confirmada por telefono'),
    (1, 10, 4, 1,    1, FALSE, '2026-05-21 16:00:00', '2026-05-21 16:45:00', 'Pendiente de confirmar'),
    (1, 11, 2, 1,    2, FALSE, '2026-05-22 09:30:00', '2026-05-22 10:00:00', 'Primera visita'),
    (1,  2, 3, 2,    1, FALSE, '2026-05-22 12:00:00', '2026-05-22 13:00:00', 'Peinado para boda'),
    (1,  3, 2, 1,    2, FALSE, '2026-05-25 10:00:00', '2026-05-25 12:00:00', 'Reserva de mechas'),
    (1,  4, 4, 2,    1, FALSE, '2026-06-02 16:30:00', '2026-06-02 17:00:00', 'Solicitada por la web');

INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES
    ( 5, 1, 15.00, 21.00),
    ( 6, 3, 50.00, 21.00), ( 6, 1, 15.00, 21.00),
    ( 7, 4, 60.00, 21.00),
    ( 8, 2, 25.00, 21.00),
    ( 9, 2, 25.00, 21.00),
    (10, 5, 35.00, 21.00), (10, 2, 25.00, 21.00),
    (11, 1, 15.00, 21.00),
    (12, 3, 50.00, 21.00),
    (13, 2, 25.00, 21.00),
    (14, 1, 15.00, 21.00),
    (15, 5, 35.00, 21.00),
    (16, 4, 60.00, 21.00),
    (17, 1, 15.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes) VALUES
    (2, 13, 5, NULL, 4, TRUE,  '2026-04-14 10:00:00', '2026-04-14 10:45:00', 'Manicura quincenal'),
    (2, 14, 7, NULL, 4, TRUE,  '2026-04-16 11:00:00', '2026-04-16 12:00:00', 'Limpieza facial'),
    (2, 15, 5, NULL, 4, TRUE,  '2026-04-22 16:00:00', '2026-04-22 17:50:00', 'Sesion doble de masaje'),
    (2, 16, 8, NULL, 5, FALSE, '2026-04-29 12:00:00', '2026-04-29 13:00:00', 'Cancelada por la clienta'),
    (2,  1, 6, NULL, 4, TRUE,  '2026-05-06 10:30:00', '2026-05-06 12:15:00', 'Antiedad mas manicura'),
    (2, 17, 7, NULL, 6, FALSE, '2026-05-13 17:00:00', '2026-05-13 17:50:00', 'No acudio a la cita'),
    (2, 18, 5, 3,    3, FALSE, '2026-05-21 10:00:00', '2026-05-21 11:15:00', 'Sesion en curso'),
    (2, 19, 7, 4,    2, FALSE, '2026-05-21 12:00:00', '2026-05-21 13:00:00', 'Confirmada'),
    (2, 20, 8, 5,    1, FALSE, '2026-05-21 16:30:00', '2026-05-21 17:00:00', 'Pendiente de confirmar'),
    (2, 13, 5, 3,    2, FALSE, '2026-05-22 11:00:00', '2026-05-22 11:30:00', 'Manicura rapida'),
    (2, 14, 8, 4,    1, FALSE, '2026-05-22 13:00:00', '2026-05-22 13:50:00', 'Reserva de peeling'),
    (2, 15, 8, 3,    2, FALSE, '2026-05-26 10:00:00', '2026-05-26 11:00:00', 'Masaje confirmado'),
    (2,  1, 5, 5,    1, FALSE, '2026-06-03 17:00:00', '2026-06-03 18:15:00', 'Solicitada por la web');

INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES
    (18,  7, 28.00, 21.00),
    (19,  9, 45.00, 21.00),
    (20, 13, 50.00, 21.00), (20, 12, 40.00, 21.00),
    (21,  8, 35.00, 21.00),
    (22, 10, 70.00, 21.00), (22,  6, 18.00, 21.00),
    (23, 12, 40.00, 21.00),
    (24, 14, 65.00, 21.00),
    (25,  9, 45.00, 21.00),
    (26, 15, 20.00, 21.00),
    (27,  6, 18.00, 21.00),
    (28, 11, 55.00, 21.00),
    (29, 13, 50.00, 21.00),
    (30, 10, 70.00, 21.00);

INSERT INTO schedule_blocks (id_business, id_membership, id_booth, start_date, end_date, reason) VALUES
    (1, 4,    NULL, '2026-06-08', '2026-06-12', 'Vacaciones'),
    (2, NULL, 5,    '2026-06-15', '2026-06-16', 'Mantenimiento de cabina');

INSERT INTO employee_absences (id_membership, start_datetime, end_datetime, reason)
VALUES (8, '2026-05-27 10:00:00', '2026-05-27 14:00:00', 'Cita medica');


SET @biz_demo = (SELECT id_business FROM businesses WHERE slug = 'demo');
SET @role_employee = (SELECT id_role FROM roles WHERE name = 'EMPLOYEE');
SET @status_pending = (SELECT id_status FROM appointment_statuses WHERE name = 'PENDING');
SET @status_confirmed = (SELECT id_status FROM appointment_statuses WHERE name = 'CONFIRMED');
SET @status_completed = (SELECT id_status FROM appointment_statuses WHERE name = 'COMPLETED');
SET @status_cancelled = (SELECT id_status FROM appointment_statuses WHERE name = 'CANCELLED');

-- Tercera cabina para que el ADMIN vea 3 espacios de trabajo en su negocio.
INSERT IGNORE INTO booths (id_business, name, color, is_active)
VALUES (@biz_demo, 'Sala 3', '#F59E0B', TRUE);

SET @booth_sala1 = (SELECT id_booth FROM booths WHERE id_business = @biz_demo AND name = 'Sala 1');
SET @booth_sala2 = (SELECT id_booth FROM booths WHERE id_business = @biz_demo AND name = 'Sala 2');
SET @booth_sala3 = (SELECT id_booth FROM booths WHERE id_business = @biz_demo AND name = 'Sala 3');

-- Empleados extra para enseñar distintos tipos de jornada.
INSERT IGNORE INTO users (full_name, email, password_hash, phone)
VALUES ('Paula Navas', 'paula.navas@optima.com',
        '$2a$10$fCI9ZhcMUj5Z.fmPX2nZ7.SrSn22K42fxU8dvf8GCm8NUDoGud8xq',
        '600111005');

INSERT IGNORE INTO users (full_name, email, password_hash, phone)
VALUES ('Nerea Valverde', 'nerea.valverde@optima.com',
        '$2a$10$fCI9ZhcMUj5Z.fmPX2nZ7.SrSn22K42fxU8dvf8GCm8NUDoGud8xq',
        '600111006');

SET @u_paula = (SELECT id_user FROM users WHERE email = 'paula.navas@optima.com');
SET @u_nerea = (SELECT id_user FROM users WHERE email = 'nerea.valverde@optima.com');

INSERT IGNORE INTO memberships (id_user, id_business, id_role, color)
VALUES (@u_paula, @biz_demo, @role_employee, '#0EA5E9');

INSERT IGNORE INTO memberships (id_user, id_business, id_role, color)
VALUES (@u_nerea, @biz_demo, @role_employee, '#8B5CF6');

SET @m_empleado = (SELECT m.id_membership FROM memberships m JOIN users u ON u.id_user = m.id_user WHERE m.id_business = @biz_demo AND u.email = 'empleado@optima.com');
SET @m_maria = (SELECT m.id_membership FROM memberships m JOIN users u ON u.id_user = m.id_user WHERE m.id_business = @biz_demo AND u.email = 'maria@optima.com');
SET @m_carlos = (SELECT m.id_membership FROM memberships m JOIN users u ON u.id_user = m.id_user WHERE m.id_business = @biz_demo AND u.email = 'carlos@optima.com');
SET @m_paula = (SELECT m.id_membership FROM memberships m JOIN users u ON u.id_user = m.id_user WHERE m.id_business = @biz_demo AND u.email = 'paula.navas@optima.com');
SET @m_nerea = (SELECT m.id_membership FROM memberships m JOIN users u ON u.id_user = m.id_user WHERE m.id_business = @biz_demo AND u.email = 'nerea.valverde@optima.com');

-- Jornada completa: Paula trabaja en un unico tramo continuado.
INSERT INTO employee_schedules (id_membership, day_of_week, start_time, end_time) VALUES
                                                                                      (@m_paula, 1, '09:00:00', '17:00:00'),
                                                                                      (@m_paula, 2, '09:00:00', '17:00:00'),
                                                                                      (@m_paula, 3, '09:00:00', '17:00:00'),
                                                                                      (@m_paula, 4, '09:00:00', '17:00:00'),
                                                                                      (@m_paula, 5, '09:00:00', '17:00:00'),
                                                                                      (@m_paula, 6, '10:00:00', '14:00:00');

-- Jornada partida: Nerea trabaja manana y tarde con pausa al mediodia.
INSERT INTO employee_schedules (id_membership, day_of_week, start_time, end_time) VALUES
                                                                                      (@m_nerea, 1, '09:00:00', '13:00:00'), (@m_nerea, 1, '15:00:00', '18:00:00'),
                                                                                      (@m_nerea, 2, '09:00:00', '13:00:00'), (@m_nerea, 2, '15:00:00', '18:00:00'),
                                                                                      (@m_nerea, 3, '09:00:00', '13:00:00'), (@m_nerea, 3, '15:00:00', '18:00:00'),
                                                                                      (@m_nerea, 4, '09:00:00', '13:00:00'), (@m_nerea, 4, '15:00:00', '18:00:00'),
                                                                                      (@m_nerea, 5, '09:00:00', '13:00:00'), (@m_nerea, 5, '15:00:00', '18:00:00'),
                                                                                      (@m_nerea, 6, '10:00:00', '14:00:00');

-- Clientes adicionales del negocio Demo para llenar la agenda del ADMIN.
INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Clara Santos', 'clara.santos@email.com', '600555001', 'Prefiere primera hora', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'clara.santos@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Daniel Perez', 'daniel.perez@email.com', '600555002', 'Cliente nuevo', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'daniel.perez@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Sara Molina', 'sara.molina@email.com', '600555003', 'Quiere recordatorio por email', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'sara.molina@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Isabel Romero', 'isabel.romero@email.com', '600555004', 'Tinte sin amoniaco', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'isabel.romero@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Jorge Navarro', 'jorge.navarro@email.com', '600555005', 'Paga con tarjeta', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'jorge.navarro@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Paula Vega', 'paula.vega@email.com', '600555006', 'Preparacion para evento', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'paula.vega@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Marcos Prieto', 'marcos.prieto@email.com', '600555007', 'Cliente recurrente', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'marcos.prieto@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Alicia Torres', 'alicia.torres@email.com', '600555008', 'Prefiere Sala 2', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'alicia.torres@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Ruben Castillo', 'ruben.castillo@email.com', '600555009', 'Solo puede por la tarde', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'ruben.castillo@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Noelia Cano', 'noelia.cano@email.com', '600555010', 'Quiere presupuesto de color', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'noelia.cano@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Oscar Benitez', 'oscar.benitez@email.com', '600555011', 'Cita mensual', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'oscar.benitez@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Alba Reyes', 'alba.reyes@email.com', '600555012', 'Mechas para evento familiar', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'alba.reyes@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Fernando Soto', 'fernando.soto@email.com', '600555013', 'Necesita factura', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'fernando.soto@email.com');

INSERT INTO clients (id_business, full_name, email, phone, notes, is_active)
SELECT @biz_demo, 'Elena Suarez', 'elena.suarez@email.com', '600555014', 'Cambio de look', TRUE
    WHERE NOT EXISTS (SELECT 1 FROM clients WHERE id_business = @biz_demo AND email = 'elena.suarez@email.com');

SET @c_clara = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'clara.santos@email.com');
SET @c_daniel = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'daniel.perez@email.com');
SET @c_sara = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'sara.molina@email.com');
SET @c_isabel = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'isabel.romero@email.com');
SET @c_jorge = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'jorge.navarro@email.com');
SET @c_paula = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'paula.vega@email.com');
SET @c_marcos = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'marcos.prieto@email.com');
SET @c_alicia = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'alicia.torres@email.com');
SET @c_ruben = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'ruben.castillo@email.com');
SET @c_noelia = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'noelia.cano@email.com');
SET @c_oscar = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'oscar.benitez@email.com');
SET @c_alba = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'alba.reyes@email.com');
SET @c_fernando = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'fernando.soto@email.com');
SET @c_elena = (SELECT id_client FROM clients WHERE id_business = @biz_demo AND email = 'elena.suarez@email.com');

SET @svc_corte_caballero = (SELECT id_service FROM services WHERE id_business = @biz_demo AND name = 'Corte caballero');
SET @svc_corte_senora = (SELECT id_service FROM services WHERE id_business = @biz_demo AND name = 'Corte senora');
SET @svc_tinte = (SELECT id_service FROM services WHERE id_business = @biz_demo AND name = 'Tinte completo');
SET @svc_mechas = (SELECT id_service FROM services WHERE id_business = @biz_demo AND name = 'Mechas');
SET @svc_peinado = (SELECT id_service FROM services WHERE id_business = @biz_demo AND name = 'Peinado evento');

-- Ausencias puntuales para demostrar huecos reales en la agenda.
INSERT INTO employee_absences (id_membership, start_datetime, end_datetime, reason) VALUES
                                                                                        (@m_nerea,  '2026-06-02 09:00:00', '2026-06-02 10:00:00', 'Gestion personal'),
                                                                                        (@m_maria,  '2026-06-03 10:00:00', '2026-06-03 11:00:00', 'Cita medica'),
                                                                                        (@m_paula,  '2026-06-04 13:00:00', '2026-06-04 15:00:00', 'Formacion interna'),
                                                                                        (@m_carlos, '2026-06-05 16:00:00', '2026-06-05 17:00:00', 'Asunto familiar');

-- Bloqueos de agenda de ejemplo: una cabina y el cierre semanal.
INSERT INTO schedule_blocks (id_business, id_membership, id_booth, start_date, end_date, reason) VALUES
                                                                                                     (@biz_demo, NULL, @booth_sala3, '2026-06-05', '2026-06-05', 'Mantenimiento de Sala 3'),
                                                                                                     (@biz_demo, NULL, NULL,         '2026-06-07', '2026-06-07', 'Cierre por descanso semanal');

-- Citas de la semana 1-7 de junio de 2026 para el ADMIN.
-- Lunes 2026-06-01
INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_clara, @m_empleado, @booth_sala1, @status_confirmed, FALSE, '2026-06-01 09:00:00', '2026-06-01 09:30:00', 'Demo semana 1-7: corte rapido confirmado');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_caballero, 15.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_sara, @m_maria, @booth_sala2, @status_pending, FALSE, '2026-06-01 09:30:00', '2026-06-01 10:15:00', 'Demo semana 1-7: pendiente de confirmar');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_senora, 25.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_isabel, @m_paula, @booth_sala3, @status_confirmed, FALSE, '2026-06-01 10:30:00', '2026-06-01 12:00:00', 'Demo semana 1-7: tinte en nueva Sala 3');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_tinte, 50.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_jorge, @m_nerea, @booth_sala1, @status_confirmed, FALSE, '2026-06-01 15:00:00', '2026-06-01 16:00:00', 'Demo semana 1-7: jornada partida por la tarde');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_paula, @m_maria, @booth_sala2, @status_pending, FALSE, '2026-06-01 16:00:00', '2026-06-01 18:00:00', 'Demo semana 1-7: mechas pendientes');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_mechas, 60.00, 21.00);

-- Martes 2026-06-02
INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_marcos, @m_empleado, @booth_sala1, @status_confirmed, FALSE, '2026-06-02 09:00:00', '2026-06-02 09:45:00', 'Demo semana 1-7: corte de senora');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_senora, 25.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_ruben, @m_maria, @booth_sala2, @status_confirmed, TRUE, '2026-06-02 10:00:00', '2026-06-02 10:30:00', 'Demo semana 1-7: cita pagada');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_caballero, 15.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_noelia, @m_carlos, @booth_sala3, @status_pending, FALSE, '2026-06-02 11:00:00', '2026-06-02 12:30:00', 'Demo semana 1-7: color pendiente');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_tinte, 50.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_oscar, @m_paula, @booth_sala1, @status_confirmed, FALSE, '2026-06-02 15:00:00', '2026-06-02 16:00:00', 'Demo semana 1-7: empleado con jornada completa');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_alba, @m_nerea, @booth_sala3, @status_confirmed, FALSE, '2026-06-02 16:30:00', '2026-06-02 18:00:00', 'Demo semana 1-7: mechas sin conflicto con cita existente');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_mechas, 60.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_fernando, @m_empleado, @booth_sala1, @status_cancelled, FALSE, '2026-06-02 12:00:00', '2026-06-02 12:30:00', 'Demo semana 1-7: cita cancelada visible en historico');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_caballero, 15.00, 21.00);

-- Miercoles 2026-06-03
INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_elena, @m_empleado, @booth_sala1, @status_confirmed, FALSE, '2026-06-03 09:00:00', '2026-06-03 09:30:00', 'Demo semana 1-7: primera cita del dia');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_caballero, 15.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_clara, @m_paula, @booth_sala2, @status_pending, FALSE, '2026-06-03 10:00:00', '2026-06-03 10:45:00', 'Demo semana 1-7: hueco mientras Maria esta ausente');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_senora, 25.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_daniel, @m_carlos, @booth_sala3, @status_confirmed, FALSE, '2026-06-03 11:30:00', '2026-06-03 13:00:00', 'Demo semana 1-7: tinte confirmado');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_tinte, 50.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_sara, @m_maria, @booth_sala1, @status_confirmed, FALSE, '2026-06-03 15:00:00', '2026-06-03 16:00:00', 'Demo semana 1-7: Maria vuelve tras ausencia puntual');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_isabel, @m_nerea, @booth_sala2, @status_pending, FALSE, '2026-06-03 16:15:00', '2026-06-03 17:45:00', 'Demo semana 1-7: servicio largo por la tarde');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_mechas, 60.00, 21.00);

-- Jueves 2026-06-04
INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_jorge, @m_empleado, @booth_sala1, @status_confirmed, FALSE, '2026-06-04 09:30:00', '2026-06-04 10:15:00', 'Demo semana 1-7: corte de mantenimiento');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_senora, 25.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_paula, @m_maria, @booth_sala2, @status_confirmed, FALSE, '2026-06-04 10:30:00', '2026-06-04 11:30:00', 'Demo semana 1-7: peinado para evento');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_marcos, @m_nerea, @booth_sala1, @status_pending, FALSE, '2026-06-04 12:00:00', '2026-06-04 13:00:00', 'Demo semana 1-7: antes de pausa de jornada partida');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_alicia, @m_carlos, @booth_sala2, @status_confirmed, FALSE, '2026-06-04 16:00:00', '2026-06-04 17:30:00', 'Demo semana 1-7: tinte de tarde');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_tinte, 50.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_ruben, @m_paula, @booth_sala1, @status_pending, FALSE, '2026-06-04 15:00:00', '2026-06-04 16:00:00', 'Demo semana 1-7: Paula vuelve tras formacion');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

-- Viernes 2026-06-05
INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_noelia, @m_empleado, @booth_sala1, @status_confirmed, FALSE, '2026-06-05 09:00:00', '2026-06-05 09:30:00', 'Demo semana 1-7: cita corta');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_caballero, 15.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_oscar, @m_maria, @booth_sala2, @status_pending, FALSE, '2026-06-05 10:00:00', '2026-06-05 11:30:00', 'Demo semana 1-7: color pendiente');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_tinte, 50.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_alba, @m_paula, @booth_sala1, @status_confirmed, FALSE, '2026-06-05 11:30:00', '2026-06-05 12:15:00', 'Demo semana 1-7: Sala 3 bloqueada por mantenimiento');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_senora, 25.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_fernando, @m_nerea, @booth_sala2, @status_confirmed, FALSE, '2026-06-05 15:00:00', '2026-06-05 16:00:00', 'Demo semana 1-7: ultima cita antes de ausencia de Carlos');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_elena, @m_maria, @booth_sala1, @status_cancelled, FALSE, '2026-06-05 16:00:00', '2026-06-05 17:00:00', 'Demo semana 1-7: cancelada por cliente');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

-- Sabado 2026-06-06
INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_clara, @m_maria, @booth_sala1, @status_confirmed, FALSE, '2026-06-06 10:00:00', '2026-06-06 10:30:00', 'Demo semana 1-7: sabado por la manana');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_corte_caballero, 15.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_daniel, @m_carlos, @booth_sala2, @status_pending, FALSE, '2026-06-06 10:30:00', '2026-06-06 11:30:00', 'Demo semana 1-7: pendiente de confirmacion');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_isabel, @m_paula, @booth_sala3, @status_confirmed, FALSE, '2026-06-06 12:00:00', '2026-06-06 13:00:00', 'Demo semana 1-7: uso de tercera cabina en sabado');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

INSERT INTO appointments (id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
VALUES (@biz_demo, @c_jorge, @m_nerea, @booth_sala1, @status_confirmed, TRUE, '2026-06-06 13:00:00', '2026-06-06 14:00:00', 'Demo semana 1-7: ultima cita pagada del sabado');
SET @appt = LAST_INSERT_ID();
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage) VALUES (@appt, @svc_peinado, 35.00, 21.00);

-- Domingo 2026-06-07 queda sin citas para mostrar negocio cerrado y bloqueo global.

DROP USER IF EXISTS 'optima_user'@'%';
DROP USER IF EXISTS 'optima_user'@'localhost';
CREATE USER 'optima_user'@'%' IDENTIFIED BY 'optima_pass';
CREATE USER 'optima_user'@'localhost' IDENTIFIED BY 'optima_pass';
GRANT ALL PRIVILEGES ON optima_db.* TO 'optima_user'@'%';
GRANT ALL PRIVILEGES ON optima_db.* TO 'optima_user'@'localhost';
