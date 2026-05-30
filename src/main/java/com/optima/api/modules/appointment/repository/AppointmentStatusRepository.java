package com.optima.api.modules.appointment.repository;

import com.optima.api.modules.appointment.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** consultas de los estados de cita */
@Repository
public interface AppointmentStatusRepository extends JpaRepository<AppointmentStatus, Long> {

    /**
     * busca un estado por su nombre exacto
     * puede no existir y por eso devuelve optional
     */
    Optional<AppointmentStatus> findByName(String name);
}
