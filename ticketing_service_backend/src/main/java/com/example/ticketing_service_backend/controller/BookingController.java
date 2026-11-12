package com.example.ticketing_service_backend.controller;

import java.util.List;

import com.example.ticketing_service_backend.dto.BookingRequest;
import com.example.ticketing_service_backend.dto.BookingResponse;
import com.example.ticketing_service_backend.service.BookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/api/events/{eventId}/bookings")
    public ResponseEntity<BookingResponse> create(@PathVariable long eventId, @Valid @RequestBody BookingRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookings.create(Long.parseLong(authentication.getName()), eventId, request));
    }

    @GetMapping("/api/bookings/me")
    public List<BookingResponse> mine(Authentication authentication) {
        return bookings.listForUser(Long.parseLong(authentication.getName()));
    }

    @PostMapping("/api/bookings/{id}/cancel")
    public BookingResponse cancel(@PathVariable long id, Authentication authentication) {
        return bookings.cancel(Long.parseLong(authentication.getName()), id);
    }
}
