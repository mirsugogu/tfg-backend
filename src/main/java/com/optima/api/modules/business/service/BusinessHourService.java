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

/** Logica del horario semanal de apertura del negocio. */
@Service
@Transactional
@RequiredArgsConstructor
public class BusinessHourService {

    private final BusinessHourRepository hourRepository;
    private final BusinessRepository businessRepository;

    /**
     * Crea un tramo horario para un dia del negocio. Si el nuevo tramo es
     * abierto, valida que no se solape con otros tramos abiertos del mismo
     * dia (regla A<D AND C<B; 409 si choca).
     */
    public BusinessHourResponse create(Long businessId, CreateBusinessHourRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        validateNoOverlap(businessId, request.dayOfWeek(),
                request.isClosed(), request.startTime(), request.endTime(),
                null);

        BusinessHour bh = new BusinessHour();
        bh.setBusiness(business);
        bh.setDayOfWeek(request.dayOfWeek());
        applyHours(bh, request.isClosed(), request.startTime(), request.endTime());

        return BusinessHourResponse.from(hourRepository.save(bh));
    }

    /**
     * Lista los tramos horarios del negocio ordenados por dia (lunes-domingo)
     * y, dentro del mismo dia, por hora de inicio.
     */
    @Transactional(readOnly = true)
    public List<BusinessHourResponse> listByBusiness(Long businessId) {
        return hourRepository.findAllByBusinessIdOrderByDayOfWeekAscStartTimeAsc(businessId)
                .stream().map(BusinessHourResponse::from).toList();
    }

    /**
     * Obtiene un tramo por ID dentro del negocio (cross-tenant safe).
     */
    @Transactional(readOnly = true)
    public BusinessHourResponse getById(Long businessId, Long id) {
        return BusinessHourResponse.from(findOrThrow(businessId, id));
    }

    /** Sustituye dia, hora de inicio y hora de fin de un tramo existente. */
    public BusinessHourResponse update(Long businessId, Long id, UpdateBusinessHourRequest request) {
        BusinessHour bh = findOrThrow(businessId, id);

        validateNoOverlap(businessId, request.dayOfWeek(),
                request.isClosed(), request.startTime(), request.endTime(),
                id);

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

    /** Valida que el tramo no se solape con otros horarios abiertos. */
    private void validateNoOverlap(Long businessId, Integer dayOfWeek,
                                   Boolean isClosed, LocalTime startTime, LocalTime endTime,
                                   Long excludeId) {
        if (Boolean.TRUE.equals(isClosed)) return;
        if (startTime == null || endTime == null) return;

        List<BusinessHour> sameDay =
                hourRepository.findAllByBusinessIdAndDayOfWeekOrderByStartTimeAsc(businessId, dayOfWeek);
        for (BusinessHour existing : sameDay) {
            if (excludeId != null && excludeId.equals(existing.getId())) continue;
            if (Boolean.TRUE.equals(existing.getIsClosed())) continue;
            if (startTime.isBefore(existing.getEndTime())
                    && endTime.isAfter(existing.getStartTime())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe un tramo que se solapa con ese horario en este día");
            }
        }
    }

    /** Aplica la coherencia entre el cierre del dia y sus horas. */
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
