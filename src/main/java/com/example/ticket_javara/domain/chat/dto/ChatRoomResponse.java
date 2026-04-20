package com.example.ticket_javara.domain.chat.dto;

import com.example.ticket_javara.domain.chat.entity.ChatRoom;
import com.example.ticket_javara.domain.chat.entity.ChatRoomStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomResponse {
    private Long chatRoomId;
    private ChatRoomStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private boolean isNew;

    @Builder
    public ChatRoomResponse(Long chatRoomId, ChatRoomStatus status, LocalDateTime createdAt, LocalDateTime closedAt, boolean isNew) {
        this.chatRoomId = chatRoomId;
        this.status = status;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
        this.isNew = isNew;
    }

    public static ChatRoomResponse of(ChatRoom chatRoom, boolean isNew) {
        return ChatRoomResponse.builder()
                .chatRoomId(chatRoom.getChatRoomId())
                .status(chatRoom.getStatus())
                .createdAt(chatRoom.getCreatedAt())
                .closedAt(chatRoom.getClosedAt())
                .isNew(isNew)
                .build();
    }
}
