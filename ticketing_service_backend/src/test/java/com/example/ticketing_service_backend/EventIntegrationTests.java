package com.example.ticketing_service_backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import com.example.ticketing_service_backend.dto.EventRequest;
import com.example.ticketing_service_backend.entity.Booking;
import com.example.ticketing_service_backend.entity.BookingStatus;
import com.example.ticketing_service_backend.entity.Event;
import com.example.ticketing_service_backend.entity.Role;
import com.example.ticketing_service_backend.entity.User;
import com.example.ticketing_service_backend.exception.ApiException;
import com.example.ticketing_service_backend.repository.BookingRepository;
import com.example.ticketing_service_backend.repository.EventRepository;
import com.example.ticketing_service_backend.repository.UserRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EventIntegrationTests {
    @LocalServerPort int port;
    @Autowired EventRepository events;
    @Autowired BookingRepository bookings;
    @Autowired UserRepository users;
    @Autowired EventService service;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<Long> eventIds = new ArrayList<>();
    private User admin;
    private User user;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        var suffix = UUID.randomUUID() + "@example.com";
        admin = new User("event-admin-" + suffix, "test-fixture-hash");
        admin.setRole(Role.ADMIN);
        admin = users.saveAndFlush(admin);
        user = users.saveAndFlush(new User("event-user-" + suffix, "test-fixture-hash"));
        adminToken = tokens.issue(admin).accessToken();
        userToken = tokens.issue(user).accessToken();
    }

    @AfterEach
    void cleanUp() {
        // Remove only fixtures belonging to this test, preserving existing application data.
        for (var id : eventIds) {
            jdbc.update("delete from bookings where event_id = ?", id);
            events.deleteById(id);
        }
        users.deleteById(user.getId());
        users.deleteById(admin.getId());
    }

    @Test
    void adminCanCreateReadUpdateAndDeleteEvent() throws Exception {
        var request = request(10);
        var created = send("POST", "/api/events", request, adminToken);
        assertThat(created.statusCode()).isEqualTo(201);
        var id = body(created).path("id").asLong();
        eventIds.add(id);
        assertThat(created.headers().firstValue("Location")).contains("/api/events/" + id);
        assertThat(body(created).path("availableSeats").asInt()).isEqualTo(10);
        assertThat(send("GET", "/api/events/" + id, null, null).statusCode()).isEqualTo(200);

        var changed = new EventRequest("Updated concert", null, "New venue", request.startTime(), 15, 2000L);
        var updated = send("PUT", "/api/events/" + id, changed, adminToken);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(body(updated).path("name").asString()).isEqualTo("Updated concert");
        assertThat(body(updated).path("availableSeats").asInt()).isEqualTo(15);
        assertThat(body(updated).path("ticketPriceCents").asLong()).isEqualTo(2000L);
        assertThat(send("DELETE", "/api/events/" + id, null, adminToken).statusCode()).isEqualTo(204);
        assertError(send("GET", "/api/events/" + id, null, null), 404);
    }

    @Test
    void publicListingExcludesPastEventsAndSortsByStartTime() throws Exception {
        var late = fixture(Instant.now().plus(3, ChronoUnit.DAYS));
        var early = fixture(Instant.now().plus(1, ChronoUnit.DAYS));
        var past = fixture(Instant.now().minus(1, ChronoUnit.DAYS));
        var response = send("GET", "/api/events", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(ids(response)).containsSubsequence(early.getId(), late.getId()).doesNotContain(past.getId());

        var all = send("GET", "/api/events?includePast=true", null, adminToken);
        assertThat(all.statusCode()).isEqualTo(200);
        assertThat(ids(all)).containsSubsequence(past.getId(), early.getId(), late.getId());
        // Details remain public for an event even after it has started.
        assertThat(send("GET", "/api/events/" + past.getId(), null, null).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "yes", "1"})
    void pastEventListingCannotBypassAdminAuthorization(String value) throws Exception {
        assertError(send("GET", "/api/events?includePast=" + value, null, userToken), 403);
        assertError(send("GET", "/api/events?includePast=" + value, null, null), 401);
    }

    @Test
    void creationUpdatesAndDeletionRequireAdmin() throws Exception {
        var event = fixture(Instant.now().plus(1, ChronoUnit.DAYS));
        for (var token : List.of(userToken, "")) {
            int status = token.isEmpty() ? 401 : 403;
            var auth = token.isEmpty() ? null : token;
            assertError(send("POST", "/api/events", request(10), auth), status);
            assertError(send("PUT", "/api/events/" + event.getId(), request(10), auth), status);
            assertError(send("DELETE", "/api/events/" + event.getId(), null, auth), status);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "venue", "startTime", "capacity", "ticketPriceCents"})
    void validatesRequiredFieldsOnCreateAndUpdate(String missing) throws Exception {
        var values = json.convertValue(request(10), Map.class);
        values.remove(missing);
        assertError(send("POST", "/api/events", values, adminToken), 400);
        var event = fixture(Instant.now().plus(1, ChronoUnit.DAYS));
        assertError(send("PUT", "/api/events/" + event.getId(), values, adminToken), 400);
    }

    @Test
    void rejectsInvalidValuesAndPastStartTimes() throws Exception {
        var future = Instant.now().plus(1, ChronoUnit.DAYS);
        for (var invalid : List.of(
                new EventRequest(" ", null, "Hall", future, 10, 100L),
                new EventRequest("Concert", null, " ", future, 10, 100L),
                new EventRequest("x".repeat(256), null, "Hall", future, 10, 100L),
                new EventRequest("Concert", null, "Hall", future, 0, 100L),
                new EventRequest("Concert", null, "Hall", future, -1, 100L),
                new EventRequest("Concert", null, "Hall", future, 10, -1L),
                new EventRequest("Concert", null, "Hall", Instant.now().minusSeconds(1), 10, 100L))) {
            assertError(send("POST", "/api/events", invalid, adminToken), 400);
        }
        var event = fixture(future);
        assertError(send("PUT", "/api/events/" + event.getId(),
                new EventRequest("Concert", null, "Hall", Instant.now().minusSeconds(1), 10, 100L), adminToken), 400);
    }

    @Test
    void supportsFreeEventsAndTimezoneOffsets() throws Exception {
        var data = Map.of("name", "Free concert", "venue", "Hall", "startTime", "2099-01-01T18:00:00+06:00",
                "capacity", 1, "ticketPriceCents", 0);
        var created = send("POST", "/api/events", data, adminToken);
        assertThat(created.statusCode()).isEqualTo(201);
        eventIds.add(body(created).path("id").asLong());
        assertThat(Instant.parse(body(created).path("startTime").asString())).isEqualTo(Instant.parse("2099-01-01T12:00:00Z"));
    }

    @Test
    void missingEventsAndInvalidIdsReturnConsistentErrors() throws Exception {
        assertError(send("GET", "/api/events/" + Long.MAX_VALUE, null, null), 404);
        assertError(send("PUT", "/api/events/" + Long.MAX_VALUE, request(10), adminToken), 404);
        assertError(send("DELETE", "/api/events/" + Long.MAX_VALUE, null, adminToken), 404);
        assertError(send("GET", "/api/events/not-an-id", null, null), 400);
        assertError(send("GET", "/api/events?includePast=invalid", null, null), 400);
    }

    @Test
    void capacityChangesPreserveReservedSeatsAndPriceSnapshots() throws Exception {
        var event = fixture(Instant.now().plus(1, ChronoUnit.DAYS));
        event.setAvailableSeats(6);
        events.saveAndFlush(event);
        var booking = bookings.saveAndFlush(new Booking(user, event, 4));

        var reduced = send("PUT", "/api/events/" + event.getId(), withCapacity(event, 4), adminToken);
        assertThat(reduced.statusCode()).isEqualTo(200);
        assertThat(body(reduced).path("availableSeats").asInt()).isZero();
        assertError(send("PUT", "/api/events/" + event.getId(), withCapacity(event, 3), adminToken), 409);
        var increased = send("PUT", "/api/events/" + event.getId(), withCapacity(event, 15), adminToken);
        assertThat(increased.statusCode()).isEqualTo(200);
        assertThat(body(increased).path("availableSeats").asInt()).isEqualTo(11);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getUnitPriceCents()).isEqualTo(1000L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "CANCELLED"})
    void cannotDeleteEventsWithAnyBookingHistory(String status) throws Exception {
        var event = fixture(Instant.now().plus(1, ChronoUnit.DAYS));
        var booking = new Booking(user, event, 1);
        booking.setStatus(BookingStatus.valueOf(status));
        bookings.saveAndFlush(booking);
        assertError(send("DELETE", "/api/events/" + event.getId(), null, adminToken), 409);
        assertThat(events.existsById(event.getId())).isTrue();
    }

    @Test
    void canEditPastEventWithoutReschedulingItIntoThePast() throws Exception {
        var event = fixture(Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS));
        assertThat(send("PUT", "/api/events/" + event.getId(), withCapacity(event, 20), adminToken).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void capacityUpdateAndAtomicReservationAreSafeInEitherLockOrder(boolean reservationFirst) throws Exception {
        var event = fixture(Instant.now().plus(1, ChronoUnit.DAYS));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                if (reservationFirst) {
                    reserveFour(event.getId());
                } else {
                    service.update(event.getId(), withCapacity(event, 15));
                }
                locked.countDown();
                await(release);
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    if (reservationFirst) {
                        service.update(event.getId(), withCapacity(event, 15));
                    } else {
                        new TransactionTemplate(transactions).executeWithoutResult(transaction -> reserveFour(event.getId()));
                    }
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        var stored = events.findById(event.getId()).orElseThrow();
        assertThat(stored.getCapacity()).isEqualTo(15);
        assertThat(stored.getAvailableSeats()).isEqualTo(11);
    }

    @Test
    void rejectedCapacityReductionLeavesCommittedReservationsUnchanged() {
        var event = fixture(Instant.now().plus(1, ChronoUnit.DAYS));
        // A preceding reservation leaves four seats reserved; the attempted reduction must roll back.
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> reserveFour(event.getId()));
        assertThatThrownBy(() -> service.update(event.getId(), withCapacity(event, 3)))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        var stored = events.findById(event.getId()).orElseThrow();
        assertThat(stored.getCapacity()).isEqualTo(10);
        assertThat(stored.getAvailableSeats()).isEqualTo(6);
    }

    @Test
    void swaggerSupportsOptionalBearerAuthenticationForEventListing() throws Exception {
        var response = send("GET", "/v3/api-docs", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var security = body(response).path("paths").path("/api/events").path("get").path("security");
        assertThat(security.size()).isEqualTo(2);
        assertThat(security.get(0).size()).isZero();
        assertThat(security.get(1).has("bearerAuth")).isTrue();
    }

    private void reserveFour(long id) {
        assertThat(jdbc.update("update events set available_seats = available_seats - 4 where id = ? and available_seats >= 4", id))
                .isEqualTo(1);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private Event fixture(Instant start) {
        var event = events.saveAndFlush(new Event("Concert", "Live music", "Hall", start, 10, 1000L));
        eventIds.add(event.getId());
        return event;
    }

    private EventRequest request(int capacity) {
        return new EventRequest("Concert", "Live music", "Hall", Instant.now().plus(1, ChronoUnit.DAYS), capacity, 1000L);
    }

    private EventRequest withCapacity(Event event, int capacity) {
        return new EventRequest("Updated concert", "Live music", "Hall", event.getStartTime(), capacity, 2000L);
    }

    private List<Long> ids(HttpResponse<String> response) {
        var array = body(response);
        return IntStream.range(0, array.size()).mapToObj(index -> array.get(index).path("id").asLong()).toList();
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
                .timeout(java.time.Duration.ofSeconds(15)).header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.method(method, data == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(), HttpResponse.BodyHandlers.ofString());
    }
}
