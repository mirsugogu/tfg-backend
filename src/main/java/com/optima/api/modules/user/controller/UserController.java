package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.dto.request.CreateUserRequest;
import com.optima.api.modules.user.dto.request.UpdateUserRequest;
import com.optima.api.modules.user.dto.response.UserResponse;
import com.optima.api.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@PathVariable Long businessId,
                               @Valid @RequestBody CreateUserRequest req) {
        return userService.create(businessId, req);
    }

    @GetMapping
    public List<UserResponse> listByBusiness(@PathVariable Long businessId) {
        return userService.listByBusiness(businessId);
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long businessId, @PathVariable Long id) {
        return userService.getById(businessId, id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long businessId,
                               @PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest req) {
        return userService.update(businessId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long businessId, @PathVariable Long id) {
        userService.deactivate(businessId, id);
    }
}
