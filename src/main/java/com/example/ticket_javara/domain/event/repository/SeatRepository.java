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

    //이벤트 ID로 전체 좌석 조회
    @Query("""
        SELECT s FROM Seat s
        JOIN FETCH s.section sec
        WHERE sec.event.eventId = :eventId
        ORDER BY sec.sectionId, s.rowName, s.colNum
    """)
    List<Seat> findAllByEventIdWithSection(@Param("eventId") Long eventId);

    //특정 섹션의 좌석 조회 (sectionId 필터링 시 사용)
    @Query("""
        SELECT s FROM Seat s
        JOIN FETCH s.section sec
        WHERE sec.sectionId = :sectionId
        ORDER BY s.rowName, s.colNum
    """)
    List<Seat> findAllBySectionIdWithSection(@Param("sectionId") Long sectionId);

    // 특정 이벤트의 CONFIRMED 좌석 ID 목록 조회 Seat.status 없으므로 ACTIVE_BOOKING 존재 여부로 판단
    @Query("""
        SELECT ab.seatId
        FROM com.example.ticket_javara.domain.booking.entity.ActiveBooking ab
        WHERE ab.seat.section.event.eventId = :eventId
    """)
    List<Long> findConfirmedSeatIdsByEventId(@Param("eventId") Long eventId);


}
