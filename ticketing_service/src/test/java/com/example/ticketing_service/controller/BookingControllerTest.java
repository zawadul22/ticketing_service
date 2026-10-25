package com.example.ticketing_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.example.ticketing_service.dto.BookingResponse;
import com.example.ticketing_service.entity.BookingStatus;
import com.example.ticketing_service.exception.ForbiddenException;
import com.example.ticketing_service.exception.NotEnoughSeatsException;
import com.example.ticketing_service.service.BookingService;

@WebMvcTest(BookingController.class)
@Import(SecurityConfig.class)
class BookingControllerTest {

	private static final BookingResponse BOOKING = new BookingResponse(5L, 2, BookingStatus.CONFIRMED,
			Instant.parse("2026-10-25T10:00:00Z"), 1L, "Concert", "Main Hall", Instant.parse("2099-01-01T19:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private BookingService bookingService;

	@Test
	void bookUsesUserIdFromToken() {
		given(bookingService.book(7L, 1L, 2)).willReturn(BOOKING);

		assertThat(book("{\"quantity\":2}").with(user("7")))
				.hasStatus(HttpStatus.CREATED)
				.bodyJson()
				.isLenientlyEqualTo("{\"id\":5,\"quantity\":2,\"status\":\"CONFIRMED\",\"eventName\":\"Concert\"}");
		then(bookingService).should().book(7L, 1L, 2);
	}

	@Test
	void bookRequiresAuthentication() {
		assertThat(book("{\"quantity\":2}")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void adminCannotBook() {
		assertThat(book("{\"quantity\":2}").with(admin())).hasStatus(HttpStatus.FORBIDDEN);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"quantity\":0}", "{\"quantity\":11}", "{}" })
	void bookRejectsInvalidQuantity(String json) {
		assertThat(book(json).with(user("7")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.hasPathSatisfying("$.errors.quantity", value -> assertThat(value).isNotNull());
	}

	@Test
	void bookReturnsConflictWhenSoldOut() {
		given(bookingService.book(7L, 1L, 2)).willThrow(new NotEnoughSeatsException());

		assertThat(book("{\"quantity\":2}").with(user("7")))
				.hasStatus(HttpStatus.CONFLICT)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Not enough seats available");
	}

	@Test
	void listMineReturnsCallersBookings() {
		given(bookingService.listMine(7L)).willReturn(List.of(BOOKING));

		assertThat(mvc.get().uri("/api/bookings/me").with(user("7")))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$[0].id")
				.isEqualTo(5);
	}

	@Test
	void listMineRequiresAuthentication() {
		assertThat(mvc.get().uri("/api/bookings/me")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void cancelReturnsForbiddenForSomeoneElsesBooking() {
		given(bookingService.cancel(7L, 5L)).willThrow(new ForbiddenException("You can only cancel your own bookings"));

		assertThat(mvc.post().uri("/api/bookings/5/cancel").with(user("7")))
				.hasStatus(HttpStatus.FORBIDDEN)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("You can only cancel your own bookings");
	}

	private MockMvcTester.MockMvcRequestBuilder book(String json) {
		return mvc.post().uri("/api/events/1/bookings").contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private static JwtRequestPostProcessor user(String id) {
		return jwt().jwt(token -> token.subject(id)).authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private static JwtRequestPostProcessor admin() {
		return jwt().jwt(token -> token.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

}
