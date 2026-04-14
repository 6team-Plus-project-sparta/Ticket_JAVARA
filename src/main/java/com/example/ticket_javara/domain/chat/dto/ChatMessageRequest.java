package com.example.ticket_javara.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageRequest {
    
    private Long chatRoomId;

    @NotBlank
    private String content;
}
