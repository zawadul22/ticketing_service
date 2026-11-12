package com.example.ticketing_service_backend.dto;

import java.math.BigInteger;
import java.time.Instant;

import com.example.ticketing_service_backend.entity.Booking;
import com.example.ticketing_service_backend.entity.BookingStatus;

public record BookingResponse(Long id, EventResponse event, Integer quantity, Long unitPriceCents,
                              BigInteger totalPriceCents, BookingStatus status, Instant createdAt) {
    public static BookingResponse from(Booking booking) {
        // BigInteger preserves exact totals even when a valid long unit price is multiplied by ten.
        var total = BigInteger.valueOf(booking.getUnitPriceCents()).multiply(BigInteger.valueOf(booking.getQuantity()));
        return new BookingResponse(booking.getId(), EventResponse.from(booking.getEvent()), booking.getQuantity(),
                booking.getUnitPriceCents(), total, booking.getStatus(), booking.getCreatedAt());
    }
}
