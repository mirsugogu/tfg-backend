package com.optima.api.modules.business.service;

import com.optima.api.modules.business.dto.response.BusinessHourResponse;
import com.optima.api.modules.business.dto.request.CreateBusinessHourRequest;
import com.optima.api.modules.business.dto.request.UpdateBusinessHourRequest;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.BusinessHour;
import com.optima.api.modules.business.repository.BusinessHourRepository;
import com.optima.api.modules.business.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;

/**
 * BusinessHourService - Logica del horario semanal de apertura del negocio.
 * No usa soft delete: un horario se borra (DELETE) o se reemplaza (PUT).
 *
 * COMUNICACION:
 * - Lo invoca: BusinessHourController.
 * - Llama a:
 *     BusinessHourRepository       CRUD + existsByBusinessIdAndDayOfWeek.
 *     BusinessRepository.findById  verifica que el negocio existe.
 * - Devuelve: BusinessHourResponse.
 *
 * Helper applyHours(): centraliza la coherencia entre isClosed y las
 * horas (si cerrado -> horas null; si abierto -> ambas obligatorias y
 * start < end). Lo usan create y update.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BusinessHourService {

    private final BusinessHourRepository hourRepository;
    private final BusinessRepository businessRepository;

    /**
     * Crea un tramo horario para un dia del negocio. Falla si ya existe otro
     * tramo para el mismo dia.
     */
    public BusinessHourResponse create(Long businessId, CreateBusinessHourRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        if (hourRepository.existsByBusinessIdAndDayOfWeek(businessId, request.dayOfWeek())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un horario para ese día en este negocio");
        }

        BusinessHour bh = new BusinessHour();
        bh.setBusiness(business);
        bh.setDayOfWeek(request.dayOfWeek());
        applyHours(bh, request.isClosed(), request.startTime(), request.endTime());

        return BusinessHourResponse.from(hourRepository.save(bh));
    }

    /**
     * Lista los tramos horarios del negocio ordenados de lunes a domingo.
     */
    @Transactional(readOnly = true)
    public List<BusinessHourResponse> listByBusiness(Long businessId) {
        return hourRepository.findAllByBusinessIdOrderByDayOfWeekAsc(businessId)
                .stream().map(BusinessHourResponse::from).toList();
    }

    /**
     * Obtiene un tramo por ID dentro del negocio (cross-tenant safe).
     */
    @Transactional(readOnly = true)
    public BusinessHourResponse getById(Long businessId, Long id) {
        return BusinessHourResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza dia y horas del tramo. Si se cambia el dia, valida que no
     * choque con otro tramo del mismo negocio.
     */
    public BusinessHourResponse update(Long businessId, Long id, UpdateBusinessHourRequest request) {
        BusinessHour bh = findOrThrow(businessId, id);

        if (!bh.getDayOfWeek().equals(request.dayOfWeek()) &&
                hourRepository.existsByBusinessIdAndDayOfWeek(businessId, request.dayOfWeek())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un horario para ese día en este negocio");
        }

        bh.setDayOfWeek(request.dayOfWeek());
        applyHours(bh, request.isClosed(), request.startTime(), request.endTime());

        return BusinessHourResponse.from(hourRepository.save(bh));
    }

    /**
     * Hard delete del tramo (no es soft delete: un horario o existe o no existe).
     */
    public void delete(Long businessId, Long id) {
        BusinessHour bh = findOrThrow(businessId, id);
        hourRepository.delete(bh);
    }

    /**
     * Aplica la logica de coherencia entre isClosed y las horas.
     * - Si esta cerrado: las horas se ponen a null.
     * - Si esta abierto: ambas horas son obligatorias y start < end.
     */
    private void applyHours(BusinessHour bh, Boolean isClosed,
                            LocalTime startTime, LocalTime endTime) {
        boolean closed = Boolean.TRUE.equals(isClosed);
        if (closed) {
            bh.setIsClosed(true);
            bh.setStartTime(null);
            bh.setEndTime(null);
            return;
        }

        if (startTime == null || endTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las horas de inicio y fin son obligatorias cuando el negocio está abierto");
        }
        if (!startTime.isBefore(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La hora de inicio debe ser anterior a la hora de fin");
        }
        bh.setIsClosed(false);
        bh.setStartTime(startTime);
        bh.setEndTime(endTime);
    }

    private BusinessHour findOrThrow(Long businessId, Long id) {
        return hourRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el horario con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
