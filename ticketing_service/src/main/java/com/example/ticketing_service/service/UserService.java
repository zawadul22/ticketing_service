package com.example.ticketing_service.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing_service.dto.RegisterRequest;
import com.example.ticketing_service.dto.UserResponse;
import com.example.ticketing_service.entity.Role;
import com.example.ticketing_service.entity.User;
import com.example.ticketing_service.exception.DuplicateEmailException;
import com.example.ticketing_service.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public UserResponse register(RegisterRequest request) {
		return UserResponse.from(createUser(request.email(), request.password(), Role.USER));
	}

	/** Returns true if the admin was created, false if a user with that email already exists. */
	@Transactional
	public boolean createAdminIfMissing(String email, String password) {
		if (userRepository.existsByEmail(normalizeEmail(email))) {
			return false;
		}
		createUser(email, password, Role.ADMIN);
		return true;
	}

	private User createUser(String email, String rawPassword, Role role) {
		String normalizedEmail = normalizeEmail(email);
		if (userRepository.existsByEmail(normalizedEmail)) {
			throw new DuplicateEmailException(normalizedEmail);
		}
		User user = new User();
		user.setEmail(normalizedEmail);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setRole(role);
		return userRepository.save(user);
	}

	// Emails are stored trimmed and lowercased, so "Bob@X.com" and "bob@x.com" are the same account
	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
