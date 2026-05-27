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

/** Gestiona bloqueos de agenda globales, por empleado o por cabina. */
@Service
@Transactional
@RequiredArgsConstructor
public class ScheduleBlockService {

    private final ScheduleBlockRepository blockRepository;
    private final BusinessRepository businessRepository;
    private final MembershipRepository membershipRepository;
    private final BoothRepository boothRepository;

    /** Crea un bloqueo de agenda y valida sus referencias del negocio. */
    public ScheduleBlockResponse create(Long businessId, CreateScheduleBlockRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        if (request.startDate().isAfter(request.endDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio no puede ser posterior a la de fin");
        }

        // El bloqueo solo puede apuntar a un tipo de recurso.
        if (request.membershipId() != null && request.boothId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un bloqueo no puede dirigirse simultaneamente a un empleado "
                            + "y a una cabina. Indica solo uno (o ninguno para "
                            + "bloqueo global del negocio).");
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

    /** Devuelve un bloqueo del negocio. */
    @Transactional(readOnly = true)
    public ScheduleBlockResponse getById(Long businessId, Long id) {
        return ScheduleBlockResponse.from(findOrThrow(businessId, id));
    }

    /** Borra el bloqueo de la base de datos. */
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
