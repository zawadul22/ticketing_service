package com.example.ticketing_service.dto;

import jakarta.validation.constraints.NotBlank;

// No @Email or length checks: login only answers "valid credentials or not"
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
