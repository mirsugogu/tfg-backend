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
 * Se separa del service para mantener el código organizado
 * y que cada clase tenga una única responsabilidad.

 * COMUNICACION:
 * - Lo invoca: AppointmentService (en createAppointment y
 *   updateAppointmentStatus).
 * - Llama a (1 query por validacion):
 *     AppointmentRepository.existsOverlappingAppointment      (validateNoOverlap).
 *     AppointmentRepository.existsOverlappingBoothAppointment (validateNoBoothOverlap, v14).
 *     EmployeeScheduleRepository.findAllByMembershipIdAndDayOfWeek (validateEmployeeSchedule).
 *     ScheduleBlockRepository.findApplicableBlocks            (validateNoScheduleBlock, v15).
 * - No devuelve nada: cada metodo lanza ResponseStatusException si una
 *   regla falla, o no hace nada si todo esta bien (fail-fast).

 * [v16 membership] Los parametros llamados `membershipId` son en realidad el
 * id de la membership (pertenencia usuario-negocio). El nombre externo se
 * mantiene por compatibilidad con la API; internamente es membershipId.

 * 8 validaciones publicas (en orden de invocacion tipica):
 *   validateAppointmentInterval  400 si la hora no es multiplo del intervalo.
 *   validateBusinessHours        400 si el negocio esta cerrado ese dia o
 *                                la cita no cabe en su horario de apertura.
 *   validateEmployeeSchedule     400 si la cita cae fuera del horario del
 *                                empleado o cruza medianoche.
 *   validateNoOverlap            409 si solapa con otra cita activa del
 *                                empleado.
 *   validateNoEmployeeAbsence    409 si solapa con una ausencia (vacaciones,
 *                                cita medica) registrada del empleado.
 *   validateNoBoothOverlap       409 si solapa con otra cita en la misma
 *                                cabina (solo si la cita lleva cabina).
 *   validateNoScheduleBlock      409 si la fecha cae en un bloqueo de
 *                                agenda (global, por empleado o por cabina).
 *   validateStatusTransition     400 si la transicion de estado es ilegal
 *                                (ver mapa VALID_TRANSITIONS).
 */
@Component
@RequiredArgsConstructor
public class AppointmentValidator {

    private final AppointmentRepository appointmentRepository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final EmployeeAbsenceRepository absenceRepository;
    private final ScheduleBlockRepository scheduleBlockRepository;
    private final BusinessHourRepository businessHourRepository;

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
     * Comprobar que no hay otra cita que se solape con el mismo empleado en
     * el rango de tiempo dado.
     *
     * @param excludeAppointmentId  id de cita a excluir del check (P9): al
     *                              reagendar una cita existente, su propio
     *                              slot original no cuenta como "otra cita
     *                              solapada". null en createAppointment.
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

    /**
     * Comprueba que la membership (empleado) no tiene una ausencia registrada
     * (vacaciones, cita medica, etc.) que solape con el rango de la cita.

     * Cubre el hueco semantico entre `GET /availability` (que ya excluye
     * slots dentro de ausencias) y `POST /appointments` (que antes podia
     * crear citas encima de una ausencia si el cliente saltaba la consulta
     * previa).

     * Si encuentra al menos una ausencia solapada, devuelve 409 con el
     * `reason` del primero para que el frontend muestre el motivo
     * ("El empleado tiene una ausencia: Vacaciones").
     */
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

    /**
     * Validacion ortogonal a la del empleado: comprueba que la cabina no
     * esta ocupada por otra cita activa en el rango. Solo se invoca cuando
     * la cita lleva boothId (no aplica si el negocio no usa cabinas).
     *
     * @param excludeAppointmentId  id de cita a excluir del check (P9):
     *                              misma semantica que en validateNoOverlap.
     */
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

    /**
     * Comprueba que la fecha de la cita no caiga en un bloqueo de agenda
     * (schedule_block) aplicable.

     * Un bloqueo aplica a la cita si su rango [startDate, endDate] incluye
     * la fecha de la cita Y se da alguno de estos casos:
     *   - global (employee NULL y booth NULL): aplica a todo el negocio.
     *   - dirigido al empleado de la cita.
     *   - dirigido a la cabina de la cita (si la cita lleva cabina).

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

     * Si el negocio tiene appointment_interval = 30, las citas
     * deben empezar en minutos múltiplos de 30 (00, 30).
     * Si es 15, pueden empezar en 00, 15, 30 o 45.
     */
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

    /**
     * Comprueba que la cita cae dentro del horario de apertura del negocio
     * para ese dia de la semana.

     * Politica (misma que AvailabilityService al generar slots, para que
     * GET /availability y POST /appointments sean coherentes):
     *   - Si no existe fila business_hours para ese dia -> cerrado implicito.
     *   - Si solo hay tramos con is_closed=true -> cerrado.
     *   - Si hay tramos abiertos -> [startDateTime, endDateTime] debe caber
     *     integro dentro de ALGUNO de ellos (el negocio puede tener turno
     *     partido: 10-14 + 16-20; una cita 13-15 no encaja en ninguno, una
     *     cita 11-13 si encaja en el primero).

     * En cualquier caso de violacion se devuelve 400 con un mensaje generico
     * "El negocio está cerrado en ese día y horario" (no se revelan detalles
     * del horario configurado).
     */
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