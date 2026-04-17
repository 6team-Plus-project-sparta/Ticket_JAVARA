package com.example.ticket_javara.domain.booking.dto.response;

import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.entity.OrderStatus;
import com.example.ticket_javara.domain.booking.entity.Payment;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세 조회 응답 DTO (FN-BK-06)
 *
 * [설계 원칙]
 * DTO 생성자에서 엔티티 연관관계 탐색(getSeat().getSection()...) 금지
 * → LazyInitializationException 위험: 트랜잭션 범위 밖에서 프록시 초기화 실패 가능
 * → 서비스 계층에서 필요한 값을 미리 조립한 후 파라미터로 전달
 *
 * 반환 정보:
 *   - 주문 기본 정보 (orderId, status, 금액)
 *   - 예매 목록 (좌석 정보, 원가, 티켓 코드, 상태)
 *   - 결제 정보 (결제키, 수단, 금액, 결제 시각)
 *   - 쿠폰 사용 정보 (쿠폰명, 할인액)
 *   - 주문 생성 시각
 */
@Getter
public class OrderDetailResponseDto {

    private final Long orderId;
    private final OrderStatus status;
    private final Integer totalAmount;
    private final Integer discountAmount;
    private final Integer finalAmount;
    private final LocalDateTime createdAt;

    /** 예매 목록 (좌석별) */
    private final List<BookingItemDto> bookings;

    /** 결제 정보 (PENDING이면 null) */
    private final PaymentDto payment;

    /** 쿠폰 사용 정보 (쿠폰 미사용이면 null) */
    private final CouponUsedDto couponUsed;

    public OrderDetailResponseDto(Order order, List<BookingItemDto> bookings,
                                  PaymentDto payment, CouponUsedDto couponUsed) {
        this.orderId = order.getOrderId();
        this.status = order.getStatus();
        this.totalAmount = order.getTotalAmount();
        this.discountAmount = order.getDiscountAmount();
        this.finalAmount = order.getFinalAmount();
        this.createdAt = order.getCreatedAt();
        this.bookings = bookings;
        this.payment = payment;
        this.couponUsed = couponUsed;
    }

    // ── 내부 DTO ──

    /**
     * 예매 항목 DTO (좌석별)
     *
     * [변경 이유]
     * 기존: 생성자에서 booking.getSeat().getSection()... 엔티티 탐색
     * → LazyInitializationException 위험 (트랜잭션 범위 밖 프록시 초기화 실패)
     * 변경: 서비스에서 seatInfo 문자열을 미리 조립하여 파라미터로 전달
     */
    @Getter
    public static class BookingItemDto {
        private final Long bookingId;
        private final String seatInfo;       // "A구역 A열 15번" — 서비스에서 조립하여 전달
        private final Integer originalPrice;
        private final String ticketCode;     // CONFIRMED 시 발급, 그 외 null
        private final OrderStatus status;

        public BookingItemDto(Long bookingId, String seatInfo,
                              Integer originalPrice, String ticketCode, OrderStatus status) {
            this.bookingId = bookingId;
            this.seatInfo = seatInfo;
            this.originalPrice = originalPrice;
            this.ticketCode = ticketCode;
            this.status = status;
        }
    }

    /**
     * 결제 정보 DTO
     * Payment 엔티티는 연관관계 탐색 없이 단순 필드만 사용하므로 엔티티 직접 수신
     */
    @Getter
    public static class PaymentDto {
        private final String paymentKey;
        private final String method;
        private final Integer paidAmount;
        private final LocalDateTime paidAt;
        private final LocalDateTime refundedAt;

        public PaymentDto(Payment payment) {
            this.paymentKey = payment.getPaymentKey();
            this.method = payment.getMethod();
            this.paidAmount = payment.getPaidAmount();
            this.paidAt = payment.getPaidAt();
            this.refundedAt = payment.getRefundedAt();
        }
    }

    /**
     * 쿠폰 사용 정보 DTO
     * UserCoupon → Coupon 탐색이 있으므로 서비스에서 JOIN FETCH 후 전달
     */
    @Getter
    public static class CouponUsedDto {
        private final Long userCouponId;
        private final String couponName;
        private final Integer discountAmount;

        public CouponUsedDto(Long userCouponId, String couponName, Integer discountAmount) {
            this.userCouponId = userCouponId;
            this.couponName = couponName;
            this.discountAmount = discountAmount;
        }
    }
}