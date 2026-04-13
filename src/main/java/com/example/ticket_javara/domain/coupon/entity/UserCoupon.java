package com.example.ticket_javara.domain.coupon.entity;

import com.example.ticket_javara.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * USER_COUPON 테이블 엔티티
 * ERD v7.0: user_coupon_id, user_id FK, coupon_id FK, status, issued_at, used_at(nullable), version
 * - UNIQUE(user_id, coupon_id): 동일 쿠폰 중복 발급 방지 (DB 레벨)
 * - @Version: 낙관적 락으로 중복 사용 방지
 * ⚠️ @Setter 사용 금지
 */
@Entity
@Table(name = "user_coupon",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_coupon",
                        columnNames = {"user_id", "coupon_id"}
                )
        },
        indexes = {
                @Index(name = "idx_user_coupon_composite", columnList = "coupon_id, user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserCouponStatus status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime usedAt;

    /** 낙관적 락 — 중복 사용 방지 */
    @Version
    private Long version;

    @Builder
    public UserCoupon(User user, Coupon coupon) {
        this.user = user;
        this.coupon = coupon;
        this.status = UserCouponStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
    }

    // ── 비즈니스 메서드 ──

    /** 쿠폰 사용 처리 */
    public void use() {
        if (this.status != UserCouponStatus.ISSUED) {
            throw new IllegalStateException("이미 사용된 쿠폰입니다.");
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    /** 쿠폰 사용 취소 (예매 취소 시 복원) */
    public void restore() {
        this.status = UserCouponStatus.ISSUED;
        this.usedAt = null;
    }

    /** 사용 가능 여부 */
    public boolean isUsable() {
        return UserCouponStatus.ISSUED.equals(this.status);
    }
}
