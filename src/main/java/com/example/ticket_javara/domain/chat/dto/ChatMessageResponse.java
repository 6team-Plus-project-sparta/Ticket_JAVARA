package com.example.ticket_javara.domain.chat.dto;

import com.example.ticket_javara.domain.chat.entity.ChatMessage;
import com.example.ticket_javara.domain.chat.entity.ChatMessage.SenderRole;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageResponse {
    private Long chatMessageId;
    private Long senderId;
    private SenderRole senderRole;
    private String senderNickname;
    private String content;
    private LocalDateTime sentAt;

    @Builder
    public ChatMessageResponse(Long chatMessageId, Long senderId, SenderRole senderRole, String senderNickname, String content, LocalDateTime sentAt) {
        this.chatMessageId = chatMessageId;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.senderNickname = senderNickname;
        this.content = content;
        this.sentAt = sentAt;
    }

    public static ChatMessageResponse of(ChatMessage message, String senderNickname) {
        return ChatMessageResponse.builder()
                .chatMessageId(message.getChatMessageId())
                .senderId(message.getSenderId())
                .senderRole(message.getSenderRole())
                .senderNickname(senderNickname)
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .build();
    }
}
