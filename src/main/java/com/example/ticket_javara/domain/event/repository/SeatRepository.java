package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Seat 레포지토리
 */
public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * 섹션별 좌석 조회
     */
    List<Seat> findBySectionSectionId(Long sectionId);

    /**
     * 이벤트의 좌석 중 CONFIRMED 아닌(ACTIVE_BOOKING 없는) 좌석 수 조회
     * FN-SEAT-01: 잔여 좌석 수 계산
     */
    @Query("SELECT COUNT(s) FROM Seat s " +
           "WHERE s.section.event.eventId = :eventId " +
           "AND NOT EXISTS (SELECT 1 FROM ActiveBooking ab WHERE ab.seatId = s.seatId)")
    long countAvailableSeatsByEventId(@Param("eventId") Long eventId);

    @Query("SELECT COUNT(s) FROM Seat s " +
           "WHERE s.section.sectionId = :sectionId " +
           "AND NOT EXISTS (SELECT 1 FROM ActiveBooking ab WHERE ab.seatId = s.seatId)")
    long countAvailableSeatsBySectionId(@Param("sectionId") Long sectionId);
}
