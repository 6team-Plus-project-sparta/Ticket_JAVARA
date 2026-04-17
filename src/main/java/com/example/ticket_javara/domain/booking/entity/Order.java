package com.example.ticket_javara.domain.booking.entity;

import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.global.common.BaseTimeEntity;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
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

    /**
     * 주문 소유자 검증 (도메인 메서드)
     * 조회, 취소 등 여러 곳에서 재사용 — 에러코드를 파라미터로 받아 중복 코드 제거
     *
     * 사용 예:
     *   order.validateOwner(userId, ErrorCode.ORDER_NOT_OWNED);   // 조회
     *   order.validateOwner(userId, ErrorCode.CANCEL_NOT_OWNED);  // 취소
     *
     * [null 방어]
     * DB 스키마상 user_id는 NOT NULL이므로 this.user가 null일 수 없으나,
     * 방어적 프로그래밍 원칙에 따라 null 체크를 추가하여 NPE로 인한 500 에러 방지
     *
     * @param userId    JWT에서 추출한 현재 사용자 ID
     * @param errorCode 검증 실패 시 던질 에러코드
     * @throws ForbiddenException 본인 주문이 아닌 경우
     */
    public void validateOwner(Long userId, ErrorCode errorCode) {
        if (this.user == null || !this.user.getUserId().equals(userId)) {
            throw new ForbiddenException(errorCode);
        }
    }

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