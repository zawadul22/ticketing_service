package com.example.ticketing_service_backend.service;

import java.time.Instant;
import java.util.List;

import com.example.ticketing_service_backend.dto.EventRequest;
import com.example.ticketing_service_backend.dto.EventResponse;
import com.example.ticketing_service_backend.entity.Event;
import com.example.ticketing_service_backend.exception.ApiException;
import com.example.ticketing_service_backend.repository.BookingRepository;
import com.example.ticketing_service_backend.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
    private final EventRepository events;
    private final BookingRepository bookings;

    public EventService(EventRepository events, BookingRepository bookings) {
        this.events = events;
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> list(boolean includePast) {
        var result = includePast ? events.findAllByOrderByStartTimeAsc()
                : events.findByStartTimeAfterOrderByStartTimeAsc(Instant.now());
        return result.stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EventResponse get(long id) {
        return EventResponse.from(events.findById(id).orElseThrow(this::notFound));
    }

    @Transactional
    public EventResponse create(EventRequest request) {
        requireFutureStart(request.startTime());
        var event = new Event(request.name(), request.description(), request.venue(), request.startTime(),
                request.capacity(), request.ticketPriceCents());
        return EventResponse.from(events.saveAndFlush(event));
    }

    @Transactional
    public EventResponse update(long id, EventRequest request) {
        // Conditional seat reservations also lock this row. Read current seat counts after acquiring it.
        var event = events.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (!request.startTime().equals(event.getStartTime())) {
            requireFutureStart(request.startTime());
        }
        int reservedSeats = event.getCapacity() - event.getAvailableSeats();
        if (request.capacity() < reservedSeats) {
            throw new ApiException(HttpStatus.CONFLICT, "Capacity cannot be less than the number of reserved tickets");
        }
        event.setName(request.name());
        event.setDescription(request.description());
        event.setVenue(request.venue());
        event.setStartTime(request.startTime());
        event.setCapacity(request.capacity());
        event.setAvailableSeats(request.capacity() - reservedSeats);
        event.setTicketPriceCents(request.ticketPriceCents());
        events.flush();
        return EventResponse.from(event);
    }

    @Transactional
    public void delete(long id) {
        var event = events.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (bookings.existsByEventId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Events with booking history cannot be deleted");
        }
        events.delete(event);
        events.flush();
    }

    private void requireFutureStart(Instant startTime) {
        if (!startTime.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Event start time must be in the future");
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Event not found");
    }
}
