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
 *     BusinessHourRepository       CRUD + findAllByBusinessIdAndDayOfWeek (overlap).
 *     BusinessRepository.findById  verifica que el negocio existe.
 * - Devuelve: BusinessHourResponse.
 *
 * Turno partido (P1-negocio): un mismo dia puede tener varios tramos
 * (10-14 + 16-20). La no-superposicion se valida en create con el mismo
 * patron A<D AND C<B que EmployeeScheduleService.
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

    /**
     * Sustituye dia, hora de inicio y hora de fin de un tramo existente.
     * 404 si el tramo no pertenece al negocio.
     *
     * Nota: simetria con EmployeeScheduleService.update — NO revalida overlap
     * con otros tramos (decision: el overlap se chequea solo al crear; el
     * ADMIN modifica con intencion y puede borrar + crear si necesita rearmar
     * el cuadro).
     */
    public BusinessHourResponse update(Long businessId, Long id, UpdateBusinessHourRequest request) {
        BusinessHour bh = findOrThrow(businessId, id);

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
     * Valida que el nuevo tramo no se solape con otros tramos abiertos del
     * mismo (business, dayOfWeek). Aplica solo si el nuevo tramo es abierto:
     * un tramo cerrado es un "marcador" sin horas y nunca solapa.
     *
     * Regla de solape (mismas semanticas que EmployeeScheduleService):
     *   nuevo.start < existente.end AND nuevo.end > existente.start
     *
     * @param excludeId  id a excluir del check (util al actualizar, aunque
     *                   por simetria con EmployeeScheduleService el update
     *                   actual no usa este metodo; queda preparado por si
     *                   en el futuro se quiere revalidar).
     */
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
