package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.entity.EventCategory;
import com.example.ticket_javara.domain.event.entity.EventStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Event 레포지토리
 * 이벤트 상세 조회에 Caffeine 캐시 적용 (TTL 10분) — CLAUDE.md §캐싱 전략
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    @Deprecated // DELETED 이벤트 포함됨 — 직접 호출 금지, WithVenueAndSections 계열 사용
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    /**
     * 이벤트 상세 조회 — Caffeine 캐시 적용 (TTL 10분)
     * 이벤트 수정 시 @CacheEvict 필요
     */
    @Cacheable(value = "event-detail", key = "#eventId")
    Optional<Event> findById(Long eventId);//findCachedById;

    // 관리자 API 전용 — 캐시 없이 항상 DB에서 직접 조회
    @Query("SELECT e FROM Event e WHERE e.eventId = :eventId")
    Optional<Event> findByIdForUpdate(@Param("eventId") Long eventId);

    @Deprecated // DELETED 이벤트 포함됨 — 직접 호출 금지
    Page<Event> findByCategory(EventCategory category, Pageable pageable);
    @Deprecated // DELETED 이벤트 포함됨 — 직접 호출 금지
    Page<Event> findByCategoryAndStatus(EventCategory category, EventStatus status, Pageable pageable);

    @Query("SELECT e FROM Event e " +
            "JOIN FETCH e.venue " +
            "JOIN FETCH e.sections " +
            "WHERE e.eventId = :eventId")
    Optional<Event> findByIdWithVenueAndSections(@Param("eventId") Long eventId);


    //DELETED 제외
    @Query("SELECT e FROM Event e " +
            "JOIN FETCH e.venue " +
            "JOIN FETCH e.sections " +
            "WHERE e.eventId = :eventId " +
            "AND e.status != com.example.ticket_javara.domain.event.entity.EventStatus.DELETED")
    Optional<Event> findByIdWithVenueAndSectionsExcludeDeleted(@Param("eventId") Long eventId);

    @Deprecated // DELETED 이벤트 포함됨 — findByIdWithVenueExcludeDeleted() 사용
    @Query("SELECT e FROM Event e JOIN FETCH e.venue WHERE e.eventId = :eventId")
    Optional<Event> findByIdWithVenue(@Param("eventId") Long eventId);

    // 신규 추가
    @Query("SELECT e FROM Event e JOIN FETCH e.venue " +
            "WHERE e.eventId = :eventId " +
            "AND e.status != com.example.ticket_javara.domain.event.entity.EventStatus.DELETED")
    Optional<Event> findByIdWithVenueExcludeDeleted(@Param("eventId") Long eventId);

    // eventDate가 현재 시각보다 이전이고 ON_SALE 또는 SOLD_OUT인 이벤트를 ENDED로 일괄 전환
    @Modifying
    @Query("""
        UPDATE Event e
        SET e.status = :endedStatus
        WHERE e.eventDate < :now
        AND e.status IN (
            com.example.ticket_javara.domain.event.entity.EventStatus.ON_SALE,
            com.example.ticket_javara.domain.event.entity.EventStatus.SOLD_OUT
        )
    """)
    int bulkUpdateEndedStatus(
            @Param("now") LocalDateTime now,
            @Param("endedStatus") EventStatus endedStatus
    );

    // HHH90003004문제발생하여 sections JOIN FETCH 제거
    @Query(
            value = "SELECT e FROM Event e JOIN FETCH e.venue "+
            "WHERE e.status != com.example.ticket_javara.domain.event.entity.EventStatus.DELETED",
            countQuery = "SELECT COUNT(e) FROM Event e "+
                    "WHERE e.status != com.example.ticket_javara.domain.event.entity.EventStatus.DELETED"
    )
    Page<Event> findAllWithVenueAndSections(Pageable pageable);

    @Query(
            value = "SELECT e FROM Event e JOIN FETCH e.venue WHERE e.category = :category "+
                    "AND e.status != com.example.ticket_javara.domain.event.entity.EventStatus.DELETED",
            countQuery = "SELECT COUNT(e) FROM Event e WHERE e.category = :category "+
                    "AND e.status != com.example.ticket_javara.domain.event.entity.EventStatus.DELETED"
    )
    Page<Event> findByCategoryWithVenueAndSections(@Param("category") EventCategory category, Pageable pageable);

    @Query(
            value = "SELECT e FROM Event e JOIN FETCH e.venue " +
                    "WHERE e.status = :status " +
                    "AND e.status != com.example.ticket_javara.domain.event.entity.EventStatus.DELETED",
            countQuery = "SELECT COUNT(e) FROM Event e " +
                    "WHERE e.status = :status " +
                    "AND e.status != com.example.ticket_javara.domain.event.entity.EventStatus.DELETED"
    )
    Page<Event> findByStatusWithVenueAndSections(@Param("status") EventStatus status, Pageable pageable);

    @Query(
            value = "SELECT e FROM Event e JOIN FETCH e.venue " +
                    "WHERE e.category = :category AND e.status = :status " +
                    "AND e.status != com.example.ticket_javara.domain.event.entity.EventStatus.DELETED",
            countQuery = "SELECT COUNT(e) FROM Event e " +
                    "WHERE e.category = :category AND e.status = :status " +
                    "AND e.status != com.example.ticket_javara.domain.event.entity.EventStatus.DELETED"
    )
    Page<Event> findByCategoryAndStatusWithVenueAndSections(
            @Param("category") EventCategory category,
            @Param("status") EventStatus status,
            Pageable pageable
    );
}
