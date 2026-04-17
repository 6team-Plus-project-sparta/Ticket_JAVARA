package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.entity.EventCategory;
import com.example.ticket_javara.domain.event.entity.EventStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Event 레포지토리
 * 이벤트 상세 조회에 Caffeine 캐시 적용 (TTL 10분) — CLAUDE.md §캐싱 전략
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    /**
     * 이벤트 상세 조회 — Caffeine 캐시 적용 (TTL 10분)
     * 이벤트 수정 시 @CacheEvict 필요
     */
    @Cacheable(value = "event-detail", key = "#eventId")
    Optional<Event> findById(Long eventId);//findCachedById;

    Page<Event> findByCategory(EventCategory category, Pageable pageable);

    Page<Event> findByCategoryAndStatus(EventCategory category, EventStatus status, Pageable pageable);

    @Query("SELECT e FROM Event e " +
            "JOIN FETCH e.venue " +
            "JOIN FETCH e.sections " +
            "WHERE e.eventId = :eventId")
    Optional<Event> findByIdWithVenueAndSections(@Param("eventId") Long eventId);

    @Query("SELECT e FROM Event e JOIN FETCH e.venue WHERE e.eventId = :eventId")
    Optional<Event> findByIdWithVenue(@Param("eventId") Long eventId);
}
