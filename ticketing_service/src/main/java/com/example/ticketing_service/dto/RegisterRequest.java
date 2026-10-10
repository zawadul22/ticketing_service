package com.example.ticketing_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 255) String email,
		// BCrypt only uses the first 72 bytes of a password
		@NotBlank @Size(min = 8, max = 72) String password) {

	// Trim before validation runs, so " bob@example.com " passes @Email
	public RegisterRequest {
		email = email == null ? null : email.trim();
	}

}
