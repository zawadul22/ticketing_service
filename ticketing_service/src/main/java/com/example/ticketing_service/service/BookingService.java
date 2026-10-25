package com.example.ticketing_service.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing_service.dto.BookingResponse;
import com.example.ticketing_service.entity.Booking;
import com.example.ticketing_service.entity.BookingStatus;
import com.example.ticketing_service.entity.Event;
import com.example.ticketing_service.exception.ConflictException;
import com.example.ticketing_service.exception.ForbiddenException;
import com.example.ticketing_service.exception.NotEnoughSeatsException;
import com.example.ticketing_service.exception.NotFoundException;
import com.example.ticketing_service.repository.BookingRepository;
import com.example.ticketing_service.repository.EventRepository;
import com.example.ticketing_service.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {

	private final BookingRepository bookingRepository;
	private final EventRepository eventRepository;
	private final UserRepository userRepository;

	// The seat decrement and the booking insert commit together, or neither does
	@Transactional
	public BookingResponse book(Long userId, Long eventId, int quantity) {
		Event event = eventRepository.findById(eventId)
				.orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
		ensureNotStarted(event, "Can't book an event that has already started");
		if (eventRepository.decrementAvailableSeats(eventId, quantity) == 0) {
			throw new NotEnoughSeatsException();
		}
		Booking booking = new Booking();
		// References carry only the ids for the foreign keys; neither row is loaded
		booking.setUser(userRepository.getReferenceById(userId));
		booking.setEvent(eventRepository.getReferenceById(eventId));
		booking.setQuantity(quantity);
		booking.setStatus(BookingStatus.CONFIRMED);
		return BookingResponse.from(bookingRepository.save(booking));
	}

	@Transactional(readOnly = true)
	public List<BookingResponse> listMine(Long userId) {
		return bookingRepository
				.findByUserIdOrderByCreatedAtDesc(userId)
				.stream()
				.map(BookingResponse::from)
				.toList();
	}

	@Transactional
	public BookingResponse cancel(Long userId, Long bookingId) {
		Booking booking = findBooking(bookingId);
		if (!booking.getUser().getId().equals(userId)) {
			throw new ForbiddenException("You can only cancel your own bookings");
		}
		Long eventId = booking.getEvent().getId();
		ensureNotStarted(booking.getEvent(), "Can't cancel a booking after the event has started");
		if (bookingRepository.cancelIfConfirmed(bookingId) == 0) {
			throw new ConflictException("Booking is already cancelled");
		}
		eventRepository.incrementAvailableSeats(eventId, booking.getQuantity());
		// The bulk updates cleared the persistence context, so reload to return the new status
		return BookingResponse.from(findBooking(bookingId));
	}

	private Booking findBooking(Long id) {
		return bookingRepository.findById(id).orElseThrow(() -> new NotFoundException("Booking not found: " + id));
	}

	private static void ensureNotStarted(Event event, String message) {
		if (!event.getStartsAt().isAfter(Instant.now())) {
			throw new ConflictException(message);
		}
	}

}
