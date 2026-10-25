package com.example.ticketing_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.example.ticketing_service.entity.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	// Fetches the event in the same query, since "my bookings" shows event details for every row
	@EntityGraph(attributePaths = "event")
	List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

	boolean existsByEventId(Long eventId);

	// Atomic check-and-cancel: only one of two simultaneous cancel requests gets 1 back,
	// so the seats can't be returned twice
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Booking b SET b.status = com.example.ticketing_service.entity.BookingStatus.CANCELLED "
			+ "WHERE b.id = :id AND b.status = com.example.ticketing_service.entity.BookingStatus.CONFIRMED")
	int cancelIfConfirmed(Long id);

}
