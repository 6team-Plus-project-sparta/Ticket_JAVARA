package com.example.ticket_javara.domain.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Mock PG 웹훅 수신 요청 DTO (FN-BK-02)
 * Mock PG → POST /api/mock-pg/webhook 호출 시 전달되는 페이로드
 *
 * [정적 팩토리 메서드]
 * TossPaymentService에서 BookingConfirmService 재사용 시
 * WebhookRequestDto를 직접 생성하기 위한 of() 메서드 제공
 */
@Getter
@NoArgsConstructor
public class WebhookRequestDto {

    @NotNull
    private Long orderId;

    /** "SUCCESS" 또는 "FAIL" */
    @NotNull
    private WebhookPaymentStatus paymentStatus;

    /** PG 결제키 (성공 시 존재, 실패 시 null) */
    private String paymentKey;

    /** 실제 결제 금액 (실패 시 0) */
    private Integer paidAmount;

    /**
     * 토스페이먼츠 승인 결과로 WebhookRequestDto 생성 (정적 팩토리)
     * TossPaymentService에서 BookingConfirmService 재사용 시 사용
     */
    public static WebhookRequestDto ofTossSuccess(Long orderId, String paymentKey, Integer paidAmount) {
        WebhookRequestDto dto = new WebhookRequestDto();
        dto.orderId = orderId;
        dto.paymentStatus = WebhookPaymentStatus.SUCCESS;
        dto.paymentKey = paymentKey;
        dto.paidAmount = paidAmount;
        return dto;
    }
}