package com.optima.api.modules.user.service;

import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.repository.MembershipRepository;
import com.optima.api.modules.user.dto.request.CreateEmployeeScheduleRequest;
import com.optima.api.modules.user.dto.request.UpdateEmployeeScheduleRequest;
import com.optima.api.modules.user.dto.response.EmployeeScheduleResponse;
import com.optima.api.modules.user.model.EmployeeSchedule;
import com.optima.api.modules.user.repository.EmployeeScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;

/**
 * EmployeeScheduleService - Capa de lógica de negocio para los horarios
 * semanales de empleados.
 * No usa soft delete: un tramo se borra (DELETE) o se reemplaza (PUT).
 *
 * Doble comprobación cross-tenant: cada operación valida que la membership
 * (empleado) pertenece al negocio antes de tocar el horario.
 *
 * COMUNICACION:
 * - Lo invoca: EmployeeScheduleController.
 * - Llama a:
 *     EmployeeScheduleRepository    CRUD + findByIdAndMembershipId,
 *                                   findAllByMembershipIdAndDayOfWeek (overlap).
 *     MembershipRepository.findByIdAndBusinessId  cross-tenant del empleado.
 * - Devuelve: EmployeeScheduleResponse.
 *
 * Tambien lo lee indirectamente: AppointmentValidator.validateEmployeeSchedule
 * usa EmployeeScheduleRepository para verificar que una cita encaja en el
 * horario del empleado.
 *
 * [v16 membership] El parametro externo se sigue llamando `userId` para no
 * romper los paths ya estables (/api/businesses/{id}/users/{userId}/schedules),
 * pero internamente es el id de la membership.
 *
 * Validacion de overlap dentro del mismo dia: al crear, comprueba que el
 * nuevo tramo no se solapa con otros de la misma (membership, dayOfWeek).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeScheduleService {

    private final EmployeeScheduleRepository scheduleRepository;
    private final MembershipRepository membershipRepository;

    /**
     * Crea un tramo del horario semanal para un empleado del negocio.
     *
     * Pasos:
     *   1. Verifica que la membership existe y pertenece al negocio
     *      (404 si no).
     *   2. Valida que startTime < endTime (400 si no).
     *   3. Comprueba que el nuevo tramo no se solapa con otros tramos
     *      existentes del mismo (membership, dayOfWeek), regla
     *      A < D AND C < B (409 si choca).
     *   4. Persiste el tramo.
     *
     * No valida unicidad por (membership, dayOfWeek) porque un empleado
     * puede tener turno partido (ej. Lunes 09-13 + Lunes 16-20).
     */
    public EmployeeScheduleResponse create(Long businessId, Long userId, CreateEmployeeScheduleRequest request) {
        Membership membership = ensureMembershipOfBusiness(businessId, userId);
        validateHours(request.startTime(), request.endTime());

        List<EmployeeSchedule> existingSchedules = scheduleRepository
                .findAllByMembershipIdAndDayOfWeek(userId, request.dayOfWeek());
        for (EmployeeSchedule existing : existingSchedules) {
            if (request.startTime().isBefore(existing.getEndTime())
                    && request.endTime().isAfter(existing.getStartTime())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El empleado ya tiene un tramo en ese horario para ese día");
            }
        }

        EmployeeSchedule s = new EmployeeSchedule();
        s.setMembership(membership);
        s.setDayOfWeek(request.dayOfWeek());
        s.setStartTime(request.startTime());
        s.setEndTime(request.endTime());

        return EmployeeScheduleResponse.from(scheduleRepository.save(s));
    }

    /**
     * Lista todos los tramos del horario semanal de un empleado, ordenados
     * por dia y hora de inicio.
     *
     * Devuelve List directo (sin paginar): la cardinalidad esta acotada
     * por diseno (max ~14 tramos = 7 dias x turno partido), asi que
     * paginar anyade complejidad sin valor. 404 si la membership no
     * pertenece al negocio.
     */
    @Transactional(readOnly = true)
    public List<EmployeeScheduleResponse> listByEmployee(Long businessId, Long userId) {
        ensureMembershipOfBusiness(businessId, userId);
        return scheduleRepository
                .findAllByMembershipIdOrderByDayOfWeekAscStartTimeAsc(userId)
                .stream().map(EmployeeScheduleResponse::from).toList();
    }

    /**
     * Detalle de un tramo. Doble proteccion tenant: 404 si la membership
     * no pertenece al negocio o si el tramo no pertenece a esa membership.
     */
    @Transactional(readOnly = true)
    public EmployeeScheduleResponse getById(Long businessId, Long userId, Long id) {
        ensureMembershipOfBusiness(businessId, userId);
        return EmployeeScheduleResponse.from(findOrThrow(userId, id));
    }

    /**
     * Sustituye dia, hora de inicio y hora de fin de un tramo existente.
     * 404 si el tramo no pertenece al empleado/negocio; 400 si
     * startTime >= endTime.
     *
     * Nota: NO revalida overlap con otros tramos (decision: el overlap
     * se chequea solo al crear; el ADMIN modifica con intencion y puede
     * borrar + crear si necesita rearmar el cuadro).
     */
    public EmployeeScheduleResponse update(Long businessId, Long userId, Long id, UpdateEmployeeScheduleRequest request) {
        ensureMembershipOfBusiness(businessId, userId);
        validateHours(request.startTime(), request.endTime());

        EmployeeSchedule s = findOrThrow(userId, id);
        s.setDayOfWeek(request.dayOfWeek());
        s.setStartTime(request.startTime());
        s.setEndTime(request.endTime());

        return EmployeeScheduleResponse.from(scheduleRepository.save(s));
    }

    /**
     * Hard delete del tramo (no es soft delete: un horario o existe o no existe).
     */
    public void delete(Long businessId, Long userId, Long id) {
        ensureMembershipOfBusiness(businessId, userId);
        EmployeeSchedule s = findOrThrow(userId, id);
        scheduleRepository.delete(s);
    }

    /**
     * Verifica que la membership existe y pertenece al negocio. Devuelve la
     * entidad por si el caller la necesita (lo aprovechamos en create).
     * Si no existe o pertenece a otro tenant, devuelve 404.
     */
    private Membership ensureMembershipOfBusiness(Long businessId, Long membershipId) {
        return membershipRepository.findByIdAndBusinessId(membershipId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + membershipId
                                + " en el negocio con ID: " + businessId));
    }

    private EmployeeSchedule findOrThrow(Long membershipId, Long id) {
        return scheduleRepository.findByIdAndMembershipId(id, membershipId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el horario con ID: " + id
                                + " para el empleado con ID: " + membershipId));
    }

    private void validateHours(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La hora de inicio debe ser anterior a la hora de fin");
        }
    }
}
