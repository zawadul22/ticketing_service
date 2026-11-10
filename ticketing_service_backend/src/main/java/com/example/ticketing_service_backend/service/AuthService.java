package com.example.ticketing_service_backend.service;

import java.nio.charset.StandardCharsets;

import com.example.ticketing_service_backend.dto.AuthResponse;
import com.example.ticketing_service_backend.dto.LoginRequest;
import com.example.ticketing_service_backend.dto.RegisterRequest;
import com.example.ticketing_service_backend.dto.UserResponse;
import com.example.ticketing_service_backend.entity.User;
import com.example.ticketing_service_backend.exception.ApiException;
import com.example.ticketing_service_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyPasswordHash = passwords.encode("not-a-real-user-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        requireValidPasswordBytes(request.password());
        if (users.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        // The database unique constraint also guards simultaneous registrations.
        var user = users.saveAndFlush(new User(request.email(), passwords.encode(request.password())));
        return tokens.issue(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        var user = users.findByEmail(request.email());
        boolean matches = passwords.matches(request.password(), user.map(User::getPasswordHash).orElse(dummyPasswordHash));
        if (user.isEmpty() || !matches) {
            throw invalidCredentials();
        }
        return tokens.issue(user.get());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(long userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    static void requireValidPasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
