package com.example.ticketing_service_backend.repository;

import java.util.List;

import com.example.ticketing_service_backend.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByEventId(Long eventId);
}
