package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.business.model.BusinessHour;
import com.optima.api.modules.business.model.ScheduleBlock;
import com.optima.api.modules.business.repository.BusinessHourRepository;
import com.optima.api.modules.business.repository.ScheduleBlockRepository;
import com.optima.api.modules.user.model.EmployeeAbsence;
import com.optima.api.modules.user.model.EmployeeSchedule;
import com.optima.api.modules.user.repository.EmployeeAbsenceRepository;
import com.optima.api.modules.user.repository.EmployeeScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clase dedicada a las validaciones complejas de citas.
 */
@Component
@RequiredArgsConstructor
public class AppointmentValidator {

    private final AppointmentRepository appointmentRepository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final EmployeeAbsenceRepository absenceRepository;
    private final ScheduleBlockRepository scheduleBlockRepository;
    private final BusinessHourRepository businessHourRepository;

    /** Transiciones de estado permitidas para una cita. */
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "PENDING", Set.of("CONFIRMED", "CANCELLED"),
            "CONFIRMED", Set.of("IN_PROGRESS", "CANCELLED", "NO_SHOW"),
            "IN_PROGRESS", Set.of("COMPLETED", "CANCELLED")
    );

    /**
     * Comprobar que no hay otra cita que se solape con el mismo empleado en
     * el rango de tiempo dado.
     */
    public void validateNoOverlap(Long membershipId,
                                  LocalDateTime startDateTime,
                                  LocalDateTime endDateTime,
                                  Long excludeAppointmentId) {

        boolean hasOverlap = (excludeAppointmentId == null)
                ? appointmentRepository.existsOverlappingAppointment(
                        membershipId, startDateTime, endDateTime)
                : appointmentRepository.existsOverlappingAppointmentExcluding(
                        membershipId, startDateTime, endDateTime, excludeAppointmentId);

        if (hasOverlap) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El empleado ya tiene una cita en ese horario"
            );
        }
    }

    /** Comprueba que el empleado no tenga ausencias en el rango indicado. */
    public void validateNoEmployeeAbsence(Long membershipId,
                                          LocalDateTime startDateTime,
                                          LocalDateTime endDateTime) {

        List<EmployeeAbsence> overlapping = absenceRepository
                .findOverlappingByMembershipAndRange(membershipId, startDateTime, endDateTime);

        if (!overlapping.isEmpty()) {
            String reason = overlapping.get(0).getReason();
            String msg = reason != null
                    ? "El empleado tiene una ausencia: " + reason
                    : "El empleado tiene una ausencia en ese horario";
            throw new ResponseStatusException(HttpStatus.CONFLICT, msg);
        }
    }

    /** Comprueba que una cabina no tenga otra cita activa en el mismo rango. */
    public void validateNoBoothOverlap(Long boothId,
                                       LocalDateTime startDateTime,
                                       LocalDateTime endDateTime,
                                       Long excludeAppointmentId) {

        boolean hasOverlap = (excludeAppointmentId == null)
                ? appointmentRepository.existsOverlappingBoothAppointment(
                        boothId, startDateTime, endDateTime)
                : appointmentRepository.existsOverlappingBoothAppointmentExcluding(
                        boothId, startDateTime, endDateTime, excludeAppointmentId);

        if (hasOverlap) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La cabina ya tiene una cita en ese horario"
            );
        }
    }

    /** Comprueba que la cita no caiga en un bloqueo de agenda. */
    public void validateNoScheduleBlock(Long businessId,
                                        Long membershipId,
                                        Long boothId,
                                        LocalDateTime startDateTime) {

        List<ScheduleBlock> blocks = scheduleBlockRepository.findApplicableBlocks(
                businessId,
                startDateTime.toLocalDate(),
                membershipId,
                boothId
        );

        if (!blocks.isEmpty()) {
            String reason = blocks.get(0).getReason();
            String msg = reason != null
                    ? "La fecha está bloqueada por: " + reason
                    : "La fecha está bloqueada por un bloqueo de agenda";
            throw new ResponseStatusException(HttpStatus.CONFLICT, msg);
        }
    }

    /** Comprueba que la cita encaje en el horario del empleado. */
    public void validateEmployeeSchedule(Long membershipId,
                                         LocalDateTime startDateTime,
                                         LocalDateTime endDateTime) {

        if (!startDateTime.toLocalDate().equals(endDateTime.toLocalDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cita no puede cruzar medianoche"
            );
        }

        int dayOfWeek = startDateTime.getDayOfWeek().getValue();

        List<EmployeeSchedule> schedules = scheduleRepository.findAllByMembershipIdAndDayOfWeek(
                membershipId,
                dayOfWeek
        );

        if (schedules.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El empleado no trabaja el día seleccionado"
            );
        }

        LocalTime appointmentStart = startDateTime.toLocalTime();
        LocalTime appointmentEnd = endDateTime.toLocalTime();

        boolean fitsInAnySchedule = schedules.stream()
                .anyMatch(schedule ->
                        !appointmentStart.isBefore(schedule.getStartTime())
                                && !appointmentEnd.isAfter(schedule.getEndTime())
                );

        if (!fitsInAnySchedule) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cita no encaja en el horario de trabajo del empleado"
            );
        }
    }

    /** Comprueba que la cita respete el intervalo configurado. */
    public void validateAppointmentInterval(LocalDateTime startDateTime,
                                            Integer interval) {
        if (interval == null || interval <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error de configuración: el intervalo de cita del negocio "
                            + "no es válido (" + interval + ")"
            );
        }

        int minutes = startDateTime.getMinute();

        if (minutes % interval != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La hora de inicio debe ser múltiplo de "
                            + interval + " minutos"
            );
        }
    }

    /** Comprueba que la cita encaje en el horario de apertura del negocio. */
    public void validateBusinessHours(Long businessId,
                                      LocalDateTime startDateTime,
                                      LocalDateTime endDateTime) {
        int dayOfWeek = startDateTime.getDayOfWeek().getValue();
        List<BusinessHour> hoursOfDay =
                businessHourRepository.findAllByBusinessIdAndDayOfWeekOrderByStartTimeAsc(
                        businessId, dayOfWeek);

        LocalTime appointmentStart = startDateTime.toLocalTime();
        LocalTime appointmentEnd = endDateTime.toLocalTime();

        boolean fits = hoursOfDay.stream()
                .filter(h -> !Boolean.TRUE.equals(h.getIsClosed()))
                .anyMatch(h -> !appointmentStart.isBefore(h.getStartTime())
                        && !appointmentEnd.isAfter(h.getEndTime()));

        if (!fits) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El negocio está cerrado en ese día y horario"
            );
        }
    }

    /** Comprueba que la transicion de estado sea valida. */
    public void validateStatusTransition(String currentStatus, String newStatus) {

        Set<String> allowedTargets = VALID_TRANSITIONS.get(currentStatus);

        if (allowedTargets == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cita en estado " + currentStatus
                            + " no puede cambiar de estado. Es un estado final"
            );
        }

        if (!allowedTargets.contains(newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede pasar de " + currentStatus
                            + " a " + newStatus + ". Transiciones válidas: "
                            + allowedTargets
            );
        }
    }
}
