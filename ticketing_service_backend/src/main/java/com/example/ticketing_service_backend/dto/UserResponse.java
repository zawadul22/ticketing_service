package com.example.ticketing_service_backend.dto;

import java.time.Instant;

import com.example.ticketing_service_backend.entity.Role;
import com.example.ticketing_service_backend.entity.User;

public record UserResponse(Long id, String email, Role role, Instant createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
