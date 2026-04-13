package com.example.ticket_javara.domain.booking.repository;

import com.example.ticket_javara.domain.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Booking 레포지토리
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * 주문 내 예약 목록 조회 (Seat, Section, Event JOIN FETCH — N+1 방지)
     * 웹훅 처리 시 holdKey 구성을 위해 eventId, seatId 필요
     */
    @Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.seat s " +
           "JOIN FETCH s.section sec " +
           "JOIN FETCH sec.event " +
           "WHERE b.order.orderId = :orderId")
    List<Booking> findByOrderOrderId(@Param("orderId") Long orderId);

    /**
     * 주문 내 예약 목록 비관적 락 조회 (취소/확정 충돌 방지 — C-03 대응)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.order.orderId = :orderId")
    List<Booking> findByOrderOrderIdWithLock(@Param("orderId") Long orderId);

    /** 비관적 락으로 단일 Booking 조회 (수동 확정, 취소 충돌 방지) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.bookingId = :bookingId")
    Optional<Booking> findByIdWithLock(@Param("bookingId") Long bookingId);

    /** 내 예매 내역 조회 (userId 기준, BOOKING.user_id 역정규화 활용) */
    List<Booking> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    /** 발급된 쿠폰 ID로 Booking 수 조회 (동시성 테스트 검증용) */
    long countByOrderUserCouponUserCouponId(Long userCouponId);
}
