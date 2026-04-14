package com.example.ticket_javara.domain.chat.repository;

import com.example.ticket_javara.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
}
