package com.example.ticket_javara.domain.event.entity;

/**
 * 이벤트 판매 상태 Enum
 */
public enum EventStatus {
    ON_SALE,    // 판매 중
    SOLD_OUT,   // 매진
    CANCELLED,  // 취소됨
    ENDED,      // 종료됨
    DELETED     // 삭제됨
    }
