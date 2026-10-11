package com.example.ticketing_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.ticketing_service.TestcontainersConfiguration;
import com.example.ticketing_service.dto.EventRequest;
import com.example.ticketing_service.dto.EventResponse;
import com.example.ticketing_service.dto.RegisterRequest;
import com.example.ticketing_service.entity.Booking;
import com.example.ticketing_service.entity.BookingStatus;
import com.example.ticketing_service.entity.Event;
import com.example.ticketing_service.exception.ConflictException;
import com.example.ticketing_service.exception.NotFoundException;
import com.example.ticketing_service.repository.BookingRepository;
import com.example.ticketing_service.repository.EventRepository;
import com.example.ticketing_service.repository.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EventServiceTest {

	private static final Instant NEXT_WEEK = Instant.now().plus(Duration.ofDays(7));

	@Autowired
	private EventService eventService;

	@Autowired
	private UserService userService;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void createSetsAvailableSeatsToCapacity() {
		EventResponse event = eventService.create(request("Concert", NEXT_WEEK, 10));

		assertThat(event.id()).isNotNull();
		assertThat(event.capacity()).isEqualTo(10);
		assertThat(event.availableSeats()).isEqualTo(10);
	}

	@Test
	void listReturnsUpcomingEventsSortedUnlessPastAreIncluded() {
		eventService.create(request("Later", NEXT_WEEK.plus(Duration.ofDays(1)), 10));
		eventService.create(request("Past", Instant.now().minus(Duration.ofDays(1)), 10));
		eventService.create(request("Sooner", NEXT_WEEK, 10));

		assertThat(eventService.list(false)).extracting(EventResponse::name).containsExactly("Sooner", "Later");
		assertThat(eventService.list(true)).extracting(EventResponse::name).containsExactly("Past", "Sooner", "Later");
	}

	@Test
	void getThrowsNotFoundForMissingEvent() {
		assertThatThrownBy(() -> eventService.get(-1L)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void updateChangesDetailsWithoutTouchingSeats() {
		Long id = createWithSoldSeats(10, 3);

		EventResponse updated = eventService.update(id, request("Renamed", NEXT_WEEK, 10));

		assertThat(updated.name()).isEqualTo("Renamed");
		assertThat(updated.availableSeats()).isEqualTo(7);
	}

	@Test
	void updateCapacityShiftsAvailableSeatsBySameAmount() {
		Long id = createWithSoldSeats(10, 3);

		assertThat(eventService.update(id, request("Concert", NEXT_WEEK, 15)).availableSeats()).isEqualTo(12);
		assertThat(eventService.update(id, request("Concert", NEXT_WEEK, 5)).availableSeats()).isEqualTo(2);
	}

	@Test
	void updateRejectsCapacityBelowSeatsSold() {
		Long id = createWithSoldSeats(10, 3);

		assertThatThrownBy(() -> eventService.update(id, request("Concert", NEXT_WEEK, 2)))
				.isInstanceOf(ConflictException.class);
		assertThat(eventService.get(id).capacity()).isEqualTo(10);
	}

	@Test
	void deleteRemovesEventWithoutBookings() {
		Long id = eventService.create(request("Concert", NEXT_WEEK, 10)).id();

		eventService.delete(id);

		assertThat(eventRepository.existsById(id)).isFalse();
	}

	@Test
	void deleteRejectsEventWithCancelledBookings() {
		Long id = eventService.create(request("Concert", NEXT_WEEK, 10)).id();
		Long userId = userService.register(new RegisterRequest("dave@example.com", "secret123")).id();
		Booking booking = new Booking();
		booking.setUser(userRepository.getReferenceById(userId));
		booking.setEvent(eventRepository.getReferenceById(id));
		booking.setQuantity(1);
		booking.setStatus(BookingStatus.CANCELLED);
		bookingRepository.save(booking);

		assertThatThrownBy(() -> eventService.delete(id)).isInstanceOf(ConflictException.class);
	}

	// Reproduces the lost update: a booking commits between loading the event and saving an edit to it.
	// Runs outside the test transaction so the two transactions really commit separately.
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void savingEventDetailsKeepsSeatsSoldByConcurrentBooking() {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		TransactionTemplate concurrentTx = new TransactionTemplate(transactionManager);
		concurrentTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		Long id = eventService.create(request("Concert", NEXT_WEEK, 10)).id();
		try {
			tx.executeWithoutResult(status -> {
				Event event = eventRepository.findById(id).orElseThrow();
				concurrentTx.executeWithoutResult(s -> eventRepository.decrementAvailableSeats(id, 3));
				// The loaded entity still says 10 available seats when this change is flushed
				event.setName("Renamed");
			});

			Event saved = eventRepository.findById(id).orElseThrow();
			assertThat(saved.getName()).isEqualTo("Renamed");
			assertThat(saved.getAvailableSeats()).isEqualTo(7);
		}
		finally {
			eventRepository.deleteById(id);
		}
	}

	private Long createWithSoldSeats(int capacity, int sold) {
		Long id = eventService.create(request("Concert", NEXT_WEEK, capacity)).id();
		eventRepository.decrementAvailableSeats(id, sold);
		return id;
	}

	private static EventRequest request(String name, Instant startsAt, int capacity) {
		return new EventRequest(name, "Description", "Main Hall", startsAt, capacity, 2500);
	}

}
