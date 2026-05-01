package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.dto.response.AppointmentStatusResponse;
import com.optima.api.modules.appointment.model.AppointmentStatus;
import com.optima.api.modules.appointment.repository.AppointmentStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentStatusService {

    private final AppointmentStatusRepository statusRepository;

    /**
     * Devuelve todos los estados de cita disponibles.
     */
    @Transactional(readOnly = true)
    public List<AppointmentStatusResponse> getAllStatuses() {
        return statusRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Busca un estado por su ID.
     * Si no existe, lanza una excepción.
     */
    @Transactional(readOnly = true)
    public AppointmentStatusResponse getStatusById(Long id) {
        AppointmentStatus status = statusRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el estado con ID: " + id
                ));

        return toResponse(status);
    }

    /**
     * Método privado que convierte la entidad al DTO de respuesta.
     * Centraliza el mapeo para no repetirlo en cada método.
     */
    private AppointmentStatusResponse toResponse(AppointmentStatus status) {
        return new AppointmentStatusResponse(
                status.getId(),
                status.getName()
        );
    }
}