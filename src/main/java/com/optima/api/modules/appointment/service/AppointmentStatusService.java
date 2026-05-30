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

/** logica del catalogo de estados de cita */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AppointmentStatusService {

    private final AppointmentStatusRepository statusRepository;

    /** devuelve todos los estados de cita disponibles */
    public List<AppointmentStatusResponse> listAll() {
        return statusRepository.findAll()
                .stream()
                .map(AppointmentStatusResponse::from)
                .toList();
    }

    /** busca un estado por id */
    public AppointmentStatusResponse getStatusById(Long id) {
        AppointmentStatus status = statusRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el estado con ID: " + id
                ));

        return AppointmentStatusResponse.from(status);
    }
}
