package com.example.ticketing_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketing_service.dto.EventRequest;
import com.example.ticketing_service.dto.EventResponse;
import com.example.ticketing_service.service.EventService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

	private final EventService eventService;

	// Anyone can list upcoming events; including past ones is for the admin page only
	@GetMapping
	@PreAuthorize("!#includePast or hasRole('ADMIN')")
	public List<EventResponse> list(@RequestParam(defaultValue = "false") boolean includePast) {
		return eventService.list(includePast);
	}

	@GetMapping("/{id}")
	public EventResponse get(@PathVariable Long id) {
		return eventService.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public EventResponse create(@Valid @RequestBody EventRequest request) {
		return eventService.create(request);
	}

	@PutMapping("/{id}")
	public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
		return eventService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		eventService.delete(id);
	}

}
