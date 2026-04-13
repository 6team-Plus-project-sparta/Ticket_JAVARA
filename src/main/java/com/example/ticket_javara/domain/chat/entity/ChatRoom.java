package com.example.ticket_javara.domain.chat.entity;

import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * CHAT_ROOM 테이블 엔티티 (도전 기능)
 * ERD v7.0: chat_room_id, user_id FK, status, created_at, updated_at
 */
@Entity
@Table(name = "chat_room",
        indexes = {
                @Index(name = "idx_chat_room_user_status", columnList = "user_id, status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatRoomStatus status;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL)
    private List<ChatMessage> messages = new ArrayList<>();

    @Builder
    public ChatRoom(User user) {
        this.user = user;
        this.status = ChatRoomStatus.OPEN;
    }

    /** 채팅방 종료 */
    public void close() {
        this.status = ChatRoomStatus.CLOSED;
    }
}
