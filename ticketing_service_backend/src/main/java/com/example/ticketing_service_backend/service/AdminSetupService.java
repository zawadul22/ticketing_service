package com.example.ticketing_service_backend.service;

import com.example.ticketing_service_backend.configuration.AdminProperties;
import com.example.ticketing_service_backend.dto.RegisterRequest;
import com.example.ticketing_service_backend.entity.Role;
import com.example.ticketing_service_backend.entity.User;
import com.example.ticketing_service_backend.repository.UserRepository;
import jakarta.validation.Validator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSetupService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final Validator validator;

    public AdminSetupService(UserRepository users, PasswordEncoder passwords, Validator validator) {
        this.users = users;
        this.passwords = passwords;
        this.validator = validator;
    }

    @Transactional
    public void initialize(AdminProperties properties) {
        if (!properties.enabled()) {
            return;
        }
        var request = new RegisterRequest(properties.email(), properties.password());
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalStateException("Admin setup requires a valid email and a password of 8–72 characters");
        }
        AuthService.requireValidPasswordBytes(request.password());
        var existing = users.findByEmail(request.email());
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                throw new IllegalStateException("Admin email belongs to a regular user; refusing to promote it");
            }
            return;
        }
        var admin = new User(request.email(), passwords.encode(request.password()));
        admin.setRole(Role.ADMIN);
        users.saveAndFlush(admin);
    }
}
