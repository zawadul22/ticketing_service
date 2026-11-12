package com.example.ticketing_service_backend.service;

import java.time.Instant;
import java.util.List;

import com.example.ticketing_service_backend.dto.BookingRequest;
import com.example.ticketing_service_backend.dto.BookingResponse;
import com.example.ticketing_service_backend.entity.Booking;
import com.example.ticketing_service_backend.entity.BookingStatus;
import com.example.ticketing_service_backend.exception.ApiException;
import com.example.ticketing_service_backend.repository.BookingRepository;
import com.example.ticketing_service_backend.repository.EventRepository;
import com.example.ticketing_service_backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class BookingService {
    private final BookingRepository bookings;
    private final EventRepository events;
    private final UserRepository users;

    public BookingService(BookingRepository bookings, EventRepository events, UserRepository users) {
        this.bookings = bookings;
        this.events = events;
        this.users = users;
    }

    @Transactional
    public BookingResponse create(long userId, long eventId, @Valid BookingRequest request) {
        // All seat-changing operations acquire the event lock first, keeping lock order consistent.
        var event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Event not found"));
        if (!event.getStartTime().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Bookings are closed because the event has started");
        }
        if (events.reserveSeats(eventId, request.quantity(), Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Seats are insufficient or the event has started");
        }
        // The bulk update clears managed entities; reload to return current availability and price.
        event = events.findById(eventId).orElseThrow();
        var user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        var booking = bookings.saveAndFlush(new Booking(user, event, request.quantity()));
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForUser(long userId) {
        return bookings.findByUserIdOrderByCreatedAtDesc(userId).stream().map(BookingResponse::from).toList();
    }

    @Transactional
    public BookingResponse cancel(long userId, long bookingId) {
        // Fetch just the event ID, avoiding a stale booking in the persistence context before locking.
        var eventId = bookings.findEventIdById(bookingId).orElseThrow(this::notFound);
        var event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Event not found"));
        var booking = bookings.findByIdForUpdate(bookingId).orElseThrow(this::notFound);
        if (!booking.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the booking owner can cancel it");
        }
        if (!event.getStartTime().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Bookings cannot be cancelled after the event starts");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking is already cancelled");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        event.setAvailableSeats(event.getAvailableSeats() + booking.getQuantity());
        bookings.flush();
        return BookingResponse.from(booking);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Booking not found");
    }
}
