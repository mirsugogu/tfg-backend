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

/**
 * AvailabilityService - Calcula los huecos libres del negocio para una
 * fecha y unos servicios elegidos.
 *
 * Algoritmo: interseccion de calendarios. Para que un slot sea valido:
 *   1. El negocio abre ese dia (business_hours.is_closed=false).
 *   2. No hay schedule_block global aplicable.
 *   3. Existe al menos una MEMBERSHIP (empleado) candidata que:
 *      - Trabaja ese dia_of_week (employee_schedules).
 *      - No tiene schedule_block dirigido a ella.
 *      - El slot encaja en alguno de sus tramos (mañana/tarde) menos
 *        sus employee_absences del dia menos sus citas activas.
 *   4. Si el negocio tiene CABINAS configuradas:
 *      - Hay al menos una cabina libre (sin schedule_block, sin cita
 *        activa solapada).
 *      - Si no hay cabinas en el negocio, el constraint no aplica.
 *
 * [v16 membership] El concepto de "empleado" del path externo es ahora una
 * membership (pertenencia user-business). El DTO de salida mantiene
 * `membershipId` y `userFullName` por compatibilidad con el contrato del API.
 *
 * Granularidad: la hora de inicio del slot es multiplo del
 * appointmentInterval del negocio.
 *
 * COMUNICACION:
 * - Lo invoca: AvailabilityController.
 * - Llama a 9 repos (read-only): BusinessRepository, BusinessHourRepository,
 *   BusinessServiceRepository, MembershipRepository, EmployeeScheduleRepository,
 *   EmployeeAbsenceRepository, BoothRepository, ScheduleBlockRepository,
 *   AppointmentRepository.
 * - Devuelve: AvailabilityResponse con la lista plana de slots ordenada
 *   por hora de inicio asc y membershipId asc.
 */
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

    /**
     * Punto de entrada. Aplica las 4 capas del algoritmo y devuelve la
     * lista plana de slots libres ordenada (startTime asc, membershipId asc).
     *
     * @param businessId  negocio del path (cross-tenant garantizado por filtro).
     * @param date        fecha consultada (un solo dia).
     * @param serviceIds  servicios a reservar; su duracion total es la
     *                    longitud de cada slot.
     * @param membershipId  opcional; si viene, restringe a ese empleado.
     * @param boothId     opcional; si viene, restringe a esa cabina.
     */
    public AvailabilityResponse getAvailability(Long businessId,
                                                LocalDate date,
                                                List<Long> serviceIds,
                                                Long membershipId,
                                                Long boothId) {

        // 1) Verificar negocio
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        // 2) Resolver servicios -> sumar duracion total
        int totalDuration = resolveServicesAndSumDuration(businessId, serviceIds);

        // 3) Horario de apertura del negocio para el dia consultado
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=Lunes..7=Domingo
        BusinessHour hours = businessHourRepository
                .findByBusinessIdAndDayOfWeek(businessId, dayOfWeek)
                .orElse(null);
        if (hours == null || Boolean.TRUE.equals(hours.getIsClosed())) {
            return new AvailabilityResponse(date, businessId, totalDuration, List.of());
        }

        LocalDateTime dayStart = date.atTime(hours.getStartTime());
        LocalDateTime dayEnd = date.atTime(hours.getEndTime());

        // 4) Bloqueos del dia: globales abortan; los del recurso se reparten
        //    en Java para no lanzar N queries.
        List<ScheduleBlock> blocksOfDay =
                scheduleBlockRepository.findAllForDay(businessId, date);
        boolean hasGlobalBlock = blocksOfDay.stream()
                .anyMatch(b -> b.getMembership() == null && b.getBooth() == null);
        if (hasGlobalBlock) {
            return new AvailabilityResponse(date, businessId, totalDuration, List.of());
        }
        // [v16 membership] blocked employee ids son ids de membership.
        List<Long> blockedEmployeeIds = blocksOfDay.stream()
                .filter(b -> b.getMembership() != null)
                .map(b -> b.getMembership().getId())
                .toList();
        List<Long> blockedBoothIds = blocksOfDay.stream()
                .filter(b -> b.getBooth() != null)
                .map(b -> b.getBooth().getId())
                .toList();

        // 5) Memberships candidatas (filtro por membershipId si viene; el
        //    membershipId externo es realmente el id de la membership).
        List<Membership> candidates = resolveEmployeeCandidates(businessId, membershipId);

        // 6) Cabinas candidatas (filtro por boothId si viene). Lista vacia =
        //    el negocio no tiene cabinas o las que tiene estan filtradas; el
        //    constraint cabina libre solo aplica si la lista NO esta vacia.
        List<Booth> candidateBooths = resolveBoothCandidates(businessId, boothId);

        // 7) Citas activas del dia agrupadas por empleado y por cabina
        LocalDateTime dayWindowStart = date.atStartOfDay();
        LocalDateTime dayWindowEnd = date.plusDays(1).atStartOfDay();
        List<Appointment> activeAppointments =
                appointmentRepository.findActiveByBusinessAndDay(
                        businessId, dayWindowStart, dayWindowEnd);

        // 8) Recorrer memberships (empleados) y construir slots.
        //    Precarga batch (anti-N+1): UNA query a employee_schedules y UNA
        //    a employee_absences para TODOS los empleados candidatos, en vez
        //    de N+N dentro del bucle. Agrupamos por membershipId con
        //    Collectors.groupingBy y luego leemos del Map dentro del for.
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
                addSlotsForEmployeeRange(
                        slots, date, range, dayStart, dayEnd,
                        interval, totalDuration,
                        emp, absences, empAppts,
                        candidateBooths, blockedBoothIds, activeAppointments
                );
            }
        }

        // 9) Orden estable por hora asc y luego membershipId asc
        slots.sort(Comparator
                .comparing(AvailabilitySlotResponse::startTime)
                .thenComparing(AvailabilitySlotResponse::membershipId));

        return new AvailabilityResponse(date, businessId, totalDuration, slots);
    }

    // ------------------------------------------------------------------
    // helpers privados
    // ------------------------------------------------------------------

    private int resolveServicesAndSumDuration(Long businessId, List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe indicar al menos un servicio");
        }
        // Una sola query para todos los servicios del request (anti-N+1).
        // De-duplicamos por si el caller mando el mismo serviceId repetido:
        // contaria duracion doble y devolveria size desigual aunque todos
        // existan.
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
        // Intersectar el tramo del empleado con el horario de apertura
        LocalDateTime rangeStart = max(date.atTime(range.getStartTime()), dayStart);
        LocalDateTime rangeEnd   = min(date.atTime(range.getEndTime()),   dayEnd);

        // Alinear el inicio al multiplo del intervalo (redondeo hacia arriba)
        LocalDateTime cursor = alignUpToInterval(rangeStart, interval);

        while (!cursor.plusMinutes(totalDuration).isAfter(rangeEnd)) {
            LocalDateTime slotStart = cursor;
            LocalDateTime slotEnd = cursor.plusMinutes(totalDuration);

            if (!conflictsWith(slotStart, slotEnd, absences, empAppts)) {
                Booth booth = pickFreeBooth(
                        slotStart, slotEnd,
                        candidateBooths, blockedBoothIds, allActiveAppointments);

                // Si NO hay cabinas configuradas en el negocio, el constraint
                // no aplica: el slot es valido con booth=null.
                // Si SI hay cabinas pero ninguna libre: slot descartado.
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

    /**
     * Devuelve la primera cabina libre para el rango, o null si ninguna
     * lo esta. Si la lista de candidatas viene vacia (negocio sin cabinas)
     * devuelve null y el caller decide si es valido o no.
     */
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
