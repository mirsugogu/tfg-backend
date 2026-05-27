package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.dto.response.AvailabilityResponse;
import com.optima.api.modules.appointment.dto.response.AvailabilitySlotResponse;
import com.optima.api.modules.appointment.model.Appointment;
import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.business.model.Booth;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.BusinessHour;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.model.ScheduleBlock;
import com.optima.api.modules.business.repository.BoothRepository;
import com.optima.api.modules.business.repository.BusinessHourRepository;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.MembershipRepository;
import com.optima.api.modules.business.repository.ScheduleBlockRepository;
import com.optima.api.modules.catalog.model.BusinessService;
import com.optima.api.modules.catalog.repository.BusinessServiceRepository;
import com.optima.api.modules.user.model.EmployeeAbsence;
import com.optima.api.modules.user.model.EmployeeSchedule;
import com.optima.api.modules.user.repository.EmployeeAbsenceRepository;
import com.optima.api.modules.user.repository.EmployeeScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Calcula los huecos libres del negocio para una fecha y unos servicios elegidos. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AvailabilityService {

    private final BusinessRepository businessRepository;
    private final BusinessHourRepository businessHourRepository;
    private final BusinessServiceRepository serviceRepository;
    private final MembershipRepository membershipRepository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final EmployeeAbsenceRepository absenceRepository;
    private final BoothRepository boothRepository;
    private final ScheduleBlockRepository scheduleBlockRepository;
    private final AppointmentRepository appointmentRepository;

    /** Calcula slots libres aplicando negocio, empleado, cabina y bloqueos. */
    public AvailabilityResponse getAvailability(Long businessId,
                                                LocalDate date,
                                                List<Long> serviceIds,
                                                Long membershipId,
                                                Long boothId,
                                                Long excludeAppointmentId) {

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        int totalDuration = resolveServicesAndSumDuration(businessId, serviceIds);

        // Puede haber varios tramos abiertos en el mismo dia, por ejemplo turno partido.
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=Lunes..7=Domingo
        List<BusinessHour> openHours = businessHourRepository
                .findAllByBusinessIdAndDayOfWeekOrderByStartTimeAsc(businessId, dayOfWeek)
                .stream()
                .filter(h -> !Boolean.TRUE.equals(h.getIsClosed()))
                .toList();
        if (openHours.isEmpty()) {
            return new AvailabilityResponse(date, businessId, totalDuration, List.of());
        }

        // Un bloqueo global deja el dia sin huecos disponibles.
        List<ScheduleBlock> blocksOfDay =
                scheduleBlockRepository.findAllForDay(businessId, date);
        boolean hasGlobalBlock = blocksOfDay.stream()
                .anyMatch(b -> b.getMembership() == null && b.getBooth() == null);
        if (hasGlobalBlock) {
            return new AvailabilityResponse(date, businessId, totalDuration, List.of());
        }
        List<Long> blockedEmployeeIds = blocksOfDay.stream()
                .filter(b -> b.getMembership() != null)
                .map(b -> b.getMembership().getId())
                .toList();
        List<Long> blockedBoothIds = blocksOfDay.stream()
                .filter(b -> b.getBooth() != null)
                .map(b -> b.getBooth().getId())
                .toList();

        List<Membership> candidates = resolveEmployeeCandidates(businessId, membershipId);

        List<Booth> candidateBooths = resolveBoothCandidates(businessId, boothId);

        // Al editar, la cita actual no debe ocupar su propio slot.
        LocalDateTime dayWindowStart = date.atStartOfDay();
        LocalDateTime dayWindowEnd = date.plusDays(1).atStartOfDay();
        List<Appointment> activeAppointments =
                appointmentRepository.findActiveByBusinessAndDay(
                        businessId, dayWindowStart, dayWindowEnd)
                        .stream()
                        .filter(a -> excludeAppointmentId == null
                                || !excludeAppointmentId.equals(a.getId()))
                        .toList();

        // Se precargan horarios y ausencias para no consultar dentro del bucle.
        int interval = business.getAppointmentInterval();
        List<AvailabilitySlotResponse> slots = new ArrayList<>();

        List<Long> candidateIds = candidates.stream()
                .map(Membership::getId)
                .toList();

        Map<Long, List<EmployeeSchedule>> schedulesByEmp = candidateIds.isEmpty()
                ? Map.of()
                : scheduleRepository
                        .findAllByMembershipIdInAndDayOfWeek(candidateIds, dayOfWeek)
                        .stream()
                        .collect(Collectors.groupingBy(s -> s.getMembership().getId()));

        Map<Long, List<EmployeeAbsence>> absencesByEmp = candidateIds.isEmpty()
                ? Map.of()
                : absenceRepository
                        .findOverlappingForDayBatch(candidateIds, dayWindowStart, dayWindowEnd)
                        .stream()
                        .collect(Collectors.groupingBy(a -> a.getMembership().getId()));

        for (Membership emp : candidates) {
            if (blockedEmployeeIds.contains(emp.getId())) continue;

            List<EmployeeSchedule> ranges =
                    schedulesByEmp.getOrDefault(emp.getId(), List.of());
            if (ranges.isEmpty()) continue;

            List<EmployeeAbsence> absences =
                    absencesByEmp.getOrDefault(emp.getId(), List.of());

            List<Appointment> empAppts = activeAppointments.stream()
                    .filter(a -> a.getMembership().getId().equals(emp.getId()))
                    .toList();

            for (EmployeeSchedule range : ranges) {
                // Asi se respetan descansos del negocio aunque el empleado tenga horario continuo.
                for (BusinessHour bh : openHours) {
                    LocalDateTime dayStart = date.atTime(bh.getStartTime());
                    LocalDateTime dayEnd = date.atTime(bh.getEndTime());
                    addSlotsForEmployeeRange(
                            slots, date, range, dayStart, dayEnd,
                            interval, totalDuration,
                            emp, absences, empAppts,
                            candidateBooths, blockedBoothIds, activeAppointments
                    );
                }
            }
        }

        slots.sort(Comparator
                .comparing(AvailabilitySlotResponse::startTime)
                .thenComparing(AvailabilitySlotResponse::membershipId));

        return new AvailabilityResponse(date, businessId, totalDuration, slots);
    }

    private int resolveServicesAndSumDuration(Long businessId, List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe indicar al menos un servicio");
        }
        // Se quitan repetidos para no sumar dos veces el mismo servicio.
        Set<Long> uniqueIds = new HashSet<>(serviceIds);
        List<BusinessService> services =
                serviceRepository.findAllByIdInAndBusinessId(uniqueIds, businessId);

        if (services.size() != uniqueIds.size()) {
            Set<Long> found = services.stream()
                    .map(BusinessService::getId)
                    .collect(Collectors.toSet());
            Long missing = uniqueIds.stream()
                    .filter(id -> !found.contains(id))
                    .findFirst()
                    .orElseThrow();
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontró el servicio con ID: " + missing
                            + " en el negocio con ID: " + businessId);
        }
        for (BusinessService s : services) {
            if (!s.getIsActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El servicio con ID: " + s.getId() + " está desactivado");
            }
        }
        return services.stream().mapToInt(BusinessService::getDurationMinutes).sum();
    }

    private List<Membership> resolveEmployeeCandidates(Long businessId, Long membershipId) {
        if (membershipId == null) {
            return membershipRepository.findAllByBusinessIdAndIsActiveTrue(businessId);
        }
        Membership emp = membershipRepository.findByIdAndBusinessId(membershipId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + membershipId
                                + " en el negocio con ID: " + businessId));
        if (!emp.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El empleado con ID: " + membershipId + " está desactivado");
        }
        return List.of(emp);
    }

    private List<Booth> resolveBoothCandidates(Long businessId, Long boothId) {
        if (boothId == null) {
            return boothRepository.findAllByBusinessIdAndIsActiveTrue(businessId);
        }
        Booth b = boothRepository.findByIdAndBusinessId(boothId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró la cabina con ID: " + boothId
                                + " en el negocio con ID: " + businessId));
        if (!b.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cabina con ID: " + boothId + " está desactivada");
        }
        return List.of(b);
    }

    /**
     * Recorre un tramo del empleado en pasos del intervalo del negocio y
     * añade a `slots` cada hueco valido encontrado.
     */
    private void addSlotsForEmployeeRange(
            List<AvailabilitySlotResponse> slots,
            LocalDate date,
            EmployeeSchedule range,
            LocalDateTime dayStart,
            LocalDateTime dayEnd,
            int interval,
            int totalDuration,
            Membership emp,
            List<EmployeeAbsence> absences,
            List<Appointment> empAppts,
            List<Booth> candidateBooths,
            List<Long> blockedBoothIds,
            List<Appointment> allActiveAppointments
    ) {
        LocalDateTime rangeStart = max(date.atTime(range.getStartTime()), dayStart);
        LocalDateTime rangeEnd   = min(date.atTime(range.getEndTime()),   dayEnd);

        // Se redondea hacia arriba al intervalo configurado por el negocio.
        LocalDateTime cursor = alignUpToInterval(rangeStart, interval);

        while (!cursor.plusMinutes(totalDuration).isAfter(rangeEnd)) {
            LocalDateTime slotStart = cursor;
            LocalDateTime slotEnd = cursor.plusMinutes(totalDuration);

            if (!conflictsWith(slotStart, slotEnd, absences, empAppts)) {
                Booth booth = pickFreeBooth(
                        slotStart, slotEnd,
                        candidateBooths, blockedBoothIds, allActiveAppointments);

                // Sin cabinas configuradas, el slot es valido con booth=null.
                boolean boothConstraintSatisfied =
                        candidateBooths.isEmpty() || booth != null;

                if (boothConstraintSatisfied) {
                    slots.add(new AvailabilitySlotResponse(
                            slotStart.toLocalTime(),
                            slotEnd.toLocalTime(),
                            emp.getId(),
                            emp.getUser().getFullName(),
                            booth != null ? booth.getId() : null,
                            booth != null ? booth.getName() : null
                    ));
                }
            }
            cursor = cursor.plusMinutes(interval);
        }
    }

    private boolean conflictsWith(LocalDateTime start, LocalDateTime end,
                                  List<EmployeeAbsence> absences,
                                  List<Appointment> empAppts) {
        for (EmployeeAbsence a : absences) {
            if (start.isBefore(a.getEndDateTime()) && end.isAfter(a.getStartDateTime())) return true;
        }
        for (Appointment a : empAppts) {
            if (start.isBefore(a.getEndDateTime()) && end.isAfter(a.getStartDateTime())) return true;
        }
        return false;
    }

    /** Devuelve la primera cabina libre del rango, o null si no hay ninguna. */
    private Booth pickFreeBooth(LocalDateTime start, LocalDateTime end,
                                List<Booth> candidates,
                                List<Long> blockedBoothIds,
                                List<Appointment> activeAppointments) {
        for (Booth b : candidates) {
            if (blockedBoothIds.contains(b.getId())) continue;
            boolean overlap = activeAppointments.stream()
                    .filter(a -> a.getBooth() != null
                            && a.getBooth().getId().equals(b.getId()))
                    .anyMatch(a -> start.isBefore(a.getEndDateTime())
                            && end.isAfter(a.getStartDateTime()));
            if (!overlap) return b;
        }
        return null;
    }

    private LocalDateTime alignUpToInterval(LocalDateTime t, int interval) {
        int minute = t.getMinute();
        int mod = minute % interval;
        if (mod == 0 && t.getSecond() == 0 && t.getNano() == 0) return t;
        return t.plusMinutes((long) interval - mod)
                .withSecond(0).withNano(0);
    }

    private LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
