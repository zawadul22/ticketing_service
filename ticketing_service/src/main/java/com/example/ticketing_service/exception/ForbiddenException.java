package com.example.ticketing_service.exception;

// The caller is logged in but isn't allowed to act on this resource; mapped to 403 Forbidden
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}

}
