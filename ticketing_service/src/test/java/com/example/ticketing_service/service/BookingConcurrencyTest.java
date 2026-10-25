package com.example.ticketing_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.ticketing_service.TestcontainersConfiguration;
import com.example.ticketing_service.dto.EventRequest;
import com.example.ticketing_service.exception.ConflictException;

// Not @Transactional: every thread must commit its own transaction for the race to be real,
// so the tests clean up their rows afterwards instead of rolling back
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingConcurrencyTest {

	@Autowired
	private BookingService bookingService;

	@Autowired
	private EventService eventService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final List<Long> eventIds = new ArrayList<>();
	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		eventIds.forEach(id -> jdbcTemplate.update("DELETE FROM bookings WHERE event_id = ?", id));
		eventIds.forEach(id -> jdbcTemplate.update("DELETE FROM events WHERE id = ?", id));
		userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
	}

	@Test
	void fiftyConcurrentBookingsForTenSeatsSellExactlyTen() throws Exception {
		Long eventId = newEvent(10);
		List<Callable<Boolean>> attempts = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			Long userId = newUser("buyer" + i + "@example.com");
			attempts.add(() -> {
				bookingService.book(userId, eventId, 1);
				return true;
			});
		}

		List<Boolean> results = runAtOnce(attempts);

		assertThat(results).filteredOn(succeeded -> succeeded).hasSize(10);
		assertThat(eventService.get(eventId).availableSeats()).isZero();
		assertThat(countBookings(eventId, "CONFIRMED")).isEqualTo(10);
	}

	@Test
	void concurrentCancelsOfOneBookingReturnSeatsOnce() throws Exception {
		Long eventId = newEvent(10);
		Long userId = newUser("canceller@example.com");
		Long bookingId = bookingService.book(userId, eventId, 4).id();
		List<Callable<Boolean>> attempts = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			attempts.add(() -> {
				bookingService.cancel(userId, bookingId);
				return true;
			});
		}

		List<Boolean> results = runAtOnce(attempts);

		assertThat(results).filteredOn(succeeded -> succeeded).hasSize(1);
		assertThat(eventService.get(eventId).availableSeats()).isEqualTo(10);
	}

	// Starts every task at the same instant; a task that fails with a 409-type exception counts as false
	private static List<Boolean> runAtOnce(List<Callable<Boolean>> tasks) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Boolean> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(tasks.size())) {
			List<Future<Boolean>> futures = new ArrayList<>();
			for (Callable<Boolean> task : tasks) {
				futures.add(pool.submit(() -> {
					start.await();
					try {
						return task.call();
					}
					catch (ConflictException ex) {
						return false;
					}
				}));
			}
			start.countDown();
			for (Future<Boolean> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
		}
		return results;
	}

	private Long newEvent(int capacity) {
		Long id = eventService.create(new EventRequest("Concert", null, "Main Hall",
				Instant.now().plus(Duration.ofDays(7)), capacity, 2500)).id();
		eventIds.add(id);
		return id;
	}

	private Long newUser(String email) {
		Long id = jdbcTemplate.queryForObject(
				"INSERT INTO users (email, password_hash, role) VALUES (?, 'unused', 'USER') RETURNING id", Long.class,
				email);
		userIds.add(id);
		return id;
	}

	private int countBookings(Long eventId, String status) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM bookings WHERE event_id = ? AND status = ?",
				Integer.class, eventId, status);
	}

}
