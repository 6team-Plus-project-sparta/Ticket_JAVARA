package com.example.ticket_javara.domain.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 웹훅 처리 결과 응답 DTO (FN-BK-02)
 * 성공/실패 모두 200 OK 반환 (웹훅 계약)
 */
@Getter
@AllArgsConstructor
public class WebhookResponseDto {

    private final String message;
    private final Long orderId;

    /** 성공 시 발급된 티켓 목록 (실패 시 null) */
    private final List<TicketDto> bookings;

    @Getter
    @AllArgsConstructor
    public static class TicketDto {
        private final Long bookingId;
        private final String ticketCode;
        private final String seatInfo;
    }
}
