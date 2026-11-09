package com.example.ticketing_service_backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "events", check = @CheckConstraint(name = "ck_events_capacity_and_price", constraint =
        "capacity > 0 and available_seats >= 0 and available_seats <= capacity and ticket_price_cents >= 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    @Setter
    private String name;

    @Column(columnDefinition = "text")
    @Setter
    private String description;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    @Setter
    private String venue;

    @NotNull
    @Column(name = "start_time", nullable = false, columnDefinition = "timestamp with time zone")
    @Setter
    private Instant startTime;

    @NotNull
    @Positive
    @Column(nullable = false)
    @Setter
    private Integer capacity;

    @NotNull
    @PositiveOrZero
    @Column(name = "available_seats", nullable = false)
    @Setter
    private Integer availableSeats;

    @NotNull
    @PositiveOrZero
    @Column(name = "ticket_price_cents", nullable = false)
    @Setter
    private Long ticketPriceCents;

    public Event(String name, String description, String venue, Instant startTime,
                 Integer capacity, Long ticketPriceCents) {
        this.name = name;
        this.description = description;
        this.venue = venue;
        this.startTime = startTime;
        this.capacity = capacity;
        this.availableSeats = capacity;
        this.ticketPriceCents = ticketPriceCents;
    }
}
