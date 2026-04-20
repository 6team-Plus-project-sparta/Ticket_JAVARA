package com.example.ticket_javara.domain.chat.repository;

import com.example.ticket_javara.domain.chat.entity.ChatMessage;
import java.util.List;

public interface ChatMessageRepositoryCustom {
    List<ChatMessage> getMessagesWithCursor(Long chatRoomId, Long cursor, int size);
    List<ChatMessage> getMessagesAfter(Long chatRoomId, Long afterId);
    List<ChatMessage> getLatestMessagesByRoomIds(List<Long> chatRoomIds);
    List<ChatMessage> getLatestMessagesByRoomIdsOptimized(List<Long> chatRoomIds);
}
