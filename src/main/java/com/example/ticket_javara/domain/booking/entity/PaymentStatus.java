package com.example.ticket_javara.domain.booking.entity;

/**
 * 결제 상태 Enum
 */
public enum PaymentStatus {
    SUCCESS,   // 결제 성공
    FAILED,    // 결제 실패
    REFUNDED   // 환불 완료
}
