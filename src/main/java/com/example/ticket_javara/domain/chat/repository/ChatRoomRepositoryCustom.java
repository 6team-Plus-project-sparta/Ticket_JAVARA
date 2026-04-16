package com.example.ticket_javara.domain.chat.repository;

import com.example.ticket_javara.domain.chat.entity.ChatRoom;
import java.util.Optional;

public interface ChatRoomRepositoryCustom {
    Optional<ChatRoom> findLatestOpenRoomByUserId(Long userId);
}