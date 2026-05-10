package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.dto.response.AppointmentStatusResponse;
import com.optima.api.modules.appointment.model.AppointmentStatus;
import com.optima.api.modules.appointment.repository.AppointmentStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * AppointmentStatusService - Logica del catalogo de estados de cita.
 *
 * COMUNICACION:
 * - Lo invoca: AppointmentStatusController (catalogo publico).
 * - Llama a: AppointmentStatusRepository.
 * - Devuelve: AppointmentStatusResponse(s).
 *
 * @Transactional(readOnly = true) en clase: solo lectura, sin escritura.
 *
 * Los estados son un catalogo GLOBAL (compartido por todos los negocios),
 * sembrado en docs/schema_v13.sql: PENDING, CONFIRMED, IN_PROGRESS,
 * COMPLETED, CANCELLED, NO_SHOW.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AppointmentStatusService {

    private final AppointmentStatusRepository statusRepository;

    /**
     * Devuelve todos los estados de cita disponibles.
     */
    public List<AppointmentStatusResponse> getAllStatuses() {
        return statusRepository.findAll()
                .stream()
                .map(AppointmentStatusResponse::from)
                .toList();
    }

    /**
     * Busca un estado por su ID.
     * Si no existe, lanza una excepción.
     */
    public AppointmentStatusResponse getStatusById(Long id) {
        AppointmentStatus status = statusRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el estado con ID: " + id
                ));

        return AppointmentStatusResponse.from(status);
    }
}
