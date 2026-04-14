package com.example.ticket_javara.domain.booking.entity;

import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.entity.Seat;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BOOKING 테이블 엔티티
 * ERD v7.0: booking_id, order_id FK, user_id FK, seat_id FK, event_id FK,
 *           original_price, ticket_code(UK nullable), status, created_at, updated_at
 * - ORDER_ITEM 역할을 흡수 (original_price 스냅샷 포함)
 * - 예매 확정 시 ticket_code 생성 ("TF-연도-좌석정보-UUID앞4자리")
 * ⚠️ @Setter 사용 금지
 */
@Entity
@Table(name = "booking",
        indexes = {
                @Index(name = "idx_booking_user", columnList = "user_id"),
                @Index(name = "idx_booking_order", columnList = "order_id"),
                @Index(name = "idx_booking_seat_status", columnList = "seat_id, status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long bookingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /** 결제 당시 원가 스냅샷 (역정규화) */
    @Column(nullable = false)
    private Integer originalPrice;

    /** 예매 확정 시 발급 티켓 코드 (예: TF-2026-A015-XYZ1) */
    @Column(unique = true)
    private String ticketCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Builder
    public Booking(Order order, User user, Seat seat, Event event, Integer originalPrice) {
        this.order = order;
        this.user = user;
        this.seat = seat;
        this.event = event;
        this.originalPrice = originalPrice;
        this.status = OrderStatus.PENDING;
    }

    // ── 비즈니스 메서드 ──

    /** 예매 확정 (ticket_code 생성 포함) */
    public void confirm(String ticketCode) {
        this.status = OrderStatus.CONFIRMED;
        this.ticketCode = ticketCode;
    }

    /** 예매 취소 */
    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    /** 결제 실패 */
    public void fail() {
        this.status = OrderStatus.FAILED;
    }
}
