package com.example.ticketing_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing_service.TestcontainersConfiguration;
import com.example.ticketing_service.dto.BookingResponse;
import com.example.ticketing_service.dto.EventRequest;
import com.example.ticketing_service.entity.BookingStatus;
import com.example.ticketing_service.entity.Role;
import com.example.ticketing_service.entity.User;
import com.example.ticketing_service.exception.ConflictException;
import com.example.ticketing_service.exception.ForbiddenException;
import com.example.ticketing_service.exception.NotEnoughSeatsException;
import com.example.ticketing_service.exception.NotFoundException;
import com.example.ticketing_service.repository.UserRepository;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class BookingServiceTest {

	private static final Instant NEXT_WEEK = Instant.now().plus(Duration.ofDays(7));

	@Autowired
	private BookingService bookingService;

	@Autowired
	private EventService eventService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	void bookTakesSeatsAndConfirms() {
		Long userId = newUser("alice@example.com");
		Long eventId = newEvent(NEXT_WEEK, 10);

		BookingResponse booking = bookingService.book(userId, eventId, 3);

		assertThat(booking.id()).isNotNull();
		assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(booking.quantity()).isEqualTo(3);
		assertThat(booking.eventName()).isEqualTo("Concert");
		assertThat(eventService.get(eventId).availableSeats()).isEqualTo(7);
	}

	@Test
	void bookFailsWhenNotEnoughSeatsAndLeavesSeatsUnchanged() {
		Long userId = newUser("bob@example.com");
		Long eventId = newEvent(NEXT_WEEK, 2);

		assertThatThrownBy(() -> bookingService.book(userId, eventId, 3)).isInstanceOf(NotEnoughSeatsException.class);
		assertThat(eventService.get(eventId).availableSeats()).isEqualTo(2);
	}

	@Test
	void bookFailsForPastEvent() {
		Long userId = newUser("carol@example.com");
		Long eventId = newEvent(Instant.now().minus(Duration.ofDays(1)), 10);

		assertThatThrownBy(() -> bookingService.book(userId, eventId, 1)).isInstanceOf(ConflictException.class)
				.hasMessageContaining("already started");
	}

	@Test
	void bookFailsForMissingEvent() {
		Long userId = newUser("dave@example.com");

		assertThatThrownBy(() -> bookingService.book(userId, -1L, 1)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void listMineReturnsOnlyOwnBookingsNewestFirst() {
		Long erin = newUser("erin@example.com");
		Long frank = newUser("frank@example.com");
		Long first = bookingService.book(erin, newEvent(NEXT_WEEK, 10), 1).id();
		Long second = bookingService.book(erin, newEvent(NEXT_WEEK, 10), 2).id();
		bookingService.book(frank, newEvent(NEXT_WEEK, 10), 1);

		assertThat(bookingService.listMine(erin)).extracting(BookingResponse::id).containsExactly(second, first);
	}

	@Test
	void cancelReturnsSeats() {
		Long userId = newUser("grace@example.com");
		Long eventId = newEvent(NEXT_WEEK, 10);
		Long bookingId = bookingService.book(userId, eventId, 4).id();

		BookingResponse cancelled = bookingService.cancel(userId, bookingId);

		assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(eventService.get(eventId).availableSeats()).isEqualTo(10);
	}

	@Test
	void cancelTwiceIsRejectedAndReturnsSeatsOnce() {
		Long userId = newUser("henry@example.com");
		Long eventId = newEvent(NEXT_WEEK, 10);
		Long bookingId = bookingService.book(userId, eventId, 4).id();
		bookingService.cancel(userId, bookingId);

		assertThatThrownBy(() -> bookingService.cancel(userId, bookingId)).isInstanceOf(ConflictException.class)
				.hasMessage("Booking is already cancelled");
		assertThat(eventService.get(eventId).availableSeats()).isEqualTo(10);
	}

	@Test
	void cancelOtherUsersBookingIsForbidden() {
		Long owner = newUser("ivy@example.com");
		Long other = newUser("jack@example.com");
		Long bookingId = bookingService.book(owner, newEvent(NEXT_WEEK, 10), 1).id();

		assertThatThrownBy(() -> bookingService.cancel(other, bookingId)).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void cancelAfterEventStartedIsRejected() {
		Long userId = newUser("kate@example.com");
		Long eventId = newEvent(NEXT_WEEK, 10);
		Long bookingId = bookingService.book(userId, eventId, 1).id();
		jdbcTemplate.update("UPDATE events SET starts_at = now() - interval '1 hour' WHERE id = ?", eventId);
		// The SQL bypasses Hibernate, so drop its cached Event, as a separate request would have a fresh context
		entityManager.clear();

		assertThatThrownBy(() -> bookingService.cancel(userId, bookingId)).isInstanceOf(ConflictException.class)
				.hasMessageContaining("event has started");
	}

	@Test
	void cancelMissingBookingIsNotFound() {
		Long userId = newUser("leo@example.com");

		assertThatThrownBy(() -> bookingService.cancel(userId, -1L)).isInstanceOf(NotFoundException.class);
	}

	private Long newUser(String email) {
		User user = new User();
		user.setEmail(email);
		// Not a real hash: these users never log in, and skipping BCrypt keeps the tests fast
		user.setPasswordHash("unused");
		user.setRole(Role.USER);
		return userRepository.save(user).getId();
	}

	private Long newEvent(Instant startsAt, int capacity) {
		return eventService.create(new EventRequest("Concert", null, "Main Hall", startsAt, capacity, 2500)).id();
	}

}
