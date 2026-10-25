package com.example.ticketing_service.exception;

public class NotEnoughSeatsException extends ConflictException {

	public NotEnoughSeatsException() {
		super("Not enough seats available");
	}

}
