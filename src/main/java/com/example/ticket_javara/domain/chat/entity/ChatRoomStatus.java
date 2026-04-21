package com.example.ticket_javara.domain.chat.entity;

/**
 * CS 문의 채팅방 상태 Enum
 * 전이 규칙: WAITING → IN_PROGRESS → COMPLETED (역방향 불가)
 */
public enum ChatRoomStatus {
    WAITING,      // 대기중 (고객이 문의 접수, 아직 관리자가 확인 전)
    IN_PROGRESS,  // 처리중 (관리자가 응대 시작)
    COMPLETED     // 완료 (문의 해결)
}
