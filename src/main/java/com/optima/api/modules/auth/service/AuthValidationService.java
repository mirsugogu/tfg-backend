package com.optima.api.modules.auth.service;

import com.optima.api.modules.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthValidationService {

    private final UserRepository userRepository;

    public AuthValidationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isEmailAvailable(String email) {
        // Saneamiento: Convertir a minúsculas antes de buscar
        String normalizedEmail = email.trim().toLowerCase();
        return userRepository.existsByEmail(normalizedEmail);
    }
}