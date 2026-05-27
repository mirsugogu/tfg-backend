package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.Appointment;
import com.optima.api.modules.appointment.model.BookedService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de salida con los datos principales de una cita.
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
        LocalDateTime updatedAt,
        List<BookedServiceResponse> bookedServices
) {
    public static AppointmentResponse from(Appointment a,
                                           List<BookedService> bookedServices) {
        List<BookedServiceResponse> mapped = bookedServices.stream()
                .map(BookedServiceResponse::from)
                .toList();
        // Se devuelve la membership del empleado y su nombre visible.
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
                a.getUpdatedAt(),
                mapped
        );
    }
}
