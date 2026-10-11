package com.example.ticketing_service.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing_service.dto.AuthResponse;
import com.example.ticketing_service.dto.LoginRequest;
import com.example.ticketing_service.dto.RegisterRequest;
import com.example.ticketing_service.dto.UserResponse;
import com.example.ticketing_service.entity.User;
import com.example.ticketing_service.exception.InvalidCredentialsException;
import com.example.ticketing_service.repository.UserRepository;

@Service
public class AuthService {

	private final UserService userService;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final String dummyPasswordHash;

	public AuthService(UserService userService, UserRepository userRepository, PasswordEncoder passwordEncoder,
			JwtService jwtService) {
		this.userService = userService;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-unknown-emails");
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		return toAuthResponse(userService.register(request));
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		Optional<User> user = userRepository.findByEmail(UserService.normalizeEmail(request.email()));
		// Run a BCrypt check even for unknown emails, so the response time doesn't reveal which emails exist
		String passwordHash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
		boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
		if (user.isEmpty() || !passwordMatches) {
			throw new InvalidCredentialsException();
		}
		return toAuthResponse(UserResponse.from(user.get()));
	}

	private AuthResponse toAuthResponse(UserResponse user) {
		Jwt token = jwtService.issueToken(user);
		return new AuthResponse(token.getTokenValue(), token.getExpiresAt(), user);
	}

}
