package com.example.ticket_javara.domain.chat.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatRoomCloseResponse {
    private String message;
    private Long chatRoomId;
}
