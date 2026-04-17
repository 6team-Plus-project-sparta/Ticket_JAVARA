package com.example.ticket_javara.domain.booking.dto.response;

import com.example.ticket_javara.domain.booking.entity.Booking;
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
 * 반환 정보:
 *   - 주문 기본 정보 (orderId, status, 금액)
 *   - 예매 목록 (좌석 정보, 원가, 티켓 코드, 상태)
 *   - 결제 정보 (결제키, 수단, 금액, 결제 시각)
 *   - 쿠폰 사용 정보 (쿠폰명, 할인액)
 *   - 주문 생성 시각
 *
 * 결제 후 PENDING → CONFIRMED 폴링 용도로도 사용 가능 (FN-BK-06 비고)
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
     */
    @Getter
    public static class BookingItemDto {
        private final Long bookingId;
        private final String seatInfo;       // "A구역 A열 15번"
        private final Integer originalPrice;
        private final String ticketCode;     // CONFIRMED 시 발급, 그 외 null
        private final OrderStatus status;

        public BookingItemDto(Booking booking) {
            this.bookingId = booking.getBookingId();
            this.seatInfo = booking.getSeat().getSection().getSectionName()
                    + " " + booking.getSeat().getRowName()
                    + " " + booking.getSeat().getColNum() + "번";
            this.originalPrice = booking.getOriginalPrice();
            this.ticketCode = booking.getTicketCode();
            this.status = booking.getStatus();
        }
    }

    /**
     * 결제 정보 DTO
     */
    @Getter
    public static class PaymentDto {
        private final String paymentKey;
        private final String method;
        private final Integer paidAmount;
        private final LocalDateTime paidAt;
        private final LocalDateTime refundedAt;  // 취소 시 환불 시각

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
     */
    @Getter
    public static class CouponUsedDto {
        private final Long userCouponId;
        private final String couponName;
        private final Integer discountAmount;

        public CouponUsedDto(UserCoupon userCoupon) {
            this.userCouponId = userCoupon.getUserCouponId();
            this.couponName = userCoupon.getCoupon().getName();
            this.discountAmount = userCoupon.getCoupon().getDiscountAmount();
        }
    }
}