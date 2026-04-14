package com.example.ticket_javara.domain.booking.entity;

/**
 * 주문/예매 상태 Enum
 */
public enum OrderStatus {
    PENDING,     // 결제 대기 중
    CONFIRMED,   // 결제 완료 (예매 확정)
    CANCELLED,   // 취소됨
    FAILED       // 결제 실패
}
