package com.example.ticketing_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

// End to end with real tokens: the controller slice tests fake the JWT, so this checks the real encoder/decoder wiring
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

	private static final String EVENT_JSON = """
			{"name":"Concert","venue":"Main Hall","startsAt":"2099-01-01T19:00:00Z","capacity":10,"priceCents":2500}
			""";

	@Autowired
	private MockMvcTester mvc;

	@Value("${app.admin.email}")
	private String adminEmail;

	@Value("${app.admin.password}")
	private String adminPassword;

	@Test
	void registeredUserTokenWorksForMeButNotForAdminEndpoints() throws Exception {
		String token = tokenFrom(post("/api/auth/register", "{\"email\":\"henry@example.com\",\"password\":\"secret123\"}"));

		assertThat(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.email")
				.isEqualTo("henry@example.com");
		assertThat(post("/api/events", EVENT_JSON).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void seededAdminCanLogInAndCreateEvents() throws Exception {
		String token = tokenFrom(post("/api/auth/login",
				"{\"email\":\"" + adminEmail + "\",\"password\":\"" + adminPassword + "\"}"));

		assertThat(post("/api/events", EVENT_JSON).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.hasStatus(HttpStatus.CREATED);
	}

	@Test
	void tamperedTokenIsRejected() throws Exception {
		String token = tokenFrom(post("/api/auth/register", "{\"email\":\"ivy@example.com\",\"password\":\"secret123\"}"));
		// Flip one character of the signature
		String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

		assertThat(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private static String tokenFrom(MockMvcTester.MockMvcRequestBuilder request) throws Exception {
		MvcTestResult result = request.exchange();
		assertThat(result).hasStatus2xxSuccessful();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
	}

}
