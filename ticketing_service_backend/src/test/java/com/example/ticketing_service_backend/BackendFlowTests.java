package com.example.ticketing_service_backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.ticketing_service_backend.configuration.AdminProperties;
import com.example.ticketing_service_backend.repository.EventRepository;
import com.example.ticketing_service_backend.repository.UserRepository;
import com.example.ticketing_service_backend.service.AdminSetupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BackendFlowTests {
    @LocalServerPort int port;
    @Autowired AdminSetupService adminSetup;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<String> emails = new ArrayList<>();
    private final List<Long> eventIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (var id : eventIds) {
            jdbc.update("delete from bookings where event_id = ?", id);
            events.deleteById(id);
        }
        emails.forEach(email -> users.findByEmail(email).ifPresent(users::delete));
    }

    @Test
    void completeUserAndAdminFlowsOverHttp() throws Exception {
        var password = "Flow-password-123";
        var suffix = UUID.randomUUID() + "@example.com";
        var adminEmail = "flow-admin-" + suffix;
        var userEmail = "flow-user-" + suffix;
        emails.addAll(List.of(adminEmail, userEmail));
        adminSetup.initialize(new AdminProperties(true, adminEmail, password));

        var adminLogin = call("POST", "/api/auth/login", Map.of("email", adminEmail, "password", password), null, 200);
        var adminToken = adminLogin.path("accessToken").asString();
        call("POST", "/api/auth/register", Map.of("email", userEmail, "password", password), null, 201);
        var login = call("POST", "/api/auth/login", Map.of("email", userEmail, "password", password), null, 200);
        var userToken = login.path("accessToken").asString();
        assertThat(call("GET", "/api/auth/me", null, userToken, 200).path("email").asString()).isEqualTo(userEmail);

        var start = Instant.now().plusSeconds(86400).toString();
        var eventData = Map.of("name", "Flow concert", "venue", "Hall", "startTime", start,
                "capacity", 5, "ticketPriceCents", 1500);
        var created = call("POST", "/api/events", eventData, adminToken, 201);
        var id = created.path("id").asLong();
        eventIds.add(id);
        assertThat(call("GET", "/api/events", null, null, 200).toString()).contains("Flow concert");
        assertThat(call("GET", "/api/events/" + id, null, null, 200).path("availableSeats").asInt()).isEqualTo(5);
        call("GET", "/api/events?includePast=true", null, adminToken, 200);
        call("POST", "/api/events", eventData, userToken, 403);

        var booking = call("POST", "/api/events/" + id + "/bookings", Map.of("quantity", 2), userToken, 201);
        var bookingId = booking.path("id").asLong();
        assertThat(booking.path("totalPriceCents").asLong()).isEqualTo(3000L);
        assertThat(call("GET", "/api/events/" + id, null, null, 200).path("availableSeats").asInt()).isEqualTo(3);
        assertThat(call("GET", "/api/bookings/me", null, userToken, 200).get(0).path("id").asLong()).isEqualTo(bookingId);

        var updatedData = Map.of("name", "Updated flow concert", "venue", "New hall", "startTime", start,
                "capacity", 6, "ticketPriceCents", 2000);
        assertThat(call("PUT", "/api/events/" + id, updatedData, adminToken, 200).path("availableSeats").asInt()).isEqualTo(4);
        assertThat(call("GET", "/api/bookings/me", null, userToken, 200).get(0).path("totalPriceCents").asLong()).isEqualTo(3000L);
        call("DELETE", "/api/events/" + id, null, adminToken, 409);
        assertThat(call("POST", "/api/bookings/" + bookingId + "/cancel", null, userToken, 200)
                .path("status").asString()).isEqualTo("CANCELLED");
        assertThat(call("GET", "/api/events/" + id, null, null, 200).path("availableSeats").asInt()).isEqualTo(6);
        call("POST", "/api/bookings/" + bookingId + "/cancel", null, userToken, 409);
        call("DELETE", "/api/events/" + id, null, adminToken, 409);

        var disposable = call("POST", "/api/events", eventData, adminToken, 201).path("id").asLong();
        eventIds.add(disposable);
        call("DELETE", "/api/events/" + disposable, null, adminToken, 204);
        call("GET", "/api/events/" + disposable, null, null, 404);
    }

    @Test
    void swaggerDocumentsEveryBackendRouteAndBearerAuthentication() throws Exception {
        var ui = send("GET", "/swagger-ui/index.html", null, null);
        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).contains("swagger-ui-bundle.js");
        var api = call("GET", "/v3/api-docs", null, null, 200);
        assertThat(api.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asString())
                .isEqualTo("bearer");
        var paths = api.path("paths");
        for (var path : List.of("/api/auth/register", "/api/auth/login", "/api/auth/me", "/api/events",
                "/api/events/{id}", "/api/events/{eventId}/bookings", "/api/bookings/me", "/api/bookings/{id}/cancel")) {
            assertThat(paths.has(path)).as("Documented route %s", path).isTrue();
        }
        for (var path : List.of("/api/auth/me", "/api/bookings/me")) {
            assertThat(paths.path(path).path("get").path("security").get(0).has("bearerAuth")).isTrue();
        }
        for (var path : List.of("/api/events", "/api/events/{eventId}/bookings", "/api/bookings/{id}/cancel")) {
            assertThat(paths.path(path).path("post").path("security").get(0).has("bearerAuth")).isTrue();
        }
    }

    private JsonNode call(String method, String path, Object data, String token, int expected) throws Exception {
        var response = send(method, path, data, token);
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(expected);
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, Object data, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.method(method, data == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(), HttpResponse.BodyHandlers.ofString());
    }
}
