package com.example.ticketing_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.ticketing_service.config.SecurityConfig;
import com.example.ticketing_service.dto.AuthResponse;
import com.example.ticketing_service.dto.LoginRequest;
import com.example.ticketing_service.dto.RegisterRequest;
import com.example.ticketing_service.dto.UserResponse;
import com.example.ticketing_service.entity.Role;
import com.example.ticketing_service.exception.DuplicateEmailException;
import com.example.ticketing_service.exception.InvalidCredentialsException;
import com.example.ticketing_service.service.AuthService;
import com.example.ticketing_service.service.UserService;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

	private static final UserResponse BOB = new UserResponse(1L, "bob@example.com", Role.USER,
			Instant.parse("2026-10-04T10:00:00Z"));

	private static final AuthResponse BOB_AUTH = new AuthResponse("token-value", Instant.parse("2026-10-04T12:00:00Z"),
			BOB);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private UserService userService;

	@Test
	void registerAcceptsPaddedEmailAndReturnsToken() {
		given(authService.register(any(RegisterRequest.class))).willReturn(BOB_AUTH);

		assertThat(post("/api/auth/register", "{\"email\":\"  bob@example.com \",\"password\":\"secret123\"}"))
				.hasStatus(HttpStatus.CREATED)
				.bodyJson()
				.isLenientlyEqualTo("{\"token\":\"token-value\",\"user\":{\"id\":1,\"email\":\"bob@example.com\",\"role\":\"USER\"}}");
	}

	@Test
	void registerRejectsInvalidInputWithFieldErrors() {
		assertThat(post("/api/auth/register", "{\"email\":\"not-an-email\",\"password\":\"short\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.hasPathSatisfying("$.errors.email", value -> assertThat(value).isNotNull())
				.hasPathSatisfying("$.errors.password", value -> assertThat(value).isNotNull());
	}

	@Test
	void registerReturnsConflictForDuplicateEmail() {
		given(authService.register(any(RegisterRequest.class))).willThrow(new DuplicateEmailException("bob@example.com"));

		assertThat(post("/api/auth/register", "{\"email\":\"bob@example.com\",\"password\":\"secret123\"}"))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Email is already registered: bob@example.com");
	}

	@Test
	void loginReturnsToken() {
		given(authService.login(any(LoginRequest.class))).willReturn(BOB_AUTH);

		assertThat(post("/api/auth/login", "{\"email\":\"bob@example.com\",\"password\":\"secret123\"}"))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.token")
				.isEqualTo("token-value");
	}

	@Test
	void loginReturnsUnauthorizedForBadCredentials() {
		given(authService.login(any(LoginRequest.class))).willThrow(new InvalidCredentialsException());

		assertThat(post("/api/auth/login", "{\"email\":\"bob@example.com\",\"password\":\"wrong-pass\"}"))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Invalid email or password");
	}

	@Test
	void meReturnsUserFromTokenSubject() {
		given(userService.getById(1L)).willReturn(BOB);

		assertThat(mvc.get().uri("/api/auth/me").with(jwt().jwt(token -> token.subject("1"))))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.email")
				.isEqualTo("bob@example.com");
	}

	@Test
	void meRequiresAuthentication() {
		assertThat(mvc.get().uri("/api/auth/me"))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
				.bodyJson()
				.isLenientlyEqualTo("{\"status\":401,\"title\":\"Unauthorized\",\"instance\":\"/api/auth/me\"}");
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
	}

}
