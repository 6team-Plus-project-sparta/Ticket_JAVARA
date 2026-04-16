package com.example.ticket_javara.domain.chat.repository;

import com.example.ticket_javara.domain.chat.entity.ChatRoom;
import com.example.ticket_javara.domain.chat.entity.ChatRoomStatus;
import com.example.ticket_javara.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByUserUserIdAndStatus(Long userId, ChatRoomStatus status);
    Optional<ChatRoom> findFirstByUserUserIdAndStatusOrderByCreatedAtDesc(Long userId, ChatRoomStatus status);
    
    @EntityGraph(attributePaths = {"user"})
    Page<ChatRoom> findByStatus(ChatRoomStatus status, Pageable pageable);
}
