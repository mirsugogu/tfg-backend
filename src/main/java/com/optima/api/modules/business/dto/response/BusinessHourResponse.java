package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.BusinessHour;

import java.time.LocalTime;

/** Respuesta con un tramo horario semanal del negocio. */
public record BusinessHourResponse(
        Long id,
        Long businessId,
        Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Boolean isClosed
) {
    public static BusinessHourResponse from(BusinessHour bh) {
        return new BusinessHourResponse(
                bh.getId(),
                bh.getBusiness().getId(),
                bh.getDayOfWeek(),
                bh.getStartTime(),
                bh.getEndTime(),
                bh.getIsClosed()
        );
    }
}
