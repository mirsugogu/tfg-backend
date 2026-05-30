package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.Appointment;
import com.optima.api.modules.appointment.model.BookedService;

import java.time.LocalDateTime;
import java.util.List;

/** datos de salida de una cita */
public record AppointmentResponse(
        Long id,
        Long businessId,
        Long clientId,
        String clientName,
        Long membershipId,
        String userFullName,
        Long boothId,
        String boothName,
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
    /** pasa la entidad a datos de salida */
    public static AppointmentResponse from(Appointment a,
                                           List<BookedService> bookedServices) {
        List<BookedServiceResponse> mapped = bookedServices.stream()
                .map(BookedServiceResponse::from)
                .toList();
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
