package com.example.ticketing_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ticketing_service.entity.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	// Fetches the event in the same query, since "my bookings" shows event details for every row
	@EntityGraph(attributePaths = "event")
	List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

	boolean existsByEventId(Long eventId);

}
