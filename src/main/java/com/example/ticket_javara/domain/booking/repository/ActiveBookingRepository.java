package com.example.ticket_javara.domain.booking.repository;

import com.example.ticket_javara.domain.booking.entity.ActiveBooking;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ActiveBooking 레포지토리
 * seat_id가 PK — 중복 확정 DB 레벨 차단
 */
public interface ActiveBookingRepository extends JpaRepository<ActiveBooking, Long> {

    boolean existsBySeatId(Long seatId);
}
