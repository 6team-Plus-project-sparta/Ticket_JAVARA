package com.example.ticket_javara.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CHAT_MESSAGE 테이블 엔티티 (도전 기능)
 * ERD v7.0: chat_message_id, chat_room_id FK, sender_id FK, sender_role, content, sent_at
 */
@Entity
@Table(name = "chat_message",
        indexes = {
                @Index(name = "idx_chat_message_room", columnList = "chat_room_id, chat_message_id DESC")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long chatMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SenderRole senderRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Builder
    public ChatMessage(ChatRoom chatRoom, Long senderId, SenderRole senderRole, String content) {
        this.chatRoom = chatRoom;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.content = content;
        this.sentAt = LocalDateTime.now();
    }

    public enum SenderRole {
        USER, ADMIN, SYSTEM
    }
}
