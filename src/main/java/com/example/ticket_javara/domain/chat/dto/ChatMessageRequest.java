package com.example.ticket_javara.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageRequest {
    
    @NotNull(message = "채팅방 ID는 필수입니다.")
    private Long chatRoomId;

    @NotBlank
    private String content;
}
