package com.example.ticketing_service_backend.repository;

import java.util.List;
import java.util.Optional;

import com.example.ticketing_service_backend.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    @EntityGraph(attributePaths = "event")
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByEventId(Long eventId);

    @Query("select booking.event.id from Booking booking where booking.id = :id")
    Optional<Long> findEventIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
}
