package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/users")

public class UserController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/")
    public User CreateUser(@RequestBody User user) {
        return userRepository.save(user);
    }
    @GetMapping("/business/{businessId}")
    public List<User> getUsersByBusiness(@PathVariable Long businessId) {
        return userRepository.findByBusinessId(businessId);
    }
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(user)) // Si existe, devuelve 200 OK + Usuario
                .orElse(ResponseEntity.notFound().build()); // Si no existe, devuelve 404 Not Found
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        return userRepository.findById(id).map(user -> {
            // Marcamos como inactivo para bloquear el acceso
            user.setIsActive(false);

            // Registramos el momento exacto de la desactivación
            user.setDeactivatedAt(java.time.LocalDateTime.now());

            userRepository.save(user);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
