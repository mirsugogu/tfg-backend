package com.optima.api.modules.appointment.repository;

import com.optima.api.modules.appointment.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppointmentStatusRepository extends JpaRepository<AppointmentStatus, Long> {

    /**
     * Busca un estado por su nombre exacto (ej: "PENDING").
     * Devuelve Optional porque en teoría podría no existir.
     */
    Optional<AppointmentStatus> findByName(String name);
}