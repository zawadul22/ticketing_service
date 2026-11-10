package com.example.ticketing_service_backend;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.example.ticketing_service_backend.entity.Booking;
import com.example.ticketing_service_backend.entity.BookingStatus;
import com.example.ticketing_service_backend.entity.Event;
import com.example.ticketing_service_backend.entity.Role;
import com.example.ticketing_service_backend.entity.User;
import com.example.ticketing_service_backend.repository.BookingRepository;
import com.example.ticketing_service_backend.repository.EventRepository;
import com.example.ticketing_service_backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityPersistenceTests {

    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired BookingRepository bookings;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsRelationshipsDefaultsAndPriceSnapshot() {
        var user = users.saveAndFlush(new User("  ENTITY.TEST@Example.com  ", "hashed-password"));
        var start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        var event = events.saveAndFlush(new Event("Concert", "Live music", "Main hall", start, 20, 1500L));
        var booking = bookings.saveAndFlush(new Booking(user, event, 2));
        var createdAt = booking.getCreatedAt().truncatedTo(ChronoUnit.MICROS);

        event.setTicketPriceCents(2000L);
        events.flush();
        entityManager.clear();

        var stored = bookings.findById(booking.getId()).orElseThrow();
        assertThat(stored.getUser().getEmail()).isEqualTo("entity.test@example.com");
        assertThat(stored.getUser().getRole()).isEqualTo(Role.USER);
        assertThat(stored.getUser().getCreatedAt()).isNotNull();
        assertThat(stored.getEvent().getStartTime()).isEqualTo(start);
        assertThat(stored.getEvent().getAvailableSeats()).isEqualTo(20);
        assertThat(stored.getEvent().getTicketPriceCents()).isEqualTo(2000L);
        assertThat(stored.getUnitPriceCents()).isEqualTo(1500L);
        assertThat(stored.getQuantity()).isEqualTo(2);
        assertThat(stored.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        // PostgreSQL rounds timestamp precision to microseconds.
        assertThat(stored.getCreatedAt()).isCloseTo(createdAt, org.assertj.core.api.Assertions.within(1, ChronoUnit.MICROS));
        assertThat(users.findByEmail("entity.test@example.com")).isPresent();
        assertThat(bookings.findByUserIdOrderByCreatedAtDesc(user.getId())).hasSize(1);
        assertThat(bookings.existsByEventId(event.getId())).isTrue();
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        users.saveAndFlush(new User("Entity.Duplicate@example.com", "hashed-password"));
        assertThatThrownBy(() -> users.saveAndFlush(new User(" ENTITY.DUPLICATE@example.com ", "hashed-password")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 0, 0", "10, -1, 0", "10, 11, 0", "10, 10, -1"})
    void databaseRejectsInvalidEventCountsAndPrices(int capacity, int available, long price) {
        assertThatThrownBy(() -> jdbc.update("""
                insert into events (name, venue, start_time, capacity, available_seats, ticket_price_cents)
                values ('Invalid event', 'Hall', current_timestamp, ?, ?, ?)
                """, capacity, available, price)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 100", "11, 100", "1, -1"})
    void databaseRejectsInvalidBookingQuantityAndPrice(int quantity, long price) {
        var user = users.saveAndFlush(new User("entity.booking@example.com", "hashed-password"));
        var event = events.saveAndFlush(newEvent());
        assertThatThrownBy(() -> jdbc.update("""
                insert into bookings (user_id, event_id, quantity, unit_price_cents, status, created_at)
                values (?, ?, ?, ?, 'CONFIRMED', current_timestamp)
                """, user.getId(), event.getId(), quantity, price)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "CANCELLED"})
    void bookingHistoryPreventsEventDeletion(String status) {
        var user = users.saveAndFlush(new User("entity.history@example.com", "hashed-password"));
        var event = events.saveAndFlush(newEvent());
        var booking = new Booking(user, event, 1);
        booking.setStatus(BookingStatus.valueOf(status));
        bookings.saveAndFlush(booking);
        assertThatThrownBy(() -> jdbc.update("delete from events where id = ?", event.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repositoryListsUpcomingEventsInStartOrder() {
        var now = Instant.now();
        var later = newEvent();
        later.setStartTime(now.plus(3, ChronoUnit.DAYS));
        var earlier = newEvent();
        earlier.setStartTime(now.plus(1, ChronoUnit.DAYS));
        var past = newEvent();
        past.setStartTime(now.minus(1, ChronoUnit.DAYS));
        events.saveAndFlush(later);
        events.saveAndFlush(earlier);
        events.saveAndFlush(past);

        assertThat(events.findByStartTimeAfterOrderByStartTimeAsc(now))
                .extracting(Event::getId).containsSubsequence(earlier.getId(), later.getId()).doesNotContain(past.getId());
    }

    private Event newEvent() {
        return new Event("Concert", "Live music", "Main hall", Instant.now().plus(1, ChronoUnit.DAYS), 20, 1500L);
    }
}
