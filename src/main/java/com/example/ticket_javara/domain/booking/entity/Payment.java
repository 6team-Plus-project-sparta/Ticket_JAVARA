package com.example.ticket_javara.domain.booking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PAYMENT 테이블 엔티티
 * ERD v7.0: payment_id, order_id FK, payment_key(UK), method, paid_amount,
 *           status, paid_at, refunded_at(nullable)
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(unique = true, length = 100)
    private String paymentKey;  // PG사 승인 번호

    @Column(length = 30)
    private String method;      // CARD, KAKAO_PAY, NAVER_PAY

    @Column(nullable = false)
    private Integer paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private LocalDateTime paidAt;

    private LocalDateTime refundedAt;

    @Builder
    public Payment(Order order, String paymentKey, String method,
                   Integer paidAmount, PaymentStatus status) {
        this.order = order;
        this.paymentKey = paymentKey;
        this.method = method;
        this.paidAmount = paidAmount;
        this.status = status;
        this.paidAt = LocalDateTime.now();
    }

    /** 환불 처리 */
    public void refund() {
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
    }
}
