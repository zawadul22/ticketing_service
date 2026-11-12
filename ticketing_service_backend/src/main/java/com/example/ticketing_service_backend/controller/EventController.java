package com.example.ticketing_service_backend.controller;

import java.net.URI;
import java.util.List;

import com.example.ticketing_service_backend.dto.EventRequest;
import com.example.ticketing_service_backend.dto.EventResponse;
import com.example.ticketing_service_backend.service.EventService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    @GetMapping
    public List<EventResponse> list(@RequestParam(defaultValue = "false") boolean includePast,
                                    Authentication authentication) {
        // Check the bound boolean too: Spring also accepts values such as "yes" and "1".
        if (includePast && (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")))) {
            throw new AccessDeniedException("Only admins can list past events");
        }
        return events.list(includePast);
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable long id) {
        return events.get(id);
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
        var response = events.create(request);
        return ResponseEntity.created(URI.create("/api/events/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public EventResponse update(@PathVariable long id, @Valid @RequestBody EventRequest request) {
        return events.update(id, request);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        events.delete(id);
        return ResponseEntity.noContent().build();
    }
}
