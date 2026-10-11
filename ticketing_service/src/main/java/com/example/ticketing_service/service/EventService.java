package com.example.ticketing_service.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing_service.dto.EventRequest;
import com.example.ticketing_service.dto.EventResponse;
import com.example.ticketing_service.entity.Event;
import com.example.ticketing_service.exception.ConflictException;
import com.example.ticketing_service.exception.NotFoundException;
import com.example.ticketing_service.repository.BookingRepository;
import com.example.ticketing_service.repository.EventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

	private final EventRepository eventRepository;
	private final BookingRepository bookingRepository;

	@Transactional(readOnly = true)
	public List<EventResponse> list(boolean includePast) {
		List<Event> events = includePast
				? eventRepository.findAllByOrderByStartsAtAsc()
				: eventRepository.findByStartsAtAfterOrderByStartsAtAsc(Instant.now());
		return events.stream().map(EventResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public EventResponse get(Long id) {
		return EventResponse.from(findEvent(id));
	}

	@Transactional
	public EventResponse create(EventRequest request) {
		Event event = new Event();
		applyDetails(event, request);
		event.setCapacity(request.capacity());
		event.setAvailableSeats(request.capacity());
		return EventResponse.from(eventRepository.save(event));
	}

	@Transactional
	public EventResponse update(Long id, EventRequest request) {
		Event event = findEvent(id);
		applyDetails(event, request);
		if (request.capacity() != event.getCapacity() && eventRepository.updateCapacity(id, request.capacity()) == 0) {
			throw new ConflictException("Capacity can't be lower than the seats already sold");
		}
		// updateCapacity clears the persistence context, so reload to return the current seat counts
		return EventResponse.from(findEvent(id));
	}

	@Transactional
	public void delete(Long id) {
		Event event = findEvent(id);
		// Cancelled bookings count too: their rows still reference the event through a foreign key
		if (bookingRepository.existsByEventId(id)) {
			throw new ConflictException("Events with bookings can't be deleted");
		}
		eventRepository.delete(event);
	}

	private Event findEvent(Long id) {
		return eventRepository.findById(id).orElseThrow(() -> new NotFoundException("Event not found: " + id));
	}

	private static void applyDetails(Event event, EventRequest request) {
		event.setName(request.name());
		event.setDescription(request.description());
		event.setVenue(request.venue());
		event.setStartsAt(request.startsAt());
		event.setPriceCents(request.priceCents());
	}

}
