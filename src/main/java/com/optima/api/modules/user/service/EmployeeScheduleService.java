package com.optima.api.modules.user.service;

import com.optima.api.modules.user.dto.request.CreateScheduleRequest;
import com.optima.api.modules.user.dto.request.UpdateScheduleRequest;
import com.optima.api.modules.user.dto.response.ScheduleResponse;
import com.optima.api.modules.user.model.EmployeeSchedule;
import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.EmployeeScheduleRepository;
import com.optima.api.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;

/**
 * Capa de lógica de negocio para los horarios semanales de empleados.
 * No usa soft delete: un tramo se borra (DELETE) o se reemplaza (PUT).
 *
 * Doble comprobación cross-tenant: cada operación valida que el empleado
 * pertenece al negocio antes de tocar el horario.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeScheduleService {

    private final EmployeeScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    public ScheduleResponse create(Long businessId, Long userId, CreateScheduleRequest req) {
        User employee = ensureEmployeeOfBusiness(businessId, userId);
        validateHours(req.startTime(), req.endTime());

        EmployeeSchedule s = new EmployeeSchedule();
        s.setUser(employee);
        s.setDayOfWeek(req.dayOfWeek());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());

        return ScheduleResponse.from(scheduleRepository.save(s));
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> listByEmployee(Long businessId, Long userId) {
        ensureEmployeeOfBusiness(businessId, userId);
        return scheduleRepository
                .findAllByUserIdOrderByDayOfWeekAscStartTimeAsc(userId)
                .stream().map(ScheduleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse getById(Long businessId, Long userId, Long id) {
        ensureEmployeeOfBusiness(businessId, userId);
        return ScheduleResponse.from(findOrThrow(userId, id));
    }

    public ScheduleResponse update(Long businessId, Long userId, Long id, UpdateScheduleRequest req) {
        ensureEmployeeOfBusiness(businessId, userId);
        validateHours(req.startTime(), req.endTime());

        EmployeeSchedule s = findOrThrow(userId, id);
        s.setDayOfWeek(req.dayOfWeek());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());

        return ScheduleResponse.from(scheduleRepository.save(s));
    }

    /**
     * Hard delete del tramo (no es soft delete: un horario o existe o no existe).
     */
    public void delete(Long businessId, Long userId, Long id) {
        ensureEmployeeOfBusiness(businessId, userId);
        EmployeeSchedule s = findOrThrow(userId, id);
        scheduleRepository.delete(s);
    }

    /**
     * Verifica que el empleado pertenece al negocio. Devuelve la entidad
     * por si el caller la necesita (lo aprovechamos en {@code create}).
     * Si no existe o pertenece a otro tenant, devuelve 404.
     */
    private User ensureEmployeeOfBusiness(Long businessId, Long userId) {
        return userRepository.findByIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + userId
                                + " en el negocio con ID: " + businessId));
    }

    private EmployeeSchedule findOrThrow(Long userId, Long id) {
        return scheduleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el horario con ID: " + id
                                + " para el empleado con ID: " + userId));
    }

    private void validateHours(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La hora de inicio debe ser anterior a la hora de fin");
        }
    }
}
