package com.example.ticketing_service_backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TicketingServiceBackendApplicationTests {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private final HttpClient client = HttpClient.newHttpClient();

	@Test
	void connectsToPostgres() {
		assertThat(jdbcTemplate.queryForObject("select version()", String.class)).startsWith("PostgreSQL");
	}

	@Test
	void healthIncludesWorkingDatabase() throws Exception {
		var response = get("/api/health");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
	}

	@Test
	void exposesApiDocumentation() throws Exception {
		var response = get("/v3/api-docs");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"openapi\"");
		assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
	}

	@Test
	void deniesAnonymousAccessToOtherRoutes() throws Exception {
		assertThat(get("/api/bookings/me").statusCode()).isEqualTo(401);
	}

	private HttpResponse<String> get(String path) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
