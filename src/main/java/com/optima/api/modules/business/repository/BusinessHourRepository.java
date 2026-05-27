package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.BusinessHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Acceso a la tabla `business_hours`. */
@Repository
public interface BusinessHourRepository extends JpaRepository<BusinessHour, Long> {

    /**
     * Lista todos los tramos horarios de un negocio, ordenados por dia y por
     * hora de inicio (asi un dia con turno partido sale 10-14 antes que 16-20).
     */
    List<BusinessHour> findAllByBusinessIdOrderByDayOfWeekAscStartTimeAsc(Long businessId);

    /** Busca un tramo horario dentro del negocio. */
    Optional<BusinessHour> findByIdAndBusinessId(Long id, Long businessId);

    /** Devuelve los tramos de un dia ordenados por hora de inicio. */
    List<BusinessHour> findAllByBusinessIdAndDayOfWeekOrderByStartTimeAsc(
            Long businessId, Integer dayOfWeek);
}
