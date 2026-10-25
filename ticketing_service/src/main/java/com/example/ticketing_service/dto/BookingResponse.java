package com.example.ticketing_service.dto;

import java.time.Instant;

import com.example.ticketing_service.entity.Booking;
import com.example.ticketing_service.entity.BookingStatus;
import com.example.ticketing_service.entity.Event;

public record BookingResponse(Long id, int quantity, BookingStatus status, Instant createdAt, Long eventId,
		String eventName, String eventVenue, Instant eventStartsAt) {

	// Reads the lazy event, so call it inside a transaction or with the event fetched
	public static BookingResponse from(Booking booking) {
		Event event = booking.getEvent();
		return new BookingResponse(booking.getId(), booking.getQuantity(), booking.getStatus(), booking.getCreatedAt(),
				event.getId(), event.getName(), event.getVenue(), event.getStartsAt());
	}

}
