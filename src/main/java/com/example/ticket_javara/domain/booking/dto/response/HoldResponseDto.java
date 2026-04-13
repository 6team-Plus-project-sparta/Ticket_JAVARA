package com.example.ticket_javara.domain.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Hold 획득 성공 응답 DTO (FN-SEAT-02)
 * holdToken: UUID — 주문 생성 시 holdToken:{uuid} 역조회 키로 사용
 * expiresAt: Hold 만료 시각 (KST 기준 5분 후)
 */
@Getter
@AllArgsConstructor
public class HoldResponseDto {

    private String holdToken;
    private Long seatId;
    private LocalDateTime expiresAt;
}
