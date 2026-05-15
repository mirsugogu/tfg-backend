package com.optima.api.modules.business.service;

import com.optima.api.modules.business.dto.request.CreateScheduleBlockRequest;
import com.optima.api.modules.business.dto.response.ScheduleBlockResponse;
import com.optima.api.modules.business.model.Booth;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.model.ScheduleBlock;
import com.optima.api.modules.business.repository.BoothRepository;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.MembershipRepository;
import com.optima.api.modules.business.repository.ScheduleBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * ScheduleBlockService - Logica de bloqueos de agenda.
 *
 * COMUNICACION:
 * - Lo invoca: ScheduleBlockController.
 * - Llama a:
 *     ScheduleBlockRepository  CRUD + findApplicableBlocks.
 *     BusinessRepository       verifica que el negocio existe.
 *     MembershipRepository     cross-tenant del empleado si viene.
 *     BoothRepository          cross-tenant de la cabina si viene.
 * - Devuelve: ScheduleBlockResponse.
 *
 * [v16 membership] El "empleado" del bloqueo es una Membership; el
 * membershipId del request es realmente el id de la membership.
 *
 * Sin soft delete: los bloqueos son eventos puntuales. Si el ADMIN se
 * equivoca, hard-delete y vuelve a crear.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ScheduleBlockService {

    private final ScheduleBlockRepository blockRepository;
    private final BusinessRepository businessRepository;
    private final MembershipRepository membershipRepository;
    private final BoothRepository boothRepository;

    /**
     * Crea un bloqueo de agenda. Segun los IDs presentes en el request es:
     * global (ninguno), por empleado (membershipId) o por cabina (boothId).
     * Valida que las referencias cruzadas pertenecen al negocio y que
     * startDate no es posterior a endDate.
     */
    public ScheduleBlockResponse create(Long businessId, CreateScheduleBlockRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        if (request.startDate().isAfter(request.endDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio no puede ser posterior a la de fin");
        }

        Membership membership = null;
        if (request.membershipId() != null) {
            membership = membershipRepository.findByIdAndBusinessId(request.membershipId(), businessId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No se encontró el empleado con ID: " + request.membershipId()
                                    + " en el negocio con ID: " + businessId));
        }

        Booth booth = null;
        if (request.boothId() != null) {
            booth = boothRepository.findByIdAndBusinessId(request.boothId(), businessId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No se encontró la cabina con ID: " + request.boothId()
                                    + " en el negocio con ID: " + businessId));
        }

        ScheduleBlock b = new ScheduleBlock();
        b.setBusiness(business);
        b.setMembership(membership);
        b.setBooth(booth);
        b.setStartDate(request.startDate());
        b.setEndDate(request.endDate());
        b.setReason(request.reason());

        return ScheduleBlockResponse.from(blockRepository.save(b));
    }

    /**
     * Listado paginado de bloqueos del negocio (orden cronologico ASC).
     */
    @Transactional(readOnly = true)
    public Page<ScheduleBlockResponse> listByBusiness(Long businessId, Pageable pageable) {
        return blockRepository.findByBusinessIdOrderByStartDateAsc(businessId, pageable)
                .map(ScheduleBlockResponse::from);
    }

    /**
     * Detalle de un bloqueo por id dentro del negocio (cross-tenant safe).
     * Lanza 404 si no existe o pertenece a otro negocio.
     */
    @Transactional(readOnly = true)
    public ScheduleBlockResponse getById(Long businessId, Long id) {
        return ScheduleBlockResponse.from(findOrThrow(businessId, id));
    }

    /** Hard delete: el bloqueo desaparece de la BD. */
    public void delete(Long businessId, Long id) {
        ScheduleBlock b = findOrThrow(businessId, id);
        blockRepository.delete(b);
    }

    private ScheduleBlock findOrThrow(Long businessId, Long id) {
        return blockRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el bloqueo con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
