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

    List<Booking> findByOrderOrderId(Long orderId);

    /** 비관적 락으로 Booking 조회 (수동 확정, 취소 충돌 방지) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.bookingId = :bookingId")
    Optional<Booking> findByIdWithLock(@Param("bookingId") Long bookingId);
}
