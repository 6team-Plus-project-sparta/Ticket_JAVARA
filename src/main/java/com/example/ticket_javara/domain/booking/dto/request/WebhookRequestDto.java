package com.example.ticket_javara.domain.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Mock PG 웹훅 수신 요청 DTO (FN-BK-02)
 * Mock PG → POST /api/mock-pg/webhook 호출 시 전달되는 페이로드
 */
@Getter
@NoArgsConstructor
public class WebhookRequestDto {

    @NotNull
    private Long orderId;

    /** "SUCCESS" 또는 "FAIL" */
    @NotNull
    private String paymentStatus;

    /** PG 결제키 (성공 시 존재, 실패 시 null) */
    private String paymentKey;

    /** 실제 결제 금액 (실패 시 0) */
    private Integer paidAmount;
}
