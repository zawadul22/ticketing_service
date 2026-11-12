package com.example.ticketing_service_backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.example.ticketing_service_backend.entity.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStartTimeAfterOrderByStartTimeAsc(Instant time);

    List<Event> findAllByOrderByStartTimeAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from Event event where event.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Event event set event.availableSeats = event.availableSeats - :quantity
            where event.id = :id and event.availableSeats >= :quantity and event.startTime > :now
            """)
    int reserveSeats(@Param("id") Long id, @Param("quantity") int quantity, @Param("now") Instant now);
}
