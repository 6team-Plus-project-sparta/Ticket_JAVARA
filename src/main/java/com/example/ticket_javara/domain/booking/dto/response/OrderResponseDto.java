package com.example.ticket_javara.domain.booking.dto.response;

import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.entity.OrderStatus;
import lombok.Getter;

import java.util.List;

/**
 * 주문 생성 응답 DTO (FN-BK-01)
 * 주문이 생성된 직후 PENDING 상태로 반환
 */
@Getter
public class OrderResponseDto {

    private final Long orderId;
    private final OrderStatus status;
    private final Integer totalAmount;
    private final Integer discountAmount;
    private final Integer finalAmount;
    /** Mock PG 연동 식별자 (예: PG-20260413-{orderId}) */
    private final String pgRequestId;

    /** 각 좌석별 요약 정보 */
    private final List<BookingItemDto> items;

    public OrderResponseDto(Order order, List<BookingItemDto> items) {
        this.orderId = order.getOrderId();
        this.status = order.getStatus();
        this.totalAmount = order.getTotalAmount();
        this.discountAmount = order.getDiscountAmount();
        this.finalAmount = order.getFinalAmount();
        this.pgRequestId = "PG-" + java.time.LocalDate.now()
                .toString().replace("-", "") + "-" + order.getOrderId();
        this.items = items;
    }

    @Getter
    public static class BookingItemDto {
        private final Long seatId;
        private final String seatInfo;  // "A구역 A열 15번"
        private final Integer originalPrice;

        public BookingItemDto(Long seatId, String seatInfo, Integer originalPrice) {
            this.seatId = seatId;
            this.seatInfo = seatInfo;
            this.originalPrice = originalPrice;
        }
    }
}
