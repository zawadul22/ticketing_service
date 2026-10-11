package com.example.ticketing_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing_service.TestcontainersConfiguration;
import com.example.ticketing_service.config.JwtProperties;
import com.example.ticketing_service.dto.AuthResponse;
import com.example.ticketing_service.dto.LoginRequest;
import com.example.ticketing_service.dto.RegisterRequest;
import com.example.ticketing_service.exception.InvalidCredentialsException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuthServiceTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Test
	void registerReturnsTokenForNewUser() {
		AuthResponse response = authService.register(new RegisterRequest("erin@example.com", "secret123"));

		Jwt token = jwtDecoder.decode(response.token());
		assertThat(token.getSubject()).isEqualTo(response.user().id().toString());
		assertThat(token.getClaimAsString("role")).isEqualTo("USER");
		assertThat(token.getClaimAsString("email")).isEqualTo("erin@example.com");
		assertThat(token.getClaimAsString("iss")).isEqualTo(JwtProperties.ISSUER);
		assertThat(response.expiresAt()).isEqualTo(token.getExpiresAt());
	}

	@Test
	void loginIgnoresEmailCaseAndReturnsToken() {
		authService.register(new RegisterRequest("frank@example.com", "secret123"));

		AuthResponse response = authService.login(new LoginRequest(" Frank@Example.com ", "secret123"));

		assertThat(response.user().email()).isEqualTo("frank@example.com");
		assertThat(jwtDecoder.decode(response.token()).getSubject()).isEqualTo(response.user().id().toString());
	}

	@Test
	void loginRejectsWrongPassword() {
		authService.register(new RegisterRequest("grace@example.com", "secret123"));

		assertThatThrownBy(() -> authService.login(new LoginRequest("grace@example.com", "wrong-pass")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void loginRejectsUnknownEmailWithSameError() {
		assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "secret123")))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("Invalid email or password");
	}

}
