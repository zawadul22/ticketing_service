package com.example.ticketing_service_backend.dto;

import java.time.Instant;

import com.example.ticketing_service_backend.entity.Event;

public record EventResponse(Long id, String name, String description, String venue, Instant startTime,
                            Integer capacity, Integer availableSeats, Long ticketPriceCents) {
    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getDescription(), event.getVenue(),
                event.getStartTime(), event.getCapacity(), event.getAvailableSeats(), event.getTicketPriceCents());
    }
}
