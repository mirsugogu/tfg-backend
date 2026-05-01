package com.optima.api.modules.appointment.dto.response;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long idAppointment,
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
        LocalDateTime createdAt
) {}