package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.EmployeeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Long> {
    List<EmployeeSchedule> findAllByUserIdAndDayOfWeek(Long userId, Integer dayOfWeek);
}
