package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.BusinessHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessHourRepository extends JpaRepository<BusinessHour, Long> {

    /**
     * Lista los siete tramos horarios de un negocio, ordenados de lunes a domingo.
     */
    List<BusinessHour> findAllByBusinessIdOrderByDayOfWeekAsc(Long businessId);

    /**
     * Búsqueda tenant-safe: el horario existe Y pertenece al negocio dado.
     */
    Optional<BusinessHour> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Devuelve el tramo de un día concreto del negocio (debería ser único).
     */
    Optional<BusinessHour> findByBusinessIdAndDayOfWeek(Long businessId, Integer dayOfWeek);

    /**
     * Comprueba si ya existe un tramo para ese día en el negocio.
     * Sirve para validar que no se duplique.
     */
    boolean existsByBusinessIdAndDayOfWeek(Long businessId, Integer dayOfWeek);
}
