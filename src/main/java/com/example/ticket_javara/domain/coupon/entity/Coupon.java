package com.example.ticket_javara.domain.coupon.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * COUPON 테이블 엔티티
 * ERD v7.0: coupon_id, name, discount_amount, total_quantity, remaining_quantity,
 *           start_at, expired_at
 * - remaining_quantity: Redis 유실 대비 Source of Truth (MySQL 기준)
 * - Redis coupon:stock:{couponId} 키와 이중 관리
 * ⚠️ @Setter 사용 금지
 */
@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends com.example.ticket_javara.global.common.BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long couponId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer discountAmount;

    @Column(nullable = false)
    private Integer totalQuantity;

    /** Redis 유실 대비 MySQL Source of Truth */
    @Column(nullable = false)
    private Integer remainingQuantity;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Column(length = 500)
    private String imageUrl;

    @Builder
    public Coupon(String name, Integer discountAmount, Integer totalQuantity,
                  LocalDateTime startAt, LocalDateTime expiredAt, String imageUrl) {
        this.name = name;
        this.discountAmount = discountAmount;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.startAt = startAt;
        this.expiredAt = expiredAt;
        this.imageUrl = imageUrl;
    }

    // ── 비즈니스 메서드 ──

    /**
     * MySQL remaining_quantity 차감
     * Redis DECR 성공 직후 호출하여 동기화
     */
    public void decreaseRemainingQuantity() {
        if (this.remainingQuantity <= 0) {
            throw new IllegalStateException("쿠폰 수량이 부족합니다.");
        }
        this.remainingQuantity--;
    }

    /** 발급 가능 여부 확인 */
    public boolean isIssuable(LocalDateTime now) {
        return !now.isBefore(startAt) && !now.isAfter(expiredAt) && remainingQuantity > 0;
    }

    /** 발급 시작 전 여부 */
    public boolean isNotStarted(LocalDateTime now) {
        return now.isBefore(startAt);
    }

    /** 쿠폰 발급 만료 여부 */
    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiredAt);
    }
}
