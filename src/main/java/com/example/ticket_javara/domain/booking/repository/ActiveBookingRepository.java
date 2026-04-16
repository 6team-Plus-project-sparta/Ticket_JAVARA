package com.example.ticket_javara.domain.booking.repository;

import com.example.ticket_javara.domain.booking.entity.ActiveBooking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 단일 좌석 취소 시 삭제 (단건 처리용)
     * 여러 좌석 일괄 삭제 시에는 deleteAllBySeatIdIn() 사용
     */
    void deleteBySeatId(Long seatId);

    /**
     * ⭐ 벌크 DELETE — N+1 방지 (취소 시 사용)
     * DELETE FROM active_booking WHERE seat_id IN (...)
     * 개별 deleteBySeatId() 루프 대신 단일 쿼리로 처리
     *
     * @Modifying 필수: UPDATE/DELETE 쿼리에 반드시 추가
     * clearAutomatically = true: 영속성 컨텍스트 캐시와 DB 불일치 방지
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ActiveBooking ab WHERE ab.seatId IN :seatIds")
    void deleteAllBySeatIdIn(@Param("seatIds") List<Long> seatIds);

    /** 비관적 락으로 특정 좌석들을 잠금 (취소 트랜잭션 충돌 방지, C-03 대응) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ab FROM ActiveBooking ab WHERE ab.seatId IN :seatIds")
    List<ActiveBooking> findBySeatIdInWithLock(@Param("seatIds") List<Long> seatIds);

    /** 주문 내 예약 전체 조회 (웹훅 처리 시) */
    @Query("SELECT ab FROM ActiveBooking ab WHERE ab.booking.order.orderId = :orderId")
    Optional<ActiveBooking> findByBookingOrderOrderId(@Param("orderId") Long orderId);
}