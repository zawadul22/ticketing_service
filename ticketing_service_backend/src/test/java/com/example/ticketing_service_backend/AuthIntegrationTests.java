package com.example.ticketing_service_backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.example.ticketing_service_backend.configuration.AdminProperties;
import com.example.ticketing_service_backend.configuration.JwtProperties;
import com.example.ticketing_service_backend.entity.Role;
import com.example.ticketing_service_backend.repository.UserRepository;
import com.example.ticketing_service_backend.service.AdminSetupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTests {
    private static final String PASSWORD = "Test-password-123";

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @Autowired AdminSetupService adminSetup;

    private final HttpClient client = HttpClient.newHttpClient();
    private final Set<String> emails = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanUp() {
        emails.forEach(email -> users.findByEmail(email).ifPresent(users::delete));
    }

    @Test
    void registersNormalizedUserHashesPasswordAndReturnsUsableToken() throws Exception {
        var email = email();
        var response = post("/api/auth/register", Map.of("email", "  " + email.toUpperCase() + "  ",
                "password", PASSWORD, "role", "ADMIN"), null);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        var body = body(response);
        assertThat(body.path("tokenType").asString()).isEqualTo("Bearer");
        assertThat(Instant.parse(body.path("expiresAt").asString())).isAfter(Instant.now());
        assertThat(body.path("user").path("email").asString()).isEqualTo(email);
        assertThat(body.path("user").path("role").asString()).isEqualTo("USER");
        assertThat(response.body()).doesNotContain("password", "passwordHash");
        var user = users.findByEmail(email).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, user.getPasswordHash())).isTrue();

        var me = get("/api/auth/me", body.path("accessToken").asString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(body(me).path("id").asLong()).isEqualTo(user.getId());
        assertThat(me.body()).doesNotContain("password");
    }

    @Test
    void loginNormalizesEmailAndUsesGenericCredentialErrors() throws Exception {
        var email = email();
        register(email);
        var success = post("/api/auth/login", Map.of("email", " " + email.toUpperCase() + " ", "password", PASSWORD), null);
        assertThat(success.statusCode()).isEqualTo(200);
        assertThat(get("/api/auth/me", body(success).path("accessToken").asString()).statusCode()).isEqualTo(200);

        var wrongPassword = post("/api/auth/login", Map.of("email", email, "password", "wrong-password"), null);
        var unknownUser = post("/api/auth/login", Map.of("email", email(), "password", PASSWORD), null);
        assertError(wrongPassword, 401);
        assertError(unknownUser, 401);
        assertThat(body(wrongPassword).path("message").asString())
                .isEqualTo(body(unknownUser).path("message").asString()).isEqualTo("Invalid email or password");
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        var email = email();
        register(email);
        assertError(post("/api/auth/register", Map.of("email", email.toUpperCase(), "password", PASSWORD), null), 409);
    }

    @Test
    void simultaneousRegistrationsCannotCreateDuplicateUsers() throws Exception {
        var email = email();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> post("/api/auth/register", Map.of("email", email, "password", PASSWORD), null));
            var second = executor.submit(() -> post("/api/auth/register", Map.of("email", email, "password", PASSWORD), null));
            assertThat(new int[] {first.get().statusCode(), second.get().statusCode()})
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad-email", "missing-at.example.com"})
    void invalidEmailsReturnFieldErrors(String email) throws Exception {
        var response = post("/api/auth/register", Map.of("email", email, "password", PASSWORD), null);
        assertError(response, 400);
        assertThat(body(response).path("fieldErrors").has("email")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "        "})
    void invalidPasswordsAreRejected(String password) throws Exception {
        assertError(post("/api/auth/register", Map.of("email", email(), "password", password), null), 400);
    }

    @Test
    void rejectsPasswordsBeyondBcryptByteLimit() throws Exception {
        var email = email();
        assertError(post("/api/auth/register", Map.of("email", email, "password", "a".repeat(73)), null), 400);
        assertError(post("/api/auth/register", Map.of("email", email, "password", "é".repeat(40)), null), 400);
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test
    void malformedAndMissingRequestBodiesReturnJsonErrors() throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/auth/register"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{" )).build();
        assertError(client.send(request, HttpResponse.BodyHandlers.ofString()), 400);
        assertError(post("/api/auth/register", Map.of(), null), 400);
    }

    @Test
    void missingAndMalformedBearerTokensAreRejected() throws Exception {
        assertError(get("/api/auth/me", null), 401);
        assertError(get("/api/auth/me", "not-a-jwt"), 401);
        var email = email();
        var token = body(register(email)).path("accessToken").asString();
        var parts = token.split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        assertError(get("/api/auth/me", String.join(".", parts)), 401);
    }

    @Test
    void rejectsExpiredWrongIssuerAndIncompleteTokens() throws Exception {
        var email = email();
        var id = body(register(email)).path("user").path("id").asString();
        assertError(get("/api/auth/me", signedToken(id, JwtProperties.ISSUER, Instant.now().minusSeconds(300))), 401);
        assertError(get("/api/auth/me", signedToken(id, "another-service", Instant.now().plusSeconds(300))), 401);
        assertError(get("/api/auth/me", signedToken("not-an-id", JwtProperties.ISSUER, Instant.now().plusSeconds(300))), 401);
        assertError(get("/api/auth/me", signedToken(id, JwtProperties.ISSUER, null)), 401);
    }

    @Test
    void deletedAccountsCannotUsePreviouslyIssuedTokens() throws Exception {
        var email = email();
        var token = body(register(email)).path("accessToken").asString();
        users.delete(users.findByEmail(email).orElseThrow());
        assertError(get("/api/auth/me", token), 401);
    }

    @Test
    void regularUsersCannotManageEventsOrListPastEvents() throws Exception {
        var token = body(register(email())).path("accessToken").asString();
        assertError(post("/api/events", Map.of(), token), 403);
        assertError(send("PUT", "/api/events/1", "{}", token), 403);
        assertError(send("DELETE", "/api/events/1", null, token), 403);
        assertError(get("/api/events?includePast=true", token), 403);
    }

    @Test
    void adminSetupIsIdempotentAndPermissionsUseCurrentDatabaseRole() throws Exception {
        var email = email();
        var properties = new AdminProperties(true, " " + email.toUpperCase() + " ", PASSWORD);
        adminSetup.initialize(properties);
        var original = users.findByEmail(email).orElseThrow();
        var originalHash = original.getPasswordHash();
        adminSetup.initialize(new AdminProperties(true, email, "different-password"));
        assertThat(users.findByEmail(email).orElseThrow().getPasswordHash()).isEqualTo(originalHash);

        var login = post("/api/auth/login", Map.of("email", email, "password", PASSWORD), null);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(body(login).path("user").path("role").asString()).isEqualTo("ADMIN");
        var token = body(login).path("accessToken").asString();
        assertThat(get("/api/events?includePast=true", token).statusCode()).isEqualTo(200);
        original.setRole(Role.USER);
        users.saveAndFlush(original);
        assertError(get("/api/events?includePast=true", token), 403);
    }

    @Test
    void adminSetupRefusesToPromoteRegisteredUsers() throws Exception {
        var email = email();
        register(email);
        assertThatThrownBy(() -> adminSetup.initialize(new AdminProperties(true, email, PASSWORD)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.findByEmail(email).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void adminSetupValidatesConfigurationAndCanBeDisabled() {
        adminSetup.initialize(new AdminProperties(false, null, null));
        assertThatThrownBy(() -> adminSetup.initialize(new AdminProperties(true, "invalid", "short")))
                .isInstanceOf(IllegalStateException.class);
    }

    private String email() {
        var email = "auth-test-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        return email;
    }

    private HttpResponse<String> register(String email) throws Exception {
        var response = post("/api/auth/register", Map.of("email", email, "password", PASSWORD), null);
        assertThat(response.statusCode()).isEqualTo(201);
        return response;
    }

    private String signedToken(String subject, String issuer, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().subject(subject).issuer(issuer).issuedAt(Instant.now().minusSeconds(600));
        if (expiresAt != null) {
            claims.expiresAt(expiresAt);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private void assertError(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(body(response).path("status").asInt()).isEqualTo(status);
        assertThat(body(response).path("message").asString()).isNotBlank();
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private HttpResponse<String> post(String path, Object body, String token) throws Exception {
        return send("POST", path, json.writeValueAsString(body), token);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, null, token);
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
