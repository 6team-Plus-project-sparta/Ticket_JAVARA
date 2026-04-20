package com.example.ticket_javara.domain.chat.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatRoomCloseResponse {
    private String message;
    private Long chatRoomId;
    private ChatRoomResponse chatRoom;
    private LocalDateTime closedAt;
}
