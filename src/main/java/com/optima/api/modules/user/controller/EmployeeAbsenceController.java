package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.dto.request.CreateAbsenceRequest;
import com.optima.api.modules.user.dto.request.UpdateAbsenceRequest;
import com.optima.api.modules.user.dto.response.AbsenceResponse;
import com.optima.api.modules.user.service.EmployeeAbsenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/users/{userId}/absences")
@RequiredArgsConstructor
public class EmployeeAbsenceController {

    private final EmployeeAbsenceService absenceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AbsenceResponse create(@PathVariable Long businessId,
                                  @PathVariable Long userId,
                                  @Valid @RequestBody CreateAbsenceRequest req) {
        return absenceService.create(businessId, userId, req);
    }

    @GetMapping
    public List<AbsenceResponse> listByEmployee(@PathVariable Long businessId,
                                                @PathVariable Long userId) {
        return absenceService.listByEmployee(businessId, userId);
    }

    @GetMapping("/{id}")
    public AbsenceResponse getById(@PathVariable Long businessId,
                                   @PathVariable Long userId,
                                   @PathVariable Long id) {
        return absenceService.getById(businessId, userId, id);
    }

    @PutMapping("/{id}")
    public AbsenceResponse update(@PathVariable Long businessId,
                                  @PathVariable Long userId,
                                  @PathVariable Long id,
                                  @Valid @RequestBody UpdateAbsenceRequest req) {
        return absenceService.update(businessId, userId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long businessId,
                       @PathVariable Long userId,
                       @PathVariable Long id) {
        absenceService.delete(businessId, userId, id);
    }
}
