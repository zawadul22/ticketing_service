package com.example.ticketing_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.ticketing_service.config.SecurityConfig;
import com.example.ticketing_service.dto.EventRequest;
import com.example.ticketing_service.dto.EventResponse;
import com.example.ticketing_service.exception.ConflictException;
import com.example.ticketing_service.exception.NotFoundException;
import com.example.ticketing_service.service.EventService;

@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
class EventControllerTest {

	private static final EventResponse EVENT = new EventResponse(1L, "Concert", "Description", "Main Hall",
			Instant.parse("2099-01-01T19:00:00Z"), 10, 10, 2500, Instant.parse("2026-10-11T10:00:00Z"));

	private static final String VALID_JSON = """
			{"name":"Concert","venue":"Main Hall","startsAt":"2099-01-01T19:00:00Z","capacity":10,"priceCents":2500}
			""";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private EventService eventService;

	@Test
	void listPassesIncludePastToService() {
		given(eventService.list(true)).willReturn(List.of(EVENT));

		assertThat(mvc.get().uri("/api/events?includePast=true").with(admin()))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$[0].name")
				.isEqualTo("Concert");
		then(eventService).should().list(true);
	}

	@Test
	void listIsPublic() {
		given(eventService.list(false)).willReturn(List.of(EVENT));

		assertThat(mvc.get().uri("/api/events")).hasStatusOk();
	}

	@Test
	void includePastIsForbiddenForUsers() {
		assertThat(mvc.get().uri("/api/events?includePast=true").with(user())).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void includePastRequiresAuthentication() {
		assertThat(mvc.get().uri("/api/events?includePast=true")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void createReturnsCreatedEvent() {
		given(eventService.create(any(EventRequest.class))).willReturn(EVENT);

		assertThat(mvc.post().uri("/api/events").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
				.hasStatus(HttpStatus.CREATED)
				.bodyJson()
				.isLenientlyEqualTo("{\"id\":1,\"availableSeats\":10,\"startsAt\":\"2099-01-01T19:00:00Z\"}");
	}

	@Test
	void createRejectsInvalidInputWithFieldErrors() {
		String json = """
				{"name":"","venue":"Main Hall","startsAt":"2000-01-01T19:00:00Z","capacity":0}
				""";

		assertThat(mvc.post().uri("/api/events").with(admin()).contentType(MediaType.APPLICATION_JSON).content(json))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.errors")
				.asMap()
				.containsOnlyKeys("name", "startsAt", "capacity", "priceCents");
	}

	@Test
	void createRequiresAuthentication() {
		assertThat(mvc.post().uri("/api/events").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
				.hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.extractingPath("$.status")
				.isEqualTo(401);
	}

	@Test
	void createIsForbiddenForUsers() {
		assertThat(mvc.post().uri("/api/events").with(user()).contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
				.hasStatus(HttpStatus.FORBIDDEN)
				.bodyJson()
				.extractingPath("$.status")
				.isEqualTo(403);
	}

	@Test
	void getReturnsNotFoundForMissingEvent() {
		given(eventService.get(99L)).willThrow(new NotFoundException("Event not found: 99"));

		assertThat(mvc.get().uri("/api/events/99"))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Event not found: 99");
	}

	@Test
	void updateReturnsConflictWhenCapacityBelowSold() {
		given(eventService.update(eq(1L), any(EventRequest.class)))
				.willThrow(new ConflictException("Capacity can't be lower than the seats already sold"));

		assertThat(mvc.put().uri("/api/events/1").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
				.hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void deleteReturnsNoContent() {
		assertThat(mvc.delete().uri("/api/events/1").with(admin())).hasStatus(HttpStatus.NO_CONTENT);
		then(eventService).should().delete(1L);
	}

	private static JwtRequestPostProcessor admin() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private static JwtRequestPostProcessor user() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

}
