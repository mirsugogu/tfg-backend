package com.optima.api.modules.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
public class LoginRequest {
    private String email;
    private String password;
}