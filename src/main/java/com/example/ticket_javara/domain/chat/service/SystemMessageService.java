package com.example.ticket_javara.domain.chat.service;

import com.example.ticket_javara.domain.chat.dto.ChatMessageResponse;
import com.example.ticket_javara.domain.chat.entity.ChatMessage.SenderRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 채팅방 시스템 메시지(입장/퇴장) 서비스
 * DB에는 저장하지 않고 Simple Broker를 통해 실시간 브로드캐스트만 수행.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMessageService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 입장 메시지 발행: "○○님이 입장했습니다."
     */
    public void sendJoinMessage(Long chatRoomId, String nickname) {
        ChatMessageResponse systemMsg = buildSystemMessage(nickname + "님이 입장했습니다.");
        messagingTemplate.convertAndSend("/sub/chat/room/" + chatRoomId, systemMsg);
        log.info("[SystemMessage] JOIN - chatRoomId: {}, nickname: {}", chatRoomId, nickname);
    }

    /**
     * 퇴장 메시지 발행: "○○님이 퇴장했습니다."
     */
    public void sendLeaveMessage(Long chatRoomId, String nickname) {
        ChatMessageResponse systemMsg = buildSystemMessage(nickname + "님이 퇴장했습니다.");
        messagingTemplate.convertAndSend("/sub/chat/room/" + chatRoomId, systemMsg);
        log.info("[SystemMessage] LEAVE - chatRoomId: {}, nickname: {}", chatRoomId, nickname);
    }

    private ChatMessageResponse buildSystemMessage(String content) {
        return ChatMessageResponse.builder()
                .chatMessageId(null)
                .senderId(0L)
                .senderRole(SenderRole.SYSTEM)
                .senderNickname("시스템")
                .content(content)
                .sentAt(LocalDateTime.now())
                .build();
    }
}
