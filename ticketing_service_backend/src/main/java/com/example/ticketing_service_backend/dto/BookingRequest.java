package com.example.ticketing_service_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BookingRequest(@NotNull @Min(1) @Max(10) Integer quantity) {
}
