package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.Appointment;
import com.optima.api.modules.appointment.repository.BookedServiceRepository;

import java.time.LocalDateTime;
import java.util.List;

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
