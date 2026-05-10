package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.Appointment;
import com.optima.api.modules.appointment.repository.BookedServiceRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AppointmentResponse - DTO de salida de una cita.
 *
 * Aplana la entidad Appointment + carga los BookedService asociados.
 * Para cada relacion @ManyToOne (client, employee, status) expone id+name
 * en lugar del objeto entero, asi el frontend evita ir a otros endpoints.
 *
 * COMUNICACION:
 * - Lo construye AppointmentResponse.from(Appointment, BookedServiceRepository)
 *   en AppointmentService (todas las rutas de retorno).
 * - Lo serializa Jackson a JSON en las respuestas de AppointmentController.
 *
 * SMELL CONSCIENTE: from() recibe un Repository como segundo argumento.
 * Es un compromiso para que la response siempre traiga la lista de
 * bookedServices con applied_price y applied_tax_percentage congelados,
 * sin obligar al service a anadir el bloque de carga en cada metodo.
 * Defendible para un TFG; en una API de produccion lo refactorizariamos
 * para que el service haga la carga y pase la lista ya construida.
 */
public record AppointmentResponse(
        Long id,
        Long businessId,

        // Del cliente mostramos ID y nombre
        Long clientId,
        String clientName,

        // Del empleado mostramos ID y nombre
        Long employeeId,
        String employeeName,

        // Del estado mostramos ID y nombre
        Long statusId,
        String statusName,

        Boolean isPaid,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String notes,
        LocalDateTime createdAt,
        List<BookedServiceResponse> bookedServices
) {
    public static AppointmentResponse from(Appointment a,
                                           BookedServiceRepository bookedServiceRepository) {
        List<BookedServiceResponse> bookedServices =
                bookedServiceRepository
                        .findAllByAppointmentId(a.getId())
                        .stream()
                        .map(BookedServiceResponse::from)
                        .toList();
        return new AppointmentResponse(
                a.getId(),
                a.getBusiness().getId(),
                a.getClient().getId(),
                a.getClient().getFullName(),
                a.getEmployee().getId(),
                a.getEmployee().getFullName(),
                a.getStatus().getId(),
                a.getStatus().getName(),
                a.getIsPaid(),
                a.getStartDateTime(),
                a.getEndDateTime(),
                a.getNotes(),
                a.getCreatedAt(),
                bookedServices
        );
    }
}
