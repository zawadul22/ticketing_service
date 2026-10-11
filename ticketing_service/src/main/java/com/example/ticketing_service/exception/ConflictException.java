package com.example.ticketing_service.exception;

// The request breaks a business rule against the current state; mapped to 409 Conflict
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}
