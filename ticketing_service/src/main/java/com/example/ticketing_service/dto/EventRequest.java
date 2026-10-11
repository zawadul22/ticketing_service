package com.example.ticketing_service.dto;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Numbers are Integer, not int, so a missing field fails @NotNull instead of silently becoming 0
public record EventRequest(
		@NotBlank @Size(max = 200) String name,
		@Size(max = 5000) String description,
		@NotBlank @Size(max = 200) String venue,
		@NotNull @Future Instant startsAt,
		@NotNull @Min(1) Integer capacity,
		@NotNull @Min(0) Integer priceCents) {
}
