package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.entity.EventStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
    Optional<Event> findCachedById(Long eventId);
}
