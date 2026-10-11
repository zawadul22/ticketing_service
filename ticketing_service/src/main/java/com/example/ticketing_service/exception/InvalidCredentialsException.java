package com.example.ticketing_service.exception;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		// Same message for unknown email and wrong password, so it doesn't reveal which emails are registered
		super("Invalid email or password");
	}

}
