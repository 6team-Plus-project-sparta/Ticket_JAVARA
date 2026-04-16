package com.example.ticket_javara.domain.chat.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatHistoryResponse {
    private Long chatRoomId;
    private List<ChatMessageResponse> messages;
    private Long nextCursor;
    private boolean hasNext;

    @Builder
    public ChatHistoryResponse(Long chatRoomId, List<ChatMessageResponse> messages, Long nextCursor, boolean hasNext) {
        this.chatRoomId = chatRoomId;
        this.messages = messages;
        this.nextCursor = nextCursor;
        this.hasNext = hasNext;
    }
}
