package com.optima.api.modules.appointment.service;

import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.business.model.ScheduleBlock;
import com.optima.api.modules.business.repository.ScheduleBlockRepository;
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
 *
 * COMUNICACION:
 * - Lo invoca: AppointmentService (en createAppointment y
 *   updateAppointmentStatus).
 * - Llama a:
 *     AppointmentRepository.existsOverlappingAppointment (query JPQL custom).
 *     EmployeeScheduleRepository.findAllByMembershipIdAndDayOfWeek.
 * - No devuelve nada: cada metodo lanza ResponseStatusException si una
 *   regla falla, o no hace nada si todo esta bien (fail-fast).
 *
 * [v16 membership] Los parametros llamados `membershipId` son en realidad el
 * id de la membership (pertenencia usuario-negocio). El nombre externo se
 * mantiene por compatibilidad con la API; internamente es membershipId.
 *
 * 6 validaciones publicas (en orden de invocacion tipica):
 *   validateAppointmentInterval 400 si la hora no es multiplo del intervalo.
 *   validateEmployeeSchedule    400 si la cita cae fuera del horario del
 *                               empleado o cruza medianoche.
 *   validateNoOverlap           409 si solapa con otra cita activa del
 *                               empleado.
 *   validateNoBoothOverlap      409 si solapa con otra cita en la misma
 *                               cabina (solo si la cita lleva cabina).
 *   validateNoScheduleBlock     409 si la fecha cae en un bloqueo de
 *                               agenda (global, por empleado o por cabina).
 *   validateStatusTransition    400 si la transicion de estado es ilegal
 *                               (ver mapa VALID_TRANSITIONS).
 */
@Component
@RequiredArgsConstructor
public class AppointmentValidator {

    private final AppointmentRepository appointmentRepository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final ScheduleBlockRepository scheduleBlockRepository;

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
     * Comprobar que no hay otra cita que se solape
     * con el mismo empleado en el rango de tiempo dado.
     */
    public void validateNoOverlap(Long membershipId,
                                  LocalDateTime startDateTime,
                                  LocalDateTime endDateTime) {

        boolean hasOverlap = appointmentRepository.existsOverlappingAppointment(
                membershipId, startDateTime, endDateTime
        );

        if (hasOverlap) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El empleado ya tiene una cita en ese horario"
            );
        }
    }

    /**
     * Validacion ortogonal a la del empleado: comprueba que la cabina no
     * esta ocupada por otra cita activa en el rango. Solo se invoca cuando
     * la cita lleva boothId (no aplica si el negocio no usa cabinas).
     */
    public void validateNoBoothOverlap(Long boothId,
                                       LocalDateTime startDateTime,
                                       LocalDateTime endDateTime) {

        boolean hasOverlap = appointmentRepository.existsOverlappingBoothAppointment(
                boothId, startDateTime, endDateTime
        );

        if (hasOverlap) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La cabina ya tiene una cita en ese horario"
            );
        }
    }

    /**
     * Comprueba que la fecha de la cita no caiga en un bloqueo de agenda
     * (schedule_block) aplicable.
     *
     * Un bloqueo aplica a la cita si su rango [startDate, endDate] incluye
     * la fecha de la cita Y se da alguno de estos casos:
     *   - global (employee NULL y booth NULL): aplica a todo el negocio.
     *   - dirigido al empleado de la cita.
     *   - dirigido a la cabina de la cita (si la cita lleva cabina).
     *
     * Si encuentra al menos un bloqueo aplicable, devuelve 409 con el
     * `reason` del primero para que el frontend muestre la razon ("La
     * fecha está bloqueada por: San Isidro").
     */
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

    /**
     * Comprobar que la cita cae dentro del horario
     * de trabajo del empleado para ese día de la semana.
     *
     * Un empleado puede tener varios tramos en un día (ej: mañana y tarde).
     * La cita es válida si encaja completamente dentro de alguno de esos tramos.
     */
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

    /**
     * Comprobar que la hora de inicio de la cita
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
     * Comprobar que la transición de estado es válida.
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