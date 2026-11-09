package com.example.ticketing_service_backend.repository;

import java.time.Instant;
import java.util.List;

import com.example.ticketing_service_backend.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStartTimeAfterOrderByStartTimeAsc(Instant time);

    List<Event> findAllByOrderByStartTimeAsc();
}
