package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.Appointment;
import com.optima.api.modules.appointment.model.BookedService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AppointmentResponse - DTO de salida de una cita.
 *
 * Aplana la entidad Appointment + recibe la lista de BookedService ya
 * cargada por el service. Para cada relacion @ManyToOne (client, employee,
 * status) expone id+name en lugar del objeto entero, asi el frontend evita
 * ir a otros endpoints.
 *
 * COMUNICACION:
 * - Lo construye AppointmentResponse.from(Appointment, List<BookedService>)
 *   en AppointmentService. El service es quien decide como cargar los
 *   BookedService (por id puntual o en batch para listados).
 * - Lo serializa Jackson a JSON en las respuestas de AppointmentController.
 *
 * Diseno: el DTO no conoce repositorios. Convertir entidad -> record es una
 * transformacion pura; la carga de los BookedService asociados es
 * responsabilidad del service, que ademas puede agruparlos en una sola
 * query cuando se listan varias citas (evita el N+1).
 */
public record AppointmentResponse(
        Long id,
        Long businessId,

        // Del cliente mostramos ID y nombre
        Long clientId,
        String clientName,

        // Del empleado mostramos ID y nombre
        Long membershipId,
        String userFullName,

        // De la cabina (opcional) mostramos ID y nombre; null si la cita no usa cabina
        Long boothId,
        String boothName,

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
                                           List<BookedService> bookedServices) {
        List<BookedServiceResponse> mapped = bookedServices.stream()
                .map(BookedServiceResponse::from)
                .toList();
        // [v16 membership] el "empleado" de la cita es ahora una Membership.
        // membershipId expuesto = id de la membership; userFullName = fullName
        // del User al que esa membership apunta. Mantenemos los nombres
        // externos para no romper el contrato del API.
        return new AppointmentResponse(
                a.getId(),
                a.getBusiness().getId(),
                a.getClient().getId(),
                a.getClient().getFullName(),
                a.getMembership().getId(),
                a.getMembership().getUser().getFullName(),
                a.getBooth() != null ? a.getBooth().getId() : null,
                a.getBooth() != null ? a.getBooth().getName() : null,
                a.getStatus().getId(),
                a.getStatus().getName(),
                a.getIsPaid(),
                a.getStartDateTime(),
                a.getEndDateTime(),
                a.getNotes(),
                a.getCreatedAt(),
                mapped
        );
    }
}
