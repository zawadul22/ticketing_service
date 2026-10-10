package com.example.ticketing_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.ticketing_service.dto.RegisterRequest;
import com.example.ticketing_service.dto.UserResponse;
import com.example.ticketing_service.entity.Role;
import com.example.ticketing_service.exception.DuplicateEmailException;
import com.example.ticketing_service.service.UserService;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UserService userService;

	@Test
	void registerAcceptsPaddedEmailAndReturnsCreatedUser() {
		given(userService.register(any(RegisterRequest.class)))
				.willReturn(new UserResponse(1L, "bob@example.com", Role.USER, Instant.parse("2026-10-04T10:00:00Z")));

		assertThat(register("{\"email\":\"  bob@example.com \",\"password\":\"secret123\"}"))
				.hasStatus(HttpStatus.CREATED)
				.bodyJson()
				.isLenientlyEqualTo("{\"id\":1,\"email\":\"bob@example.com\",\"role\":\"USER\"}");
	}

	@Test
	void registerRejectsInvalidInputWithFieldErrors() {
		assertThat(register("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.hasPathSatisfying("$.errors.email", value -> assertThat(value).isNotNull())
				.hasPathSatisfying("$.errors.password", value -> assertThat(value).isNotNull());
	}

	@Test
	void registerReturnsConflictForDuplicateEmail() {
		given(userService.register(any(RegisterRequest.class))).willThrow(new DuplicateEmailException("bob@example.com"));

		assertThat(register("{\"email\":\"bob@example.com\",\"password\":\"secret123\"}"))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Email is already registered: bob@example.com");
	}

	private MockMvcTester.MockMvcRequestBuilder register(String json) {
		return mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json);
	}

}
