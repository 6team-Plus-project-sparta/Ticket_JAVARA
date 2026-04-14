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
public class AdminChatRoomResponse {
    private Long chatRoomId;
    private Long userId;
    private String userNickname;
    private ChatRoomStatus status;
    private String lastMessage; // TODO: 성능 최적화 필요시 테이블 분리 또는 쿼리 튜닝
    private LocalDateTime createdAt;

    @Builder
    public AdminChatRoomResponse(Long chatRoomId, Long userId, String userNickname, ChatRoomStatus status, String lastMessage, LocalDateTime createdAt) {
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.status = status;
        this.lastMessage = lastMessage;
        this.createdAt = createdAt;
    }

    public static AdminChatRoomResponse of(ChatRoom chatRoom, String lastMessage) {
        return AdminChatRoomResponse.builder()
                .chatRoomId(chatRoom.getChatRoomId())
                .userId(chatRoom.getUser().getUserId())
                .userNickname(chatRoom.getUser().getNickname())
                .status(chatRoom.getStatus())
                .lastMessage(lastMessage)
                .createdAt(chatRoom.getCreatedAt())
                .build();
    }
}
