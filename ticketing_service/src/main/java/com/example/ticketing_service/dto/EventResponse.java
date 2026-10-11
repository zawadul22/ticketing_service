package com.example.ticketing_service.dto;

import java.time.Instant;

import com.example.ticketing_service.entity.Event;

public record EventResponse(Long id, String name, String description, String venue, Instant startsAt, int capacity,
		int availableSeats, int priceCents, Instant createdAt) {

	public static EventResponse from(Event event) {
		return new EventResponse(event.getId(), event.getName(), event.getDescription(), event.getVenue(),
				event.getStartsAt(), event.getCapacity(), event.getAvailableSeats(), event.getPriceCents(),
				event.getCreatedAt());
	}

}
