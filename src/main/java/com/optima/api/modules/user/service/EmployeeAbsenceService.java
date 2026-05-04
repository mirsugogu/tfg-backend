package com.optima.api.modules.user.service;

import com.optima.api.modules.user.dto.request.CreateAbsenceRequest;
import com.optima.api.modules.user.dto.request.UpdateAbsenceRequest;
import com.optima.api.modules.user.dto.response.AbsenceResponse;
import com.optima.api.modules.user.model.EmployeeAbsence;
import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.EmployeeAbsenceRepository;
import com.optima.api.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Capa de lógica de negocio para las ausencias puntuales de empleados.
 * No usa soft delete: una ausencia se cancela borrándola.
 *
 * Doble comprobación cross-tenant: cada operación valida que el empleado
 * pertenece al negocio antes de tocar la ausencia.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeAbsenceService {

    private final EmployeeAbsenceRepository absenceRepository;
    private final UserRepository userRepository;

    public AbsenceResponse create(Long businessId, Long userId, CreateAbsenceRequest req) {
        User employee = ensureEmployeeOfBusiness(businessId, userId);
        validateRange(req.startDateTime(), req.endDateTime());

        EmployeeAbsence a = new EmployeeAbsence();
        a.setEmployee(employee);
        a.setStartDateTime(req.startDateTime());
        a.setEndDateTime(req.endDateTime());
        a.setReason(normalize(req.reason()));

        return AbsenceResponse.from(absenceRepository.save(a));
    }

    @Transactional(readOnly = true)
    public List<AbsenceResponse> listByEmployee(Long businessId, Long userId) {
        ensureEmployeeOfBusiness(businessId, userId);
        return absenceRepository.findAllByEmployeeIdOrderByStartDateTimeAsc(userId)
                .stream().map(AbsenceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AbsenceResponse getById(Long businessId, Long userId, Long id) {
        ensureEmployeeOfBusiness(businessId, userId);
        return AbsenceResponse.from(findOrThrow(userId, id));
    }

    public AbsenceResponse update(Long businessId, Long userId, Long id, UpdateAbsenceRequest req) {
        ensureEmployeeOfBusiness(businessId, userId);
        validateRange(req.startDateTime(), req.endDateTime());

        EmployeeAbsence a = findOrThrow(userId, id);
        a.setStartDateTime(req.startDateTime());
        a.setEndDateTime(req.endDateTime());
        a.setReason(normalize(req.reason()));

        return AbsenceResponse.from(absenceRepository.save(a));
    }

    /**
     * Hard delete (la ausencia se cancela borrándola).
     */
    public void delete(Long businessId, Long userId, Long id) {
        ensureEmployeeOfBusiness(businessId, userId);
        EmployeeAbsence a = findOrThrow(userId, id);
        absenceRepository.delete(a);
    }

    /**
     * Verifica que el empleado pertenece al negocio. Si no existe o
     * pertenece a otro tenant, devuelve 404. Devuelve la entidad para
     * poder reutilizarla en {@code create}.
     */
    private User ensureEmployeeOfBusiness(Long businessId, Long userId) {
        return userRepository.findByIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + userId
                                + " en el negocio con ID: " + businessId));
    }

    private EmployeeAbsence findOrThrow(Long userId, Long id) {
        return absenceRepository.findByIdAndEmployeeId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la ausencia con ID: " + id
                                + " para el empleado con ID: " + userId));
    }

    private void validateRange(LocalDateTime start, LocalDateTime end) {
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin");
        }
    }

    /**
     * Normaliza un motivo opcional: si llega vacío o solo espacios, lo
     * almacena como null.
     */
    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
