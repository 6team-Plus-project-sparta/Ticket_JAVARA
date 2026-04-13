package com.example.ticket_javara.domain.booking.entity;

import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * ORDER 테이블 엔티티 (예약어 충돌 방지로 테이블명 `orders`)
 * ERD v7.0: order_id, user_id FK, user_coupon_id FK(nullable), total_amount,
 *           discount_amount, final_amount, status, created_at, updated_at
 * ⚠️ @Setter 사용 금지
 */
@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_order_user", columnList = "user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 사용한 쿠폰 (적용 안 했으면 null) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_coupon_id")
    private UserCoupon userCoupon;

    @Column(nullable = false)
    private Integer totalAmount;     // 좌석 원가 합계

    @Column(nullable = false)
    private Integer discountAmount;  // 쿠폰 할인액 (없으면 0)

    @Column(nullable = false)
    private Integer finalAmount;     // 실제 결제 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<Booking> bookings = new ArrayList<>();

    @Builder
    public Order(User user, UserCoupon userCoupon,
                 Integer totalAmount, Integer discountAmount, Integer finalAmount) {
        this.user = user;
        this.userCoupon = userCoupon;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount != null ? discountAmount : 0;
        this.finalAmount = finalAmount;
        this.status = OrderStatus.PENDING;
    }

    // ── 비즈니스 메서드 ──

    /** 주문 확정 */
    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }

    /** 주문 취소 */
    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    /** 결제 실패 */
    public void fail() {
        this.status = OrderStatus.FAILED;
    }
}
