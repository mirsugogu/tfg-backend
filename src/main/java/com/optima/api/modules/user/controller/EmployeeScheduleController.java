package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.dto.request.CreateScheduleRequest;
import com.optima.api.modules.user.dto.request.UpdateScheduleRequest;
import com.optima.api.modules.user.dto.response.ScheduleResponse;
import com.optima.api.modules.user.service.EmployeeScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/users/{userId}/schedules")
@RequiredArgsConstructor
public class EmployeeScheduleController {

    private final EmployeeScheduleService scheduleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(@PathVariable Long businessId,
                                   @PathVariable Long userId,
                                   @Valid @RequestBody CreateScheduleRequest req) {
        return scheduleService.create(businessId, userId, req);
    }

    @GetMapping
    public List<ScheduleResponse> listByEmployee(@PathVariable Long businessId,
                                                 @PathVariable Long userId) {
        return scheduleService.listByEmployee(businessId, userId);
    }

    @GetMapping("/{id}")
    public ScheduleResponse getById(@PathVariable Long businessId,
                                    @PathVariable Long userId,
                                    @PathVariable Long id) {
        return scheduleService.getById(businessId, userId, id);
    }

    @PutMapping("/{id}")
    public ScheduleResponse update(@PathVariable Long businessId,
                                   @PathVariable Long userId,
                                   @PathVariable Long id,
                                   @Valid @RequestBody UpdateScheduleRequest req) {
        return scheduleService.update(businessId, userId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long businessId,
                       @PathVariable Long userId,
                       @PathVariable Long id) {
        scheduleService.delete(businessId, userId, id);
    }
}
