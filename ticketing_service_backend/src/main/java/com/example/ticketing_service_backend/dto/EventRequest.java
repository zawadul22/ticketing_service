package com.example.ticketing_service_backend.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EventRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotBlank @Size(max = 255) String venue,
        @NotNull Instant startTime,
        @NotNull @Positive Integer capacity,
        @NotNull @PositiveOrZero Long ticketPriceCents) {
}
