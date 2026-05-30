package com.optima.api.modules.user.service;

import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.repository.MembershipRepository;
import com.optima.api.modules.user.dto.request.CreateEmployeeAbsenceRequest;
import com.optima.api.modules.user.dto.request.UpdateEmployeeAbsenceRequest;
import com.optima.api.modules.user.dto.response.EmployeeAbsenceResponse;
import com.optima.api.modules.user.model.EmployeeAbsence;
import com.optima.api.modules.user.repository.EmployeeAbsenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** servicio de ausencias puntuales de empleados */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeAbsenceService {

    private final EmployeeAbsenceRepository absenceRepository;
    private final MembershipRepository membershipRepository;

    /**
     * lista ausencias del negocio que solapan con un rango
     */
    @Transactional(readOnly = true)
    public List<EmployeeAbsenceResponse> listByBusinessAndRange(Long businessId,
                                                                LocalDateTime from,
                                                                LocalDateTime to) {
        return absenceRepository.findOverlappingByBusinessAndRange(businessId, from, to)
                .stream().map(EmployeeAbsenceResponse::from).toList();
    }

    /**
     * crea una ausencia para un empleado del negocio
     */
    public EmployeeAbsenceResponse create(Long businessId, Long userId, CreateEmployeeAbsenceRequest request) {
        Membership membership = ensureMembershipOfBusiness(businessId, userId);
        validateRange(request.startDateTime(), request.endDateTime());

        List<EmployeeAbsence> existingAbsences = absenceRepository.findAllByMembershipId(userId);
        for (EmployeeAbsence existing : existingAbsences) {
            if (request.startDateTime().isBefore(existing.getEndDateTime())
                    && request.endDateTime().isAfter(existing.getStartDateTime())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El empleado ya tiene una ausencia en ese rango de fechas");
            }
        }

        EmployeeAbsence a = new EmployeeAbsence();
        a.setMembership(membership);
        a.setStartDateTime(request.startDateTime());
        a.setEndDateTime(request.endDateTime());
        a.setReason(normalize(request.reason()));

        return EmployeeAbsenceResponse.from(absenceRepository.save(a));
    }

    /**
     * lista de ausencias del empleado
     */
    @Transactional(readOnly = true)
    public Page<EmployeeAbsenceResponse> listByEmployee(Long businessId, Long userId, Pageable pageable) {
        ensureMembershipOfBusiness(businessId, userId);
        return absenceRepository.findByMembershipIdOrderByStartDateTimeAsc(userId, pageable)
                .map(EmployeeAbsenceResponse::from);
    }

    /**
     * obtiene una ausencia concreta del empleado
     */
    @Transactional(readOnly = true)
    public EmployeeAbsenceResponse getById(Long businessId, Long userId, Long id) {
        ensureMembershipOfBusiness(businessId, userId);
        return EmployeeAbsenceResponse.from(findOrThrow(userId, id));
    }

    /**
     * actualiza el rango y motivo de una ausencia existente
     */
    public EmployeeAbsenceResponse update(Long businessId, Long userId, Long id, UpdateEmployeeAbsenceRequest request) {
        ensureMembershipOfBusiness(businessId, userId);
        validateRange(request.startDateTime(), request.endDateTime());

        EmployeeAbsence a = findOrThrow(userId, id);
        validateNoOverlap(userId, request.startDateTime(), request.endDateTime(), id);

        a.setStartDateTime(request.startDateTime());
        a.setEndDateTime(request.endDateTime());
        a.setReason(normalize(request.reason()));

        return EmployeeAbsenceResponse.from(absenceRepository.save(a));
    }

    /**
     * elimina una ausencia
     */
    public void delete(Long businessId, Long userId, Long id) {
        ensureMembershipOfBusiness(businessId, userId);
        EmployeeAbsence a = findOrThrow(userId, id);
        absenceRepository.delete(a);
    }

    /**
     * comprueba que la relacion existe y pertenece al negocio
     */
    private Membership ensureMembershipOfBusiness(Long businessId, Long membershipId) {
        return membershipRepository.findByIdAndBusinessId(membershipId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + membershipId
                                + " en el negocio con ID: " + businessId));
    }

    /** busca la ausencia dentro de la relacion */
    private EmployeeAbsence findOrThrow(Long membershipId, Long id) {
        return absenceRepository.findByIdAndMembershipId(id, membershipId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la ausencia con ID: " + id
                                + " para el empleado con ID: " + membershipId));
    }

    /** valida que el inicio sea anterior al fin */
    private void validateRange(LocalDateTime start, LocalDateTime end) {
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin");
        }
    }

    /**
     * valida que el rango no solape con otras ausencias
     */
    private void validateNoOverlap(Long membershipId,
                                   LocalDateTime startDateTime,
                                   LocalDateTime endDateTime,
                                   Long excludeId) {
        List<EmployeeAbsence> existingAbsences = absenceRepository.findAllByMembershipId(membershipId);
        for (EmployeeAbsence existing : existingAbsences) {
            if (excludeId != null && excludeId.equals(existing.getId())) continue;
            if (startDateTime.isBefore(existing.getEndDateTime())
                    && endDateTime.isAfter(existing.getStartDateTime())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El empleado ya tiene una ausencia en ese rango de fechas");
            }
        }
    }

    /**
     * limpia motivos vacios
     */
    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
