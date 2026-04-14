package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Venue 레포지토리
 * venue_id=1로 고정 (공연장 CRUD는 Out of Scope)
 */
public interface VenueRepository extends JpaRepository<Venue, Long> {
}
