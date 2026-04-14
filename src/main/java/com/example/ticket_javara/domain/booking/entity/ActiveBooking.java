package com.example.ticket_javara.domain.booking.entity;

import com.example.ticket_javara.domain.event.entity.Seat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ACTIVE_BOOKING 테이블 엔티티
 * ERD v7.0: seat_id PK, booking_id UK
 *
 * 역할:
 * - 확정된 예약만 보관 (BOOKING.status=CONFIRMED인 행의 subset)
 * - seat_id = PRIMARY KEY → 동일 좌석에 두 번째 CONFIRMED 행 삽입 시 DB 레벨 차단
 * - Redis 분산락이 1차 방어선, 이 테이블이 2차 방어선
 *
 * 좌석 상태 판단:
 *   1. ACTIVE_BOOKING에 seat_id 존재 → CONFIRMED
 *   2. Redis hold:{eventId}:{seatId} 키 존재 → ON_HOLD
 *   3. 그 외 → AVAILABLE
 */
@Entity
@Table(name = "active_booking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActiveBooking {

    /** seat_id가 PK — 좌석당 1건만 허용 (중복 확정 물리적 차단) */
    @Id
    @Column(name = "seat_id")
    private Long seatId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId  // seat_id를 PK로 공유
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Builder
    public ActiveBooking(Seat seat, Booking booking) {
        this.seat = seat;
        this.seatId = seat.getSeatId();
        this.booking = booking;
    }
}
