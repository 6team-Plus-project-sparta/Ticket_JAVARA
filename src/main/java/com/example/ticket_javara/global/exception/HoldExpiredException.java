package com.example.ticket_javara.global.exception;

/**
 * 좌석 Hold TTL 만료 예외 (S007)
 * 결제 웹훅 수신 시점에 Redis TTL이 만료된 경우 발생 (Race Condition 처리)
 */
public class HoldExpiredException extends BusinessException {
    public HoldExpiredException() {
        super(ErrorCode.HOLD_EXPIRED);
    }
}
