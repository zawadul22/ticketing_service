package com.example.ticketing_service.exception;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException(String email) {
		super("Email is already registered: " + email);
	}

}
