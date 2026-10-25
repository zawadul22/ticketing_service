package com.example.ticketing_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketing_service.dto.BookingRequest;
import com.example.ticketing_service.dto.BookingResponse;
import com.example.ticketing_service.service.BookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// The user always comes from the token, never from the request, so nobody can book or cancel as someone else
@RestController
@RequiredArgsConstructor
public class BookingController {

	private final BookingService bookingService;

	@PostMapping("/api/events/{eventId}/bookings")
	@ResponseStatus(HttpStatus.CREATED)
	public BookingResponse book(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId,
			@Valid @RequestBody BookingRequest request) {
		return bookingService.book(userId(jwt), eventId, request.quantity());
	}

	@GetMapping("/api/bookings/me")
	public List<BookingResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
		return bookingService.listMine(userId(jwt));
	}

	@PostMapping("/api/bookings/{id}/cancel")
	public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return bookingService.cancel(userId(jwt), id);
	}

	private static Long userId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

}
