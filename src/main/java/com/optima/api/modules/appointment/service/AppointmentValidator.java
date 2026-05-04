package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.user.model.EmployeeSchedule;
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
 * Se separa del service para mantener el código organizado
 * y que cada clase tenga una única responsabilidad.
 */
@Component
@RequiredArgsConstructor
public class AppointmentValidator {

    private final AppointmentRepository appointmentRepository;
    private final EmployeeScheduleRepository scheduleRepository;

    /**
     * Mapa que define las transiciones de estado permitidas.
     * Clave = estado actual, Valor = estados a los que puede pasar.
     * Los estados finales (COMPLETED, CANCELLED, NO_SHOW) no aparecen
     * como clave porque no permiten ninguna transición.
     */
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "PENDING", Set.of("CONFIRMED", "CANCELLED"),
            "CONFIRMED", Set.of("IN_PROGRESS", "CANCELLED", "NO_SHOW"),
            "IN_PROGRESS", Set.of("COMPLETED", "CANCELLED")
    );

    /**
     * Validación 1: Comprobar que no hay otra cita que se solape
     * con el mismo empleado en el rango de tiempo dado.
     */
    public void validateNoOverlap(Long employeeId,
                                  LocalDateTime startDateTime,
                                  LocalDateTime endDateTime) {

        boolean hasOverlap = appointmentRepository.existsOverlappingAppointment(
                employeeId, startDateTime, endDateTime
        );

        if (hasOverlap) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El empleado ya tiene una cita en ese horario"
            );
        }
    }

    /**
     * Validación 2: Comprobar que la cita cae dentro del horario
     * de trabajo del empleado para ese día de la semana.
     *
     * Un empleado puede tener varios tramos en un día (ej: mañana y tarde).
     * La cita es válida si encaja completamente dentro de alguno de esos tramos.
     */
    public void validateEmployeeSchedule(Long employeeId,
                                         LocalDateTime startDateTime,
                                         LocalDateTime endDateTime) {

        byte dayOfWeek = (byte) startDateTime.getDayOfWeek().getValue();

        List<EmployeeSchedule> schedules = scheduleRepository.findAllByUserIdAndDayOfWeek(
                employeeId,
                (int) dayOfWeek
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

    /**
     * Validación 3: Comprobar que la hora de inicio de la cita
     * respeta el intervalo configurado del negocio.
     *
     * Si el negocio tiene appointment_interval = 30, las citas
     * deben empezar en minutos múltiplos de 30 (00, 30).
     * Si es 15, pueden empezar en 00, 15, 30 o 45.
     */
    public void validateAppointmentInterval(LocalDateTime startDateTime,
                                            Integer interval) {
        int minutes = startDateTime.getMinute();

        if (minutes % interval != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La hora de inicio debe ser múltiplo de "
                            + interval + " minutos"
            );
        }
    }

    /**
     * Validación 4: Comprobar que la transición de estado es válida.
     * Ejemplo: PENDING → CONFIRMED es válido,
     * pero PENDING → COMPLETED no lo es.
     * Los estados COMPLETED, CANCELLED y NO_SHOW son finales
     * y no permiten ninguna transición.
     */
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