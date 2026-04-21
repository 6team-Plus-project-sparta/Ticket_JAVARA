package com.example.ticket_javara.domain.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 토스페이먼츠 결제 승인 요청 DTO
 * POST /api/orders/{orderId}/confirm-payment
 *
 * 프론트에서 토스 결제창 완료 후 전달하는 페이로드
 */
@Getter
@NoArgsConstructor
public class TossPaymentRequestDto {

    /** 토스페이먼츠 결제키 (결제창 완료 시 발급) */
    @NotBlank(message = "paymentKey는 필수입니다.")
    private String paymentKey;
    private String tossOrderId;

    /** 실제 결제 금액 (위변조 방지 검증용) */
    @NotNull(message = "amount는 필수입니다.")
    @Positive(message = "amount는 양수여야 합니다.")
    private Integer amount;
}