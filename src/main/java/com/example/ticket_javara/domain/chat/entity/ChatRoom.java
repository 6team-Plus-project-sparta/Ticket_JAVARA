package com.example.ticket_javara.domain.chat.entity;

import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.global.common.BaseTimeEntity;
import com.example.ticket_javara.global.exception.BusinessException;
import com.example.ticket_javara.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CHAT_ROOM 테이블 엔티티 (도전 기능)
 * ERD v7.0: chat_room_id, user_id FK, status, created_at, updated_at
 * status 전이: WAITING → IN_PROGRESS → COMPLETED (역방향 불가)
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
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status;

    @Builder
    public ChatRoom(User user) {
        this.user = user;
        this.status = ChatRoomStatus.WAITING;
    }

    /**
     * 관리자가 채팅방 상태를 전이시킵니다.
     * 전이 규칙: WAITING → IN_PROGRESS → COMPLETED (역방향 불가)
     */
    public void updateStatus(ChatRoomStatus newStatus) {
        if (!isValidTransition(this.status, newStatus)) {
            throw new BusinessException(ErrorCode.INVALID_CHAT_STATUS_TRANSITION);
        }
        this.status = newStatus;
    }

    private boolean isValidTransition(ChatRoomStatus current, ChatRoomStatus next) {
        return switch (current) {
            case WAITING -> next == ChatRoomStatus.IN_PROGRESS;
            case IN_PROGRESS -> next == ChatRoomStatus.COMPLETED;
            case COMPLETED -> false; // 완료 후 전이 불가
        };
    }

    /** 현재 채팅방이 활성 상태(대화 가능)인지 확인 */
    public boolean isActive() {
        return this.status == ChatRoomStatus.WAITING || this.status == ChatRoomStatus.IN_PROGRESS;
    }
}

