package com.example.ticket_javara.domain.booking.repository;

import com.example.ticket_javara.domain.booking.entity.ActiveBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * ACTIVE_BOOKING 레포지토리
 * - seat_id가 PK이므로 existsBySeatId()로 CONFIRMED 여부를 O(1)로 확인
 * - INSERT 중복 시 DataIntegrityViolationException → GlobalExceptionHandler에서 409 처리
 */
public interface ActiveBookingRepository extends JpaRepository<ActiveBooking, Long> {

    /** CONFIRMED 여부 확인 — Hold 전 분산락 내부에서 호출 */
    boolean existsBySeatId(Long seatId);

    /** 취소 시 좌석 해제 (AVAILABLE 복귀) */
    void deleteBySeatId(Long seatId);

    /** 비관적 락으로 특정 좌석들을 잠금 (취소 트랜잭션 충돌 방지, C-03 대응) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ab FROM ActiveBooking ab WHERE ab.seatId IN :seatIds")
    List<ActiveBooking> findBySeatIdInWithLock(@Param("seatIds") List<Long> seatIds);

    /** 주문 내 예약 전체 조회 (웹훅 처리 시) */
    @Query("SELECT ab FROM ActiveBooking ab WHERE ab.booking.order.orderId = :orderId")
    Optional<ActiveBooking> findByBookingOrderOrderId(@Param("orderId") Long orderId);
}
