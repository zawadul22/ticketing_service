package com.example.ticketing_service.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.example.ticketing_service.entity.Event;

public interface EventRepository extends JpaRepository<Event, Long> {

	List<Event> findByStartsAtAfterOrderByStartsAtAsc(Instant now);

	// Atomic check-and-decrement: returns 0 when there aren't enough seats, so concurrent bookings can't oversell.
	// Bulk updates bypass the persistence context, so flush pending changes first and clear stale entities after.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Event e SET e.availableSeats = e.availableSeats - :quantity WHERE e.id = :id AND e.availableSeats >= :quantity")
	int decrementAvailableSeats(Long id, int quantity);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Event e SET e.availableSeats = e.availableSeats + :quantity WHERE e.id = :id")
	int incrementAvailableSeats(Long id, int quantity);

}
