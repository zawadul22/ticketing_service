package com.example.ticketing_service_backend;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.ticketing_service_backend.dto.BookingRequest;
import com.example.ticketing_service_backend.dto.EventRequest;
import com.example.ticketing_service_backend.entity.Booking;
import com.example.ticketing_service_backend.entity.BookingStatus;
import com.example.ticketing_service_backend.entity.Event;
import com.example.ticketing_service_backend.entity.User;
import com.example.ticketing_service_backend.exception.ApiException;
import com.example.ticketing_service_backend.repository.BookingRepository;
import com.example.ticketing_service_backend.repository.EventRepository;
import com.example.ticketing_service_backend.repository.UserRepository;
import com.example.ticketing_service_backend.service.BookingService;
import com.example.ticketing_service_backend.service.EventService;
import com.example.ticketing_service_backend.service.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingIntegrationTests {
    @LocalServerPort int port;
    @Autowired EventRepository events;
    @MockitoSpyBean BookingRepository bookings;
    @Autowired UserRepository users;
    @Autowired BookingService service;
    @Autowired EventService eventService;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> eventIds = new ArrayList<>();
    private User owner;
    private User other;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        var suffix = UUID.randomUUID() + "@example.com";
        owner = users.saveAndFlush(new User("booking-owner-" + suffix, "fixture-hash"));
        other = users.saveAndFlush(new User("booking-other-" + suffix, "fixture-hash"));
        ownerToken = tokens.issue(owner).accessToken();
        otherToken = tokens.issue(other).accessToken();
    }

    @AfterEach
    void cleanUp() {
        for (var id : eventIds) {
            jdbc.update("delete from bookings where event_id = ?", id);
            events.deleteById(id);
        }
        users.deleteById(owner.getId());
        users.deleteById(other.getId());
    }

    @Test
    void bookingFlowPreservesOwnershipPriceAndCancellationHistory() throws Exception {
        var event = event(10);
        var created = send("POST", bookingPath(event), Map.of("quantity", 3, "userId", other.getId()), ownerToken);
        assertThat(created.statusCode()).isEqualTo(201);
        var booking = body(created);
        var id = booking.path("id").asLong();
        assertThat(booking.path("status").asString()).isEqualTo("CONFIRMED");
        assertThat(booking.path("unitPriceCents").asLong()).isEqualTo(1000L);
        assertThat(booking.path("totalPriceCents").asLong()).isEqualTo(3000L);
        assertThat(booking.path("event").path("availableSeats").asInt()).isEqualTo(7);
        assertThat(bookings.findById(id).orElseThrow().getUser().getId()).isEqualTo(owner.getId());
        assertThat(body(send("GET", "/api/bookings/me", null, otherToken)).size()).isZero();

        eventService.update(event.getId(), new EventRequest("Updated concert", null, "Hall", event.getStartTime(), 10, 2000L));
        var mine = body(send("GET", "/api/bookings/me", null, ownerToken));
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).path("unitPriceCents").asLong()).isEqualTo(1000L);
        assertThat(mine.get(0).path("totalPriceCents").asLong()).isEqualTo(3000L);
        assertThat(mine.get(0).path("event").path("ticketPriceCents").asLong()).isEqualTo(2000L);

        var cancelled = send("POST", cancelPath(id), null, ownerToken);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(body(cancelled).path("status").asString()).isEqualTo("CANCELLED");
        assertThat(seats(event)).isEqualTo(10);
        assertError(send("POST", cancelPath(id), null, ownerToken), 409);
        assertThat(seats(event)).isEqualTo(10);
        assertThat(body(send("GET", "/api/bookings/me", null, ownerToken)).get(0).path("status").asString())
                .isEqualTo("CANCELLED");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11})
    void rejectsQuantitiesOutsideOneToTen(int quantity) throws Exception {
        var event = event(10);
        assertError(send("POST", bookingPath(event), Map.of("quantity", quantity), ownerToken), 400);
        assertThat(seats(event)).isEqualTo(10);
        assertThat(bookings.existsByEventId(event.getId())).isFalse();
    }

    @Test
    void validatesMissingQuantityAndRequiresAuthentication() throws Exception {
        var event = event(10);
        assertError(send("POST", bookingPath(event), Map.of(), ownerToken), 400);
        assertError(send("POST", bookingPath(event), Map.of("quantity", 1), null), 401);
        assertError(send("GET", "/api/bookings/me", null, null), 401);
        assertError(send("POST", cancelPath(Long.MAX_VALUE), null, null), 401);
    }

    @Test
    void insufficientSeatsDoNotCreateBookingOrChangeAvailability() throws Exception {
        var event = event(2);
        assertError(send("POST", bookingPath(event), Map.of("quantity", 3), ownerToken), 409);
        assertThat(seats(event)).isEqualTo(2);
        assertThat(bookings.existsByEventId(event.getId())).isFalse();
    }

    @Test
    void rejectsBookingAndCancellationForStartedEvents() throws Exception {
        var event = event(10);
        var booked = service.create(owner.getId(), event.getId(), new BookingRequest(2));
        jdbc.update("update events set start_time = current_timestamp - interval '1 minute' where id = ?", event.getId());
        assertError(send("POST", bookingPath(event), Map.of("quantity", 1), ownerToken), 409);
        assertError(send("POST", cancelPath(booked.id()), null, ownerToken), 409);
        assertThat(seats(event)).isEqualTo(8);
        assertThat(bookings.findById(booked.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void nonOwnerCannotCancelAndMissingResourcesReturn404() throws Exception {
        var event = event(10);
        var booked = service.create(owner.getId(), event.getId(), new BookingRequest(2));
        assertError(send("POST", cancelPath(booked.id()), null, otherToken), 403);
        assertThat(seats(event)).isEqualTo(8);
        assertError(send("POST", "/api/events/" + Long.MAX_VALUE + "/bookings", Map.of("quantity", 1), ownerToken), 404);
        assertError(send("POST", cancelPath(Long.MAX_VALUE), null, ownerToken), 404);
    }

    @Test
    void returnsExactTotalForLargePrices() throws Exception {
        var event = event(10);
        event.setTicketPriceCents(Long.MAX_VALUE);
        events.saveAndFlush(event);
        var created = send("POST", bookingPath(event), Map.of("quantity", 10), ownerToken);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(new BigInteger(body(created).path("totalPriceCents").asString()))
                .isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TEN));
        assertThat(seats(event)).isZero();
    }

    @Test
    void bookingInsertFailureRollsBackSeatReservation() throws Exception {
        var event = event(10);
        doThrow(new DataIntegrityViolationException("Injected booking insert failure"))
                .when(bookings).saveAndFlush(any(Booking.class));
        assertError(send("POST", bookingPath(event), Map.of("quantity", 3), ownerToken), 409);
        assertThat(seats(event)).isEqualTo(10);
        assertThat(bookings.existsByEventId(event.getId())).isFalse();
    }

    @Test
    void cancellationFailureRollsBackBothStatusAndSeatRestoration() throws Exception {
        var event = event(10);
        var booked = service.create(owner.getId(), event.getId(), new BookingRequest(3));
        doThrow(new DataIntegrityViolationException("Injected cancellation flush failure")).when(bookings).flush();
        assertError(send("POST", cancelPath(booked.id()), null, ownerToken), 409);
        assertThat(seats(event)).isEqualTo(7);
        assertThat(bookings.findById(booked.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void concurrentBookingsCannotOversellSmallEvent() throws Exception {
        var event = event(5);
        var actions = new ArrayList<Callable<Integer>>();
        for (int i = 0; i < 12; i++) {
            actions.add(() -> result(() -> service.create(owner.getId(), event.getId(), new BookingRequest(1))));
        }
        var results = concurrent(actions);
        assertThat(results.stream().filter(status -> status == 200).count()).isEqualTo(5);
        assertThat(results.stream().filter(status -> status == 409).count()).isEqualTo(7);
        assertThat(seats(event)).isZero();
        assertThat(confirmedQuantity(event)).isEqualTo(5);
    }

    @Test
    void concurrentCancellationsRestoreSeatsExactlyOnce() throws Exception {
        var event = event(10);
        var booked = service.create(owner.getId(), event.getId(), new BookingRequest(4));
        var actions = new ArrayList<Callable<Integer>>();
        for (int i = 0; i < 8; i++) {
            actions.add(() -> result(() -> service.cancel(owner.getId(), booked.id())));
        }
        var results = concurrent(actions);
        assertThat(results.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(results.stream().filter(status -> status == 409).count()).isEqualTo(7);
        assertThat(seats(event)).isEqualTo(10);
        assertThat(bookings.findById(booked.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void simultaneousBookingCancellationAndCapacityUpdatePreserveSeatCounts() throws Exception {
        var event = event(10);
        var booked = service.create(owner.getId(), event.getId(), new BookingRequest(4));
        var results = concurrent(List.of(
                () -> result(() -> service.create(other.getId(), event.getId(), new BookingRequest(2))),
                () -> result(() -> service.cancel(owner.getId(), booked.id())),
                () -> result(() -> eventService.update(event.getId(),
                        new EventRequest("Concert", null, "Hall", event.getStartTime(), 6, 1000L)))));
        assertThat(results).containsOnly(200).hasSize(3);
        var stored = events.findById(event.getId()).orElseThrow();
        assertThat(stored.getCapacity()).isEqualTo(6);
        assertThat(stored.getAvailableSeats()).isEqualTo(4);
        assertThat(confirmedQuantity(event)).isEqualTo(2);
    }

    private int confirmedQuantity(Event event) {
        return jdbc.queryForObject("select coalesce(sum(quantity), 0) from bookings where event_id = ? and status = 'CONFIRMED'",
                Integer.class, event.getId());
    }

    private int result(Runnable action) {
        try {
            action.run();
            return 200;
        } catch (ApiException exception) {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            return exception.getStatus().value();
        }
    }

    private List<Integer> concurrent(List<Callable<Integer>> actions) throws Exception {
        var ready = new CountDownLatch(actions.size());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(actions.size())) {
            var futures = actions.stream().map(action -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to start concurrent requests");
                }
                return action.call();
            })).toList();
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                var results = new ArrayList<Integer>();
                for (var future : futures) {
                    results.add(future.get(15, TimeUnit.SECONDS));
                }
                return results;
            } finally {
                start.countDown();
            }
        }
    }

    private Event event(int capacity) {
        var event = events.saveAndFlush(new Event("Concert", "Live music", "Hall",
                Instant.now().plus(1, ChronoUnit.DAYS), capacity, 1000L));
        eventIds.add(event.getId());
        return event;
    }

    private int seats(Event event) {
        return events.findById(event.getId()).orElseThrow().getAvailableSeats();
    }

    private String bookingPath(Event event) {
        return "/api/events/" + event.getId() + "/bookings";
    }

    private String cancelPath(long id) {
        return "/api/bookings/" + id + "/cancel";
    }

    private void assertError(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(body(response).path("status").asInt()).isEqualTo(status);
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, Object data, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.method(method, data == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(), HttpResponse.BodyHandlers.ofString());
    }
}
