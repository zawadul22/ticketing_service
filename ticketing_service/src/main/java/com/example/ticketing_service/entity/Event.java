package com.example.ticketing_service.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "text")
	private String description;

	@Column(nullable = false)
	private String venue;

	@Column(nullable = false)
	private Instant startsAt;

	// capacity and availableSeats are set on insert, then changed only by the atomic UPDATE queries in
	// EventRepository. updatable = false stops entity saves from writing back stale values over concurrent bookings.
	@Column(nullable = false, updatable = false)
	private int capacity;

	@Column(nullable = false, updatable = false)
	private int availableSeats;

	@Column(nullable = false)
	private int priceCents;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

}
