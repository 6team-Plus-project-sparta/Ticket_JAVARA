package com.example.ticket_javara.domain.booking.dto.response;

import com.example.ticket_javara.domain.booking.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 토스페이먼츠 결제 승인 응답 DTO
 * POST /api/orders/{orderId}/confirm-payment
 */
@Getter
@AllArgsConstructor
public class TossPaymentResponseDto {

    private final Long orderId;
    private final String paymentKey;
    private final OrderStatus status;
}