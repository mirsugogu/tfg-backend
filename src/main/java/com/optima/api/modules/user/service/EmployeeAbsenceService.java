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

/**
 * EmployeeAbsenceService - Capa de lógica de negocio para las ausencias
 * puntuales de empleados.
 * No usa soft delete: una ausencia se cancela borrándola.
 *
 * Doble comprobación cross-tenant: cada operación valida que la membership
 * pertenece al negocio antes de tocar la ausencia.
 *
 * COMUNICACION:
 * - Lo invoca: EmployeeAbsenceController.
 * - Llama a:
 *     EmployeeAbsenceRepository     CRUD + findByIdAndMembershipId,
 *                                   findAllByMembershipId (overlap check).
 *     MembershipRepository.findByIdAndBusinessId  cross-tenant del empleado.
 * - Devuelve: EmployeeAbsenceResponse.
 *
 * [v16 membership] El parametro externo se sigue llamando `userId` por
 * compatibilidad con los paths existentes; internamente es el id de la
 * membership.
 *
 * Validacion de overlap: al crear, comprueba que el rango no se solapa
 * con otra ausencia ya registrada de la misma membership
 * (regla A < D AND C < B).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeAbsenceService {

    private final EmployeeAbsenceRepository absenceRepository;
    private final MembershipRepository membershipRepository;

    /**
     * Crea una ausencia para un empleado del negocio.
     *
     * Pasos:
     *   1. Verifica que la membership existe y pertenece al negocio
     *      (404 si no).
     *   2. Valida que startDateTime < endDateTime (400 si no).
     *   3. Comprueba que el rango no se solapa con otra ausencia ya
     *      registrada de la misma membership, regla A < D AND C < B
     *      (409 si choca).
     *   4. Crea la entidad, normaliza el motivo (trim + null si vacio)
     *      y persiste.
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
     * Lista paginada de ausencias del empleado, ordenadas por fecha de
     * inicio ascendente por defecto (el Sort del Pageable prevalece si
     * el cliente envia ?sort=...). 404 si la membership no pertenece al
     * negocio.
     */
    @Transactional(readOnly = true)
    public Page<EmployeeAbsenceResponse> listByEmployee(Long businessId, Long userId, Pageable pageable) {
        ensureMembershipOfBusiness(businessId, userId);
        return absenceRepository.findByMembershipIdOrderByStartDateTimeAsc(userId, pageable)
                .map(EmployeeAbsenceResponse::from);
    }

    /**
     * Detalle de una ausencia. Doble proteccion tenant: 404 si la
     * membership no pertenece al negocio o si la ausencia no pertenece a
     * esa membership.
     */
    @Transactional(readOnly = true)
    public EmployeeAbsenceResponse getById(Long businessId, Long userId, Long id) {
        ensureMembershipOfBusiness(businessId, userId);
        return EmployeeAbsenceResponse.from(findOrThrow(userId, id));
    }

    /**
     * Actualiza el rango y el motivo de una ausencia existente.
     * 404 si la ausencia no pertenece al empleado/negocio; 400 si
     * startDateTime >= endDateTime.
     *
     * Nota: NO revalida overlap con otras ausencias (decision: el
     * overlap se chequea solo al crear; un update puede ampliar/reducir
     * un rango ya conocido sin friccion).
     */
    public EmployeeAbsenceResponse update(Long businessId, Long userId, Long id, UpdateEmployeeAbsenceRequest request) {
        ensureMembershipOfBusiness(businessId, userId);
        validateRange(request.startDateTime(), request.endDateTime());

        EmployeeAbsence a = findOrThrow(userId, id);
        a.setStartDateTime(request.startDateTime());
        a.setEndDateTime(request.endDateTime());
        a.setReason(normalize(request.reason()));

        return EmployeeAbsenceResponse.from(absenceRepository.save(a));
    }

    /**
     * Hard delete (la ausencia se cancela borrándola).
     */
    public void delete(Long businessId, Long userId, Long id) {
        ensureMembershipOfBusiness(businessId, userId);
        EmployeeAbsence a = findOrThrow(userId, id);
        absenceRepository.delete(a);
    }

    /**
     * Verifica que la membership existe y pertenece al negocio. Si no
     * existe o pertenece a otro tenant, devuelve 404. Devuelve la entidad
     * para poder reutilizarla en create.
     */
    private Membership ensureMembershipOfBusiness(Long businessId, Long membershipId) {
        return membershipRepository.findByIdAndBusinessId(membershipId, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + membershipId
                                + " en el negocio con ID: " + businessId));
    }

    private EmployeeAbsence findOrThrow(Long membershipId, Long id) {
        return absenceRepository.findByIdAndMembershipId(id, membershipId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la ausencia con ID: " + id
                                + " para el empleado con ID: " + membershipId));
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
